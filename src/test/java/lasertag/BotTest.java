package lasertag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotTest {
    @Test
    void botsFindAndTagEachOtherOnEveryMap() {
        for (int m = 0; m < Arena.count(); m++) {
            for (Difficulty d : Difficulty.values()) {
                GameWorld w = new GameWorld(new Arena(m));
                List<Bot> bots = new ArrayList<>();
                for (int i = 0; i < 4; i++) bots.add(new Bot(w.addPlayer("bot", true).id, d));
                for (int step = 0; step < 60 * 60; step++) { // one minute
                    Map<Integer, Input> inputs = new HashMap<>();
                    for (Bot b : bots) inputs.put(b.playerId, b.think(w, 1 / 60.0));
                    w.update(1 / 60.0, inputs);
                }
                int tags = w.players.values().stream().mapToInt(p -> p.score).sum();
                assertTrue(tags >= 3 || w.round > 1, w.arena.name + "/" + d + ": only " + tags + " tags in a minute");
            }
        }
    }
}
