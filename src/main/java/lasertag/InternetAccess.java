package lasertag;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes a hosted game reachable from the Internet: opens the router port with UPnP and works out
 * the public address, which is turned into a short {@link InternetCode} for friends to type in.
 * Runs in the background; the getters report progress.
 */
final class InternetAccess implements AutoCloseable {
    private final int port;
    private volatile boolean done;
    private volatile String publicIp;
    private volatile boolean portOpened;
    private volatile boolean doubleNat;
    private volatile PortMapper.Mapping mapping;
    private volatile boolean closed;

    InternetAccess(int port) {
        this.port = port;
        Thread t = new Thread(this::setUp, "lt-internet");
        t.setDaemon(true);
        t.start();
    }

    private void setUp() {
        PortMapper.Mapping m = PortMapper.map(port, "Laser Tag");
        if (m != null) {
            if (closed) {
                m.remove();
                return;
            }
            mapping = m;
            portOpened = true;
            if (m.externalIp != null && isPublic(m.externalIp)) publicIp = m.externalIp;
            else doubleNat = m.externalIp != null;
        }
        if (publicIp == null) publicIp = lookUpPublicIp();
        done = true;
    }

    /** Asks a public "what is my IP" service, since a router behind another NAT doesn't know it. */
    private static String lookUpPublicIp() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(4)).GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
            return InternetCode.encode(body, 1) != null ? body : null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isPublic(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip);
            byte[] b = a.getAddress();
            boolean cgnat = b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
            return !(a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || cgnat);
        } catch (Exception e) {
            return false;
        }
    }

    /** The shareable code, or null while still checking / if offline. */
    String code() {
        String ip = publicIp;
        return ip == null ? null : InternetCode.encode(ip, port);
    }

    /** One short line for the HUD. */
    String summary() {
        if (!done) return "NET: CHECKING...";
        String code = code();
        if (code == null) return "NET: OFFLINE";
        return "CODE " + code + (portOpened && !doubleNat ? "" : " (PORT?)");
    }

    /** Full explanation for the info screen. */
    List<String> details() {
        List<String> lines = new ArrayList<>();
        if (!done) {
            lines.add("INTERNET:  OPENING ROUTER PORT...");
            return lines;
        }
        String code = code();
        if (code == null) {
            lines.add("INTERNET:  COULD NOT FIND YOUR PUBLIC ADDRESS");
            return lines;
        }
        lines.add("INTERNET CODE  " + code + "   (" + publicIp + ":" + port + ")");
        if (portOpened && !doubleNat) {
            lines.add("ROUTER PORT " + port + " OPENED AUTOMATICALLY (UPNP)");
        } else if (doubleNat) {
            lines.add("YOUR ROUTER IS BEHIND ANOTHER NETWORK (CGNAT).");
            lines.add("INTERNET PLAYERS MAY NOT REACH YOU.");
        } else {
            lines.add("COULD NOT OPEN PORT AUTOMATICALLY. FORWARD");
            lines.add("TCP PORT " + port + " TO THIS PC IN YOUR ROUTER SETTINGS.");
        }
        return lines;
    }

    @Override
    public void close() {
        closed = true;
        PortMapper.Mapping m = mapping;
        mapping = null;
        if (m != null) {
            Thread t = new Thread(m::remove, "lt-unmap"); // not a daemon, so it finishes even if the app exits
            t.start();
        }
    }
}
