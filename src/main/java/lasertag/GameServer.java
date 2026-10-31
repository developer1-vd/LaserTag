package lasertag;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Accepts remote players for a hosted match. The host's game loop stays authoritative: it calls
 * {@link #pump} to admit/remove players, reads {@link #inputs()}, and {@link #broadcast}s snapshots.
 *
 * <p>Protocol (one text line per message):
 * client → host: {@code H <protocol> <name>} then {@code I <mx> <my> <aim> <fire>} repeatedly;
 * host → client: {@code W <playerId> <mapIndex>} on join, {@code S ...} snapshots, {@code X <reason>} on refusal.
 */
final class GameServer implements AutoCloseable {
    private static final class Remote {
        Connection conn;
        volatile String name;
        volatile Input input = Input.NONE;
        int playerId = -1;
    }

    final int port;
    private final int mapIndex;
    private final ServerSocket serverSocket;
    private final List<Remote> joined = new CopyOnWriteArrayList<>();
    private final Queue<Remote> joins = new ConcurrentLinkedQueue<>();
    private final Queue<Remote> leaves = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    GameServer(int port, int mapIndex) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.port = serverSocket.getLocalPort();
        this.mapIndex = mapIndex;
        Thread t = new Thread(this::acceptLoop, "lt-accept");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                Remote r = new Remote();
                r.conn = new Connection(s, line -> onLine(r, line), () -> leaves.add(r));
                r.conn.start();
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    private void onLine(Remote r, String line) {
        if (line.startsWith("I ")) {
            r.input = Input.decode(line);
        } else if (line.startsWith("H ") && r.name == null) {
            String[] parts = line.split(" ", 3);
            if (!parts[1].equals(Rules.PROTOCOL)) {
                r.conn.sendAndClose("X Version mismatch: host runs " + Rules.PROTOCOL);
                return;
            }
            r.name = parts.length > 2 ? parts[2] : "Player";
            joins.add(r);
        }
    }

    /**
     * Applies pending joins and leaves to the world. {@code makeRoom} is asked to free a slot
     * (e.g. by removing a bot) when the match is full.
     */
    void pump(GameWorld world, BooleanSupplier makeRoom) {
        Remote r;
        while ((r = joins.poll()) != null) {
            if (r.conn.isClosed()) continue;
            if (world.players.size() >= Rules.MAX_PLAYERS && !makeRoom.getAsBoolean()) {
                r.conn.sendAndClose("X The match is full");
                continue;
            }
            Player p = world.addPlayer(r.name, false);
            r.playerId = p.id;
            joined.add(r);
            r.conn.send("W " + p.id + " " + mapIndex);
        }
        while ((r = leaves.poll()) != null) {
            if (joined.remove(r)) world.removePlayer(r.playerId);
        }
    }

    Map<Integer, Input> inputs() {
        Map<Integer, Input> map = new HashMap<>();
        for (Remote r : joined) map.put(r.playerId, r.input);
        return map;
    }

    int remoteCount() {
        return joined.size();
    }

    void broadcast(String line) {
        for (Remote r : joined) r.conn.send(line);
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        for (Remote r : joined) r.conn.sendAndClose("X The host ended the match");
    }
}
