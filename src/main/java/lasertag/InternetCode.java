package lasertag;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Short shareable codes like {@code K3F9Q-7TZ2M} that pack a host's public IPv4 address and
 * port (48 bits) plus a small checksum into 10 Crockford base-32 characters.
 */
final class InternetCode {
    private InternetCode() {}

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern SHAPE = Pattern.compile("[0-9A-Za-z]{5}-[0-9A-Za-z]{5}");

    /** Encodes "a.b.c.d" and a port, or returns null if the address is not IPv4. */
    static String encode(String ipv4, int port) {
        String[] parts = ipv4.split("\\.");
        if (parts.length != 4) return null;
        long value = 0;
        try {
            for (String part : parts) {
                int b = Integer.parseInt(part);
                if (b < 0 || b > 255) return null;
                value = value << 8 | b;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        value = value << 16 | (port & 0xFFFF);
        value = value << 2 | checksum(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 9; i >= 0; i--) sb.append(ALPHABET.charAt((int) (value >> (i * 5) & 31)));
        return sb.substring(0, 5) + "-" + sb.substring(5);
    }

    /** Mod 3 rather than a bit mask: any single-character change that isn't a multiple of 3 is caught. */
    private static int checksum(long value48) {
        return (int) (value48 % 3);
    }

    /** True if the text has the XXXXX-XXXXX shape of a code (it may still fail the checksum). */
    static boolean looksLikeCode(String text) {
        return SHAPE.matcher(text.trim()).matches();
    }

    /** Returns {"a.b.c.d", "port"}, or null if the code is invalid. */
    static String[] decode(String code) {
        String s = code.trim().toUpperCase(Locale.ROOT).replace("-", "").replace('O', '0').replace('I', '1').replace('L', '1');
        if (s.length() != 10) return null;
        long value = 0;
        for (char c : s.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) return null;
            value = value << 5 | v;
        }
        int check = (int) (value & 3);
        value >>= 2;
        if (checksum(value) != check) return null;
        int port = (int) (value & 0xFFFF);
        value >>= 16;
        StringBuilder ip = new StringBuilder();
        for (int i = 3; i >= 0; i--) ip.append(value >> (i * 8) & 0xFF).append(i > 0 ? "." : "");
        if (port == 0) return null;
        return new String[] {ip.toString(), Integer.toString(port)};
    }
}
