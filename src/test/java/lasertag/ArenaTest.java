package lasertag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ArenaTest {
    @Test
    void everyMapIsEnclosedAndFullyConnected() {
        for (int m = 0; m < Arena.count(); m++) {
            Arena a = new Arena(m);
            assertEquals(30, a.cols, a.name);
            assertEquals(20, a.rows, a.name);
            for (int c = 0; c < a.cols; c++) assertTrue(a.solid(c, 0) && a.solid(c, a.rows - 1), a.name);
            for (int r = 0; r < a.rows; r++) assertTrue(a.solid(0, r) && a.solid(a.cols - 1, r), a.name);
            assertTrue(a.spawns.size() >= 4, a.name);

            double[] start = a.spawns.get(0);
            for (int r = 0; r < a.rows; r++) {
                for (int c = 0; c < a.cols; c++) {
                    if (a.solid(c, r)) continue;
                    double x = (c + 0.5) * Arena.CELL, y = (r + 0.5) * Arena.CELL;
                    if (Math.abs(x - start[0]) < 1 && Math.abs(y - start[1]) < 1) continue;
                    assertNotNull(a.findPath(start[0], start[1], x, y), a.name + " cell " + c + "," + r + " unreachable");
                }
            }
        }
    }

    @Test
    void rayStopsAtWallFace() {
        Arena a = new Arena(0);
        // From the middle of cell (1,1) heading left, the border wall face is at x = 40.
        double[] hit = a.castRay(60, 60, -1, 0, 1000);
        assertEquals(20, hit[0], 1e-9);
        assertEquals(0, hit[1]);
        hit = a.castRay(60, 60, 0, -1, 1000);
        assertEquals(20, hit[0], 1e-9);
        assertEquals(1, hit[1]);
        assertEquals(-1, a.castRay(60, 60, 1, 0, 10)[1]);
    }

    @Test
    void collisionKeepsMovingPlayersOutOfWalls() {
        for (int m = 0; m < Arena.count(); m++) {
            Arena a = new Arena(m);
            Random rng = new Random(m);
            Player p = new Player(1, "p", 0, false);
            double[] start = a.randomOpenCell(rng);
            p.x = start[0];
            p.y = start[1];
            double dir = 0;
            for (int i = 0; i < 20000; i++) {
                if (i % 30 == 0) dir = rng.nextDouble() * Math.PI * 2;
                double step = Rules.PLAYER_SPEED / 60;
                p.x += Math.cos(dir) * step;
                p.y += Math.sin(dir) * step;
                a.collide(p);
                double r = Rules.PLAYER_RADIUS - 0.5;
                for (double ang = 0; ang < Math.PI * 2; ang += 0.3) {
                    int c = (int) Math.floor((p.x + Math.cos(ang) * r) / Arena.CELL);
                    int row = (int) Math.floor((p.y + Math.sin(ang) * r) / Arena.CELL);
                    assertTrue(!a.solid(c, row), a.name + ": overlap at " + p.x + "," + p.y);
                }
            }
        }
    }
}
