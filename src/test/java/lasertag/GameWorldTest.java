package lasertag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GameWorldTest {
    private static final double DT = 1 / 60.0;

    /** Two players facing each other across open floor on the Bunkers map (row 1 is open). */
    private static GameWorld duel(Player[] out) {
        GameWorld w = new GameWorld(new Arena(1));
        Player a = w.addPlayer("Alice", false), b = w.addPlayer("Bob", false);
        a.x = 100; a.y = 60; b.x = 500; b.y = 60;
        a.shield = b.shield = 0;
        a.cooldown = b.cooldown = 0;
        out[0] = a;
        out[1] = b;
        return w;
    }

    @Test
    void directHitTagsAndScores() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        w.update(DT, Map.of(p[0].id, new Input(0, 0, 0, true)));
        assertTrue(p[1].tagged);
        assertEquals(1, p[0].score);
        assertEquals(1, p[1].timesTagged);
        assertEquals(1, w.beams.size());
        assertTrue(w.feed.get(w.feed.size() - 1).text.contains("Alice tagged Bob"));
    }

    @Test
    void spawnShieldBlocksTag() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        p[1].shield = 1;
        w.update(DT, Map.of(p[0].id, new Input(0, 0, 0, true)));
        assertFalse(p[1].tagged);
        assertEquals(0, p[0].score);
    }

    @Test
    void energyLimitsRapidFire() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        p[1].x = 900; p[1].y = 700; // out of the line of fire
        int shots = 0;
        for (int i = 0; i < 60; i++) { // one second of holding fire
            int before = w.beams.size();
            w.update(DT, Map.of(p[0].id, new Input(0, 0, Math.PI, true)));
            if (w.beams.size() > before) shots++;
        }
        assertTrue(shots >= 4 && shots <= 5, "shots=" + shots);
    }

    @Test
    void laserRicochetsOffWalls() {
        GameWorld w = new GameWorld(new Arena(1));
        Player a = w.addPlayer("A", false);
        a.x = 300; a.y = 60; a.shield = 0; a.cooldown = 0;
        Player far = w.addPlayer("Far", false);
        far.x = 1100; far.y = 740;
        w.update(DT, Map.of(a.id, new Input(0, 0, -Math.PI / 4, true)));
        Beam b = w.beams.get(0);
        assertTrue(b.points.size() >= 3, "beam should bounce off the top wall");
        assertTrue(b.points.get(1)[1] <= 40.01, "first bounce on the top wall face");
    }

    @Test
    void taggedPlayerRespawns() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        w.update(DT, Map.of(p[0].id, new Input(0, 0, 0, true)));
        assertTrue(p[1].tagged);
        for (int i = 0; i < (Rules.TAGGED_TIME + 0.1) / DT; i++) w.update(DT, Map.of());
        assertFalse(p[1].tagged);
        assertTrue(p[1].shield > 0);
    }

    @Test
    void roundEndsAtScoreLimitAndRestarts() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        p[0].score = Rules.SCORE_LIMIT;
        w.update(DT, Map.of());
        assertTrue(w.roundOver);
        for (int i = 0; i < (Rules.ROUND_OVER_TIME + 0.1) / DT; i++) w.update(DT, Map.of());
        assertFalse(w.roundOver);
        assertEquals(2, w.round);
        assertEquals(0, p[0].score);
    }

    @Test
    void movementIsBlockedByWalls() {
        GameWorld w = new GameWorld(new Arena(0));
        Player a = w.addPlayer("A", false);
        a.x = 60; a.y = 60;
        for (int i = 0; i < 120; i++) w.update(DT, Map.of(a.id, new Input(-1, -1, Double.NaN, false)));
        assertEquals(40 + Rules.PLAYER_RADIUS, a.x, 0.01);
        assertEquals(40 + Rules.PLAYER_RADIUS, a.y, 0.01);
        assertEquals(-3 * Math.PI / 4, a.angle, 0.01); // faces the way it moved
    }

    @Test
    void snapshotRoundTrips() {
        Player[] p = new Player[2];
        GameWorld w = duel(p);
        p[0].name = "Al ice+%";
        w.update(DT, Map.of(p[0].id, new Input(0, 0, 0, true)));

        GameWorld copy = new GameWorld(new Arena(1));
        copy.applySnapshot(w.snapshot());
        assertEquals(2, copy.players.size());
        Player a = copy.players.get(p[0].id), b = copy.players.get(p[1].id);
        assertNotNull(a);
        assertEquals("Al ice+%", a.name);
        assertEquals(p[0].x, a.x, 0.1);
        assertEquals(1, a.score);
        assertTrue(b.tagged);
        assertEquals(1, copy.beams.size());
        assertEquals(w.beams.get(0).points.size(), copy.beams.get(0).points.size());
        assertEquals(w.feed.size(), copy.feed.size());

        w.removePlayer(p[1].id);
        copy.applySnapshot(w.snapshot());
        assertEquals(1, copy.players.size());
    }

    @Test
    void namesAreSanitizedAndUnique() {
        GameWorld w = new GameWorld(new Arena(0));
        assertEquals("Player", w.addPlayer("  \n ", false).name);
        assertEquals("player 2", w.addPlayer("player", false).name);
        assertEquals(14, w.addPlayer("A very long name indeed", false).name.length());
        for (int i = 0; i < 10; i++) w.addPlayer("x", true);
        assertEquals(Rules.MAX_PLAYERS, w.players.size());
    }

    @Test
    void inputEncodingRoundTrips() {
        Input in = Input.decode(new Input(0.5, -1, 1.234, true).encode());
        assertEquals(0.5, in.mx, 1e-9);
        assertEquals(-1, in.my, 1e-9);
        assertEquals(1.234, in.aim, 1e-9);
        assertTrue(in.fire);
        assertTrue(Double.isNaN(Input.decode(Input.NONE.encode()).aim));
        assertEquals(Input.NONE, Input.decode("I garbage"));
        assertEquals(1, Input.decode("I 900 0 N 0").mx, 1e-9);
    }
}
