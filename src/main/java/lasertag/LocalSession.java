package lasertag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The host side of an online match: an {@link OfflineSession} that also serves remote players. */
final class LocalSession extends OfflineSession {
    private static final double SNAPSHOT_INTERVAL = 1 / 30.0;

    private final GameServer server;
    private final LanDiscovery.Announcer announcer;
    private final InternetAccess internet;
    private final String lanAddress;
    private double snapshotTimer;

    /**
     * @param humans players on this computer (none for a dedicated server)
     * @param openInternet also try to make the match reachable from the Internet
     */
    LocalSession(int mapIndex, List<String> humans, int botCount, Difficulty difficulty, GameServer server,
            String hostName, boolean openInternet) {
        super(mapIndex, humans, botCount, difficulty);
        this.server = server;
        announcer = LanDiscovery.announce(server.port, world.arena.name, hostName);
        lanAddress = LanDiscovery.localAddress() + ":" + server.port;
        internet = openInternet ? new InternetAccess(server.port) : null;
    }

    @Override
    public void update(double dt) {
        server.pump(world, this::removeBot);
        announcer.setPlayers(world.players.size());
        super.update(dt);
        if ((snapshotTimer -= dt) <= 0) {
            snapshotTimer = SNAPSHOT_INTERVAL;
            server.broadcast(world.snapshot());
        }
    }

    @Override
    protected void addInputs(Map<Integer, Input> inputs) {
        inputs.putAll(server.inputs());
    }

    /** Other people are playing, so the host can't stop time. */
    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public String status() {
        return "LAN " + lanAddress + (internet != null ? "  " + internet.summary() : "") + "  "
                + server.remoteCount() + " ONLINE";
    }

    @Override
    public List<String> details() {
        List<String> lines = new ArrayList<>();
        lines.add("LAN ADDRESS    " + lanAddress + "   (SAME WIFI / NETWORK)");
        if (internet != null) lines.addAll(internet.details());
        return lines;
    }

    /** Internet access state, or null when not opened; used by the dedicated server. */
    InternetAccess internet() {
        return internet;
    }

    @Override
    public void close() {
        announcer.close();
        if (internet != null) internet.close();
        server.close();
    }
}
