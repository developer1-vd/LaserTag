package lasertag;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Hosts broadcast a small UDP beacon so players on the same network can find games without typing an IP. */
final class LanDiscovery {
    private LanDiscovery() {}

    record Game(String host, String address, int port, String map, int players, long seenAt) {
        @Override
        public String toString() {
            return host + "  -  " + map + "  (" + players + "/" + Rules.MAX_PLAYERS + ")  " + address + ":" + port;
        }
    }

    static final class Announcer implements AutoCloseable {
        private volatile boolean running = true;
        private volatile int players;

        void setPlayers(int n) {
            players = n;
        }

        @Override
        public void close() {
            running = false;
        }
    }

    static Announcer announce(int port, String map, String hostName) {
        Announcer a = new Announcer();
        Thread t = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                while (a.running) {
                    String msg = "LASERTAG " + Rules.PROTOCOL + " " + port + " " + a.players + " "
                            + URLEncoder.encode(map, StandardCharsets.UTF_8) + " "
                            + URLEncoder.encode(hostName, StandardCharsets.UTF_8);
                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                    for (InetAddress target : broadcastAddresses()) {
                        try {
                            socket.send(new DatagramPacket(data, data.length, target, Rules.DISCOVERY_PORT));
                        } catch (IOException ignored) {
                            // some interfaces refuse broadcast; try the others
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException ignored) {
                // discovery is best-effort; direct IP joining still works
            }
        }, "lt-announce");
        t.setDaemon(true);
        t.start();
        return a;
    }

    /** Listens for host beacons until closed. */
    static final class Listener implements AutoCloseable {
        private final Map<String, Game> games = new ConcurrentHashMap<>();
        private volatile boolean running = true;
        private DatagramSocket socket;

        Listener() {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setSoTimeout(500);
                socket.bind(new InetSocketAddress(Rules.DISCOVERY_PORT));
            } catch (SocketException e) {
                socket = null;
                return;
            }
            Thread t = new Thread(this::loop, "lt-discover");
            t.setDaemon(true);
            t.start();
        }

        private void loop() {
            byte[] buf = new byte[512];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String[] t = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).split(" ");
                    if (t.length < 6 || !t[0].equals("LASERTAG") || !t[1].equals(Rules.PROTOCOL)) continue;
                    InetAddress from = packet.getAddress();
                    int port = Integer.parseInt(t[2]);
                    String host = URLDecoder.decode(t[5], StandardCharsets.UTF_8);
                    // A host on this machine is heard via loopback and its LAN address; list it once.
                    String key = host + ":" + port;
                    Game existing = games.get(key);
                    String address = from.isLoopbackAddress() && existing != null ? existing.address() : from.getHostAddress();
                    games.put(key, new Game(host, address, port, URLDecoder.decode(t[4], StandardCharsets.UTF_8),
                            Integer.parseInt(t[3]), System.currentTimeMillis()));
                } catch (SocketTimeoutException ignored) {
                    // loop to check running
                } catch (IOException | RuntimeException e) {
                    if (!running) return;
                }
            }
        }

        boolean available() {
            return socket != null;
        }

        /** Games heard from in the last few seconds. */
        List<Game> games() {
            long cutoff = System.currentTimeMillis() - 3500;
            games.values().removeIf(g -> g.seenAt() < cutoff);
            List<Game> list = new ArrayList<>(games.values());
            list.sort((a, b) -> a.toString().compareTo(b.toString()));
            return list;
        }

        @Override
        public void close() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    private static Set<InetAddress> broadcastAddresses() {
        Set<InetAddress> out = new LinkedHashSet<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) out.add(ia.getBroadcast());
                }
            }
        } catch (SocketException ignored) {
            // fall back to the global broadcast address
        }
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            out.add(InetAddress.getLoopbackAddress()); // lets a second copy on the same machine see the host
        } catch (IOException ignored) {
            // unreachable for literal addresses
        }
        return out;
    }

    /** Best guess at this machine's LAN address, for showing to the host. */
    static String localAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (SocketException ignored) {
            // fall through
        }
        return "localhost";
    }
}
