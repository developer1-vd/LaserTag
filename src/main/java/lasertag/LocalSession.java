package lasertag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A match simulated on this computer: offline games, and the host side of online games
 * (when a {@link GameServer} is attached).
 */
final class LocalSession implements Session {
    private static final double SNAPSHOT_INTERVAL = 1 / 30.0;

    private final GameWorld world;
    private final int[] localIds;
    private final Input[] localInputs;
    private final List<Bot> bots = new ArrayList<>();
    private final Difficulty difficulty;
    private final GameServer server;
    private final LanDiscovery.Announcer announcer;
    private final String status;
    private double snapshotTimer;
    private boolean paused;

    LocalSession(int mapIndex, List<String> humans, int botCount, Difficulty difficulty, GameServer server,
            String hostName) {
        this.world = new GameWorld(new Arena(mapIndex));
        this.difficulty = difficulty;
        this.server = server;
        localIds = new int[humans.size()];
        localInputs = new Input[humans.size()];
        for (int i = 0; i < humans.size(); i++) {
            localIds[i] = world.addPlayer(humans.get(i), false).id;
            localInputs[i] = Input.NONE;
        }
        for (int i = 0; i < botCount; i++) addBot();
        world.feed.clear();
        world.addFeed("Round 1 - go!");

        if (server != null) {
            announcer = LanDiscovery.announce(server.port, world.arena.name, hostName);
            status = "Hosting on " + LanDiscovery.localAddress() + ":" + server.port;
        } else {
            announcer = null;
            status = null;
        }
    }

    private void addBot() {
        int n = bots.size() + world.players.size();
        Player p = world.addPlayer(Rules.BOT_NAMES[n % Rules.BOT_NAMES.length], true);
        if (p != null) bots.add(new Bot(p.id, difficulty));
    }

    /** Frees a slot for a joining remote player by retiring a bot. */
    private boolean removeBot() {
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
        if (server != null) {
            server.pump(world, this::removeBot);
            if (announcer != null) announcer.setPlayers(world.players.size());
        }
        if (paused) return;

        Map<Integer, Input> inputs = new HashMap<>();
        for (int i = 0; i < localIds.length; i++) inputs.put(localIds[i], localInputs[i]);
        for (Bot b : bots) inputs.put(b.playerId, b.think(world, dt));
        if (server != null) inputs.putAll(server.inputs());
        world.update(dt, inputs);

        if (server != null && (snapshotTimer -= dt) <= 0) {
            snapshotTimer = SNAPSHOT_INTERVAL;
            server.broadcast(world.snapshot());
        }
    }

    @Override
    public boolean canPause() {
        return server == null;
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
        if (server == null) return null;
        return status + "  -  " + server.remoteCount() + " online";
    }

    @Override
    public String error() {
        return null;
    }

    @Override
    public void close() {
        if (announcer != null) announcer.close();
        if (server != null) server.close();
    }
}
