package lasertag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class NetworkTest {
    /** Runs the host loop on a background thread, like the game window would. */
    private static final class Host implements AutoCloseable {
        final LocalSession session;
        final GameServer server;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread;

        Host(int bots) throws IOException {
            server = new GameServer(0, 2);
            session = new LocalSession(2, List.of("Host"), bots, Difficulty.EASY, server, "test");
            thread = new Thread(() -> {
                while (running.get()) {
                    synchronized (session) {
                        session.update(1 / 60.0);
                    }
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            thread.start();
        }

        <T> T read(java.util.function.Supplier<T> s) {
            synchronized (session) {
                return s.get();
            }
        }

        @Override
        public void close() throws InterruptedException {
            running.set(false);
            thread.join();
            session.close();
        }
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void clientJoinsMovesAndSeesTheMatch() throws Exception {
        try (Host host = new Host(0)) {
            ClientSession client = new ClientSession("127.0.0.1", host.server.port, "Guest");
            try {
                int id = client.localPlayerId(0);
                assertEquals("Guest", host.read(() -> host.session.world().players.get(id).name));
                assertEquals("Corridors", client.world().arena.name);

                // Receive snapshots containing the host and us.
                waitFor(() -> {
                    client.update(1 / 60.0);
                    return client.world().players.size() == 2;
                }, "snapshot with all players");

                // Our input moves our player on the host.
                double startX = host.read(() -> host.session.world().players.get(id).x);
                double startY = host.read(() -> host.session.world().players.get(id).y);
                Arena arena = client.world().arena;
                double toCentre = Math.atan2(arena.height() / 2 - startY, arena.width() / 2 - startX);
                client.setInput(0, new Input(Math.cos(toCentre), Math.sin(toCentre), 0, false));
                waitFor(() -> {
                    client.update(1 / 30.0);
                    Player p = host.read(() -> host.session.world().players.get(id));
                    return Math.hypot(p.x - startX, p.y - startY) > 30;
                }, "remote input to move the player");

                // And the client sees where the host put us.
                waitFor(() -> {
                    client.update(1 / 60.0);
                    Player mine = client.world().players.get(id);
                    Player real = host.read(() -> host.session.world().players.get(id));
                    return mine != null && Math.hypot(mine.x - real.x, mine.y - real.y) < 40;
                }, "client position to follow the host");
            } finally {
                client.close();
            }
            waitFor(() -> host.read(() -> host.session.world().players.size()) == 2, "host to drop the client");
        }
    }

    @Test
    void botsMakeRoomForJoiningPlayers() throws Exception {
        try (Host host = new Host(Rules.MAX_PLAYERS - 1)) {
            waitFor(() -> host.read(() -> host.session.world().players.size()) == Rules.MAX_PLAYERS, "full lobby");
            ClientSession client = new ClientSession("127.0.0.1", host.server.port, "Late");
            try {
                assertEquals(Rules.MAX_PLAYERS, (int) host.read(() -> host.session.world().players.size()));
                assertNotNull(host.read(() -> host.session.world().players.get(client.localPlayerId(0))));
                long bots = host.read(() -> host.session.world().players.values().stream().filter(p -> p.bot).count());
                assertEquals(Rules.MAX_PLAYERS - 2, bots);
            } finally {
                client.close();
            }
        }
    }

    @Test
    void fullMatchWithoutBotsRefusesJoin() throws Exception {
        try (Host host = new Host(0)) {
            ClientSession[] clients = new ClientSession[Rules.MAX_PLAYERS - 1];
            try {
                for (int i = 0; i < clients.length; i++) clients[i] = new ClientSession("127.0.0.1", host.server.port, "c" + i);
                IOException e = assertThrows(IOException.class,
                        () -> new ClientSession("127.0.0.1", host.server.port, "extra"));
                assertTrue(e.getMessage().contains("full"), e.getMessage());
            } finally {
                for (ClientSession c : clients) if (c != null) c.close();
            }
        }
    }

    @Test
    void wrongProtocolVersionIsRejected() throws Exception {
        try (Host host = new Host(0);
                Socket s = new Socket("127.0.0.1", host.server.port);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println("H LT0 old");
            String reply = in.readLine();
            assertNotNull(reply);
            assertTrue(reply.startsWith("X Version mismatch"), reply);
        }
    }

    @Test
    void clientReportsHostShutdown() throws Exception {
        Host host = new Host(0);
        ClientSession client = new ClientSession("127.0.0.1", host.server.port, "Guest");
        host.close();
        waitFor(() -> client.error() != null, "disconnect to be noticed");
        client.close();
    }
}
