package lasertag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A match simulated on this computer with local players and bots. Has no networking, so the web
 * version uses it too; {@link LocalSession} adds hosting on top.
 */
class OfflineSession implements Session {
    protected final GameWorld world;
    private final int[] localIds;
    private final Input[] localInputs;
    private final List<Bot> bots = new ArrayList<>();
    private final Difficulty difficulty;
    private boolean paused;

    OfflineSession(int mapIndex, List<String> humans, int botCount, Difficulty difficulty) {
        this.world = new GameWorld(new Arena(mapIndex));
        this.difficulty = difficulty;
        localIds = new int[humans.size()];
        localInputs = new Input[humans.size()];
        for (int i = 0; i < humans.size(); i++) {
            localIds[i] = world.addPlayer(humans.get(i), false).id;
            localInputs[i] = Input.NONE;
        }
        for (int i = 0; i < botCount; i++) addBot();
        world.feed.clear();
        world.addFeed("Round 1 - go!");
    }

    private void addBot() {
        int n = bots.size() + world.players.size();
        Player p = world.addPlayer(Rules.BOT_NAMES[n % Rules.BOT_NAMES.length], true);
        if (p != null) bots.add(new Bot(p.id, difficulty));
    }

    /** Retires a bot, e.g. to free a slot for a joining player. Returns false if there are none. */
    protected boolean removeBot() {
        if (bots.isEmpty()) return false;
        Bot b = bots.remove(bots.size() - 1);
        world.removePlayer(b.playerId);
        return true;
    }

    @Override
    public GameWorld world() {
        return world;
    }

    @Override
    public int localCount() {
        return localIds.length;
    }

    @Override
    public int localPlayerId(int slot) {
        return localIds[slot];
    }

    @Override
    public void setInput(int slot, Input input) {
        localInputs[slot] = input;
    }

    @Override
    public void update(double dt) {
        if (paused) return;
        Map<Integer, Input> inputs = new HashMap<>();
        for (int i = 0; i < localIds.length; i++) inputs.put(localIds[i], localInputs[i]);
        for (Bot b : bots) inputs.put(b.playerId, b.think(world, dt));
        addInputs(inputs);
        world.update(dt, inputs);
    }

    /** Lets subclasses add inputs for other players (e.g. remote ones) before each step. */
    protected void addInputs(Map<Integer, Input> inputs) {}

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public void togglePause() {
        if (canPause()) paused = !paused;
    }

    @Override
    public boolean paused() {
        return paused;
    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public String error() {
        return null;
    }

    @Override
    public void close() {}
}
