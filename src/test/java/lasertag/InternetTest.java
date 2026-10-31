package lasertag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class InternetTest {
    @Test
    void codesRoundTrip() {
        for (String ip : new String[] {"0.0.0.1", "81.2.69.160", "255.255.255.255", "8.8.8.8"}) {
            for (int port : new int[] {1, 47777, 65535}) {
                String code = InternetCode.encode(ip, port);
                assertTrue(InternetCode.looksLikeCode(code), code);
                assertArrayEquals(new String[] {ip, Integer.toString(port)}, InternetCode.decode(code));
                assertArrayEquals(new String[] {ip, Integer.toString(port)}, InternetCode.decode(code.toLowerCase()));
            }
        }
        assertNull(InternetCode.encode("::1", 47777));
        assertNull(InternetCode.encode("300.1.1.1", 47777));
    }

    @Test
    void codesCatchMostTypos() {
        String code = InternetCode.encode("81.2.69.160", 47777);
        int caught = 0, total = 0;
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '-') continue;
            for (char c : alphabet.toCharArray()) {
                if (c == code.charAt(i)) continue;
                total++;
                String typo = code.substring(0, i) + c + code.substring(i + 1);
                String[] d = InternetCode.decode(typo);
                if (d == null) caught++;
            }
        }
        assertTrue(caught > total / 2, caught + "/" + total);
    }

    @Test
    void joinTargetAcceptsCodesAddressesAndHostPort() {
        String code = InternetCode.encode("81.2.69.160", 5000);
        assertArrayEquals(new String[] {"81.2.69.160", "5000"}, MenuPanel.parseJoinTarget(code, "47777"));
        assertArrayEquals(new String[] {"192.168.1.20", "47777"}, MenuPanel.parseJoinTarget(" 192.168.1.20 ", "47777"));
        assertArrayEquals(new String[] {"game.example.com", "9000"}, MenuPanel.parseJoinTarget("GAME.EXAMPLE.COM:9000", "47777"));
        assertEquals(1, MenuPanel.parseJoinTarget("", "47777").length);
        assertEquals(1, MenuPanel.parseJoinTarget("host:99999", "47777").length);
        assertEquals(1, MenuPanel.parseJoinTarget("ZZZZZ-ZZZZZ", "47777").length);
    }

    @Test
    void publicAddressDetection() {
        assertTrue(InternetAccess.isPublic("81.2.69.160"));
        assertFalse(InternetAccess.isPublic("192.168.1.1"));
        assertFalse(InternetAccess.isPublic("10.0.0.5"));
        assertFalse(InternetAccess.isPublic("100.72.1.1")); // carrier-grade NAT
        assertFalse(InternetAccess.isPublic("127.0.0.1"));
    }

    /** A fake UPnP router: serves a device description and answers SOAP calls. */
    @Test
    void mapsPortThroughUpnpRouter() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        HttpServer router = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        router.createContext("/desc.xml", ex -> reply(ex, 200, """
                <root><device><deviceList><device><serviceList>
                <service><serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType>
                  <controlURL>/common</controlURL></service>
                <service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
                  <controlURL>/ctl/IPConn</controlURL></service>
                </serviceList></device></deviceList></device></root>"""));
        router.createContext("/ctl/IPConn", ex -> {
            String action = ex.getRequestHeaders().getFirst("SOAPAction");
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(action + " " + body);
            if (action.contains("GetExternalIPAddress")) {
                reply(ex, 200, "<s:Envelope><s:Body><u:GetExternalIPAddressResponse>"
                        + "<NewExternalIPAddress>81.2.69.160</NewExternalIPAddress>"
                        + "</u:GetExternalIPAddressResponse></s:Body></s:Envelope>");
            } else {
                reply(ex, 200, "<s:Envelope><s:Body/></s:Envelope>");
            }
        });
        router.start();
        try {
            URL desc = new URL("http://127.0.0.1:" + router.getAddress().getPort() + "/desc.xml");
            PortMapper.Mapping m = PortMapper.mapVia(desc, 47777, "Laser Tag");
            assertNotNull(m);
            assertEquals("81.2.69.160", m.externalIp);
            String add = calls.get(0);
            assertTrue(add.contains("WANIPConnection:1#AddPortMapping"), add);
            assertTrue(add.contains("<NewExternalPort>47777</NewExternalPort>"), add);
            assertTrue(add.contains("<NewInternalClient>127.0.0.1</NewInternalClient>"), add);
            assertTrue(add.contains("<NewProtocol>TCP</NewProtocol>"), add);

            m.remove();
            assertTrue(calls.get(calls.size() - 1).contains("#DeletePortMapping"));
        } finally {
            router.stop(0);
        }
    }

    @Test
    void routerErrorsAreReported() throws Exception {
        HttpServer router = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        router.createContext("/desc.xml", ex -> reply(ex, 200,
                "<service><serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>"
                        + "<controlURL>/ppp</controlURL></service>"));
        router.createContext("/ppp", ex -> reply(ex, 500,
                "<s:Envelope><s:Body><s:Fault><detail><UPnPError><errorCode>718</errorCode>"
                        + "<errorDescription>ConflictInMappingEntry</errorDescription></UPnPError></detail>"
                        + "</s:Fault></s:Body></s:Envelope>"));
        router.start();
        try {
            URL desc = new URL("http://127.0.0.1:" + router.getAddress().getPort() + "/desc.xml");
            IOException e = assertThrows(IOException.class, () -> PortMapper.mapVia(desc, 47777, "Laser Tag"));
            assertTrue(e.getMessage().contains("ConflictInMappingEntry"), e.getMessage());
        } finally {
            router.stop(0);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
