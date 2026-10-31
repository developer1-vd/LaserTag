package lasertag;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A line-based TCP connection with its own reader and writer threads, so the game loop never
 * blocks on the network. If the peer falls behind, the oldest queued lines are dropped
 * (snapshots supersede each other, so this only skips frames).
 */
final class Connection {
    private static final String CLOSE = new String("<close>");
    private static final int MAX_QUEUE = 64;
    private static final int MAX_LINE = 64 * 1024;

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
    private final Consumer<String> onLine;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();

    Connection(Socket socket, Consumer<String> onLine, Runnable onClose) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.onLine = onLine;
        this.onClose = onClose;
    }

    void start() {
        Thread reader = new Thread(this::readLoop, "lt-reader");
        Thread writer = new Thread(this::writeLoop, "lt-writer");
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
    }

    void send(String line) {
        if (closed.get()) return;
        while (queue.size() > MAX_QUEUE) queue.pollFirst();
        queue.offerLast(line);
    }

    /** Sends a final line, then closes once it has been flushed. */
    void sendAndClose(String line) {
        if (closed.get()) return;
        queue.offerLast(line);
        queue.offerLast(CLOSE);
    }

    boolean isClosed() {
        return closed.get();
    }

    String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already gone
        }
        queue.offerLast(CLOSE);
        onClose.run();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() > MAX_LINE) break;
                onLine.accept(line);
            }
        } catch (IOException | RuntimeException ignored) {
            // treated as a disconnect
        } finally {
            close();
        }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                String line = queue.take();
                if (line == CLOSE) {
                    out.flush();
                    break;
                }
                out.write(line);
                out.write('\n');
                if (queue.isEmpty()) out.flush();
            }
        } catch (IOException | InterruptedException ignored) {
            // treated as a disconnect
        } finally {
            close();
        }
    }
}
