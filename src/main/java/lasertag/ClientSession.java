package lasertag;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** The joining side of an online match: sends inputs, renders the host's snapshots. */
final class ClientSession implements Session {
    private static final double SEND_INTERVAL = 1 / 30.0;
    private static final double KEEPALIVE = 0.25;

    private final Connection conn;
    private final AtomicReference<String> latestSnapshot = new AtomicReference<>();
    private final BlockingQueue<String> handshake = new ArrayBlockingQueue<>(4);
    private final GameWorld world;
    private final int myId;
    private final String address;
    private volatile String error;

    private Input input = Input.NONE;
    private String lastSent = "";
    private double sinceSend;

    /** Connects and waits for the host to admit us. Blocks for up to a few seconds. */
    ClientSession(String host, int port, String name) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        socket.setTcpNoDelay(true);
        address = host + ":" + port;
        conn = new Connection(socket, this::onLine, () -> {
            if (error == null) error = "Lost connection to the host";
        });
        conn.start();
        conn.send("H " + Rules.PROTOCOL + " " + GameWorld.sanitizeName(name));

        String reply;
        try {
            reply = handshake.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply = null;
        }
        if (reply == null || !reply.startsWith("W ")) {
            conn.close();
            if (reply == null) throw new IOException(error != null ? error : "The host did not respond");
            throw new IOException(reply.substring(2));
        }
        String[] t = reply.split(" ");
        myId = Integer.parseInt(t[1]);
        world = new GameWorld(new Arena(Integer.parseInt(t[2])));
    }

    private void onLine(String line) {
        if (line.startsWith("S ")) {
            latestSnapshot.set(line);
        } else if (line.startsWith("W ")) {
            handshake.offer(line);
        } else if (line.startsWith("X ")) {
            error = line.substring(2);
            handshake.offer(line);
        }
    }

    @Override
    public GameWorld world() {
        return world;
    }

    @Override
    public int localCount() {
        return 1;
    }

    @Override
    public int localPlayerId(int slot) {
        return myId;
    }

    @Override
    public void setInput(int slot, Input input) {
        this.input = input;
    }

    @Override
    public void update(double dt) {
        String snap = latestSnapshot.getAndSet(null);
        if (snap != null) {
            try {
                world.applySnapshot(snap);
            } catch (RuntimeException e) {
                error = "Received a corrupt update from the host";
                conn.close();
            }
        }
        world.clientTick(dt);

        sinceSend += dt;
        String encoded = input.encode();
        if (sinceSend >= SEND_INTERVAL && (!encoded.equals(lastSent) || sinceSend >= KEEPALIVE)) {
            conn.send(encoded);
            lastSent = encoded;
            sinceSend = 0;
        }
    }

    @Override
    public String status() {
        return "Connected to " + address;
    }

    @Override
    public String error() {
        return error;
    }

    @Override
    public void close() {
        if (error == null) error = "Left the match";
        conn.close();
    }
}
