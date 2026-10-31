package lasertag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a TCP port on the home router with UPnP (Internet Gateway Device), so people on the
 * Internet can reach a hosted game without manual port forwarding. Best-effort: many routers
 * have UPnP turned off, in which case {@link #map} returns null.
 */
final class PortMapper {
    private PortMapper() {}

    private static final int TIMEOUT_MS = 3000;
    private static final String[] SEARCH_TARGETS = {
        "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
        "urn:schemas-upnp-org:device:InternetGatewayDevice:2",
        "urn:schemas-upnp-org:service:WANIPConnection:1",
    };

    /** An open mapping on a router. */
    static final class Mapping {
        final String controlUrl, serviceType, externalIp;
        final int port;

        Mapping(String controlUrl, String serviceType, String externalIp, int port) {
            this.controlUrl = controlUrl;
            this.serviceType = serviceType;
            this.externalIp = externalIp;
            this.port = port;
        }

        void remove() {
            try {
                soap(controlUrl, serviceType, "DeletePortMapping", new String[][] {
                    {"NewRemoteHost", ""}, {"NewExternalPort", Integer.toString(port)}, {"NewProtocol", "TCP"},
                });
            } catch (IOException ignored) {
                // the router will drop it eventually; nothing else to do
            }
        }
    }

    /** Finds a UPnP router and forwards {@code port} to this machine. Returns null if not possible. */
    static Mapping map(int port, String description) {
        for (String location : discover()) {
            try {
                return mapVia(new URL(location), port, description);
            } catch (IOException | RuntimeException ignored) {
                // try the next device
            }
        }
        return null;
    }

    /** Maps the port using the router whose device description lives at {@code location}. */
    static Mapping mapVia(URL location, int port, String description) throws IOException {
        String xml = new String(http(location, null, null, null), StandardCharsets.UTF_8);
        String[] service = findService(xml);
        if (service == null) throw new IOException("No WAN connection service");
        String base = tag(xml, "URLBase");
        String control = new URL(base != null && !base.isBlank() ? new URL(base.trim()) : location, service[1]).toString();
        String localIp = localAddressTowards(location);
        soap(control, service[0], "AddPortMapping", new String[][] {
            {"NewRemoteHost", ""},
            {"NewExternalPort", Integer.toString(port)},
            {"NewProtocol", "TCP"},
            {"NewInternalPort", Integer.toString(port)},
            {"NewInternalClient", localIp},
            {"NewEnabled", "1"},
            {"NewPortMappingDescription", description},
            {"NewLeaseDuration", "0"},
        });
        String reply = soap(control, service[0], "GetExternalIPAddress", new String[0][]);
        String external = tag(reply, "NewExternalIPAddress");
        return new Mapping(control, service[0], external == null ? null : external.trim(), port);
    }

    /** Sends SSDP searches and collects device description URLs. */
    private static List<String> discover() {
        Set<String> locations = new LinkedHashSet<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(500);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            for (String st : SEARCH_TARGETS) {
                byte[] msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nST: " + st
                        + "\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                socket.send(new DatagramPacket(msg, msg.length, group, 1900));
            }
            long deadline = System.currentTimeMillis() + 2500;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    for (String line : new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).split("\r?\n")) {
                        if (line.toLowerCase(Locale.ROOT).startsWith("location:")) locations.add(line.substring(9).trim());
                    }
                } catch (SocketTimeoutException e) {
                    if (!locations.isEmpty()) break;
                }
            }
        } catch (IOException ignored) {
            // no network or multicast blocked
        }
        return new ArrayList<>(locations);
    }

    /** Returns {serviceType, controlURL} for the router's WAN connection service, preferring IP over PPP. */
    static String[] findService(String xml) {
        String[] ppp = null;
        Matcher m = Pattern.compile("<service>(.*?)</service>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            String block = m.group(1);
            String type = tag(block, "serviceType"), control = tag(block, "controlURL");
            if (type == null || control == null) continue;
            if (type.contains("WANIPConnection")) return new String[] {type.trim(), control.trim()};
            if (type.contains("WANPPPConnection") && ppp == null) ppp = new String[] {type.trim(), control.trim()};
        }
        return ppp;
    }

    static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<(?:\\w+:)?" + name + "[^>]*>(.*?)</(?:\\w+:)?" + name + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String soap(String controlUrl, String serviceType, String action, String[][] args) throws IOException {
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:" + action + " xmlns:u=\"" + serviceType + "\">");
        for (String[] a : args) body.append('<').append(a[0]).append('>').append(a[1]).append("</").append(a[0]).append('>');
        body.append("</u:").append(action).append("></s:Body></s:Envelope>");
        byte[] reply = http(new URL(controlUrl), body.toString().getBytes(StandardCharsets.UTF_8),
                "text/xml; charset=\"utf-8\"", "\"" + serviceType + "#" + action + "\"");
        return new String(reply, StandardCharsets.UTF_8);
    }

    private static byte[] http(URL url, byte[] post, String contentType, String soapAction) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY); // routers are always local
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        if (post != null) {
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", contentType);
            c.setRequestProperty("SOAPAction", soapAction);
            try (OutputStream out = c.getOutputStream()) {
                out.write(post);
            }
        }
        int code = c.getResponseCode();
        try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
            byte[] data = in == null ? new byte[0] : readAll(in);
            if (code >= 400) {
                String err = tag(new String(data, StandardCharsets.UTF_8), "errorDescription");
                throw new IOException("Router refused: " + (err != null ? err : "HTTP " + code));
            }
            return data;
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 1 << 20) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** The address of this machine on the interface that reaches the router. */
    private static String localAddressTowards(URL url) throws IOException {
        int port = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(url.getHost(), port), TIMEOUT_MS);
            return s.getLocalAddress().getHostAddress();
        }
    }
}
