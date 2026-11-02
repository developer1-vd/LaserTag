package lasertag;

import java.util.HashMap;
import java.util.Map;

/** A hand-drawn 5x7 bitmap font drawn onto a {@link Screen}. Upper case only; lower case is shown as upper case. */
final class Text {
    private Text() {}

    static final int ADVANCE = 6, HEIGHT = 7;

    private static final Map<Character, boolean[][]> GLYPHS = new HashMap<>();

    private static void def(char c, String rows) {
        String[] r = rows.split(" ");
        boolean[][] g = new boolean[7][5];
        for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++) g[y][x] = r[y].charAt(x) == '#';
        GLYPHS.put(c, g);
    }

    static {
        def('A', ".###. #...# #...# ##### #...# #...# #...#");
        def('B', "####. #...# #...# ####. #...# #...# ####.");
        def('C', ".###. #...# #.... #.... #.... #...# .###.");
        def('D', "####. #...# #...# #...# #...# #...# ####.");
        def('E', "##### #.... #.... ####. #.... #.... #####");
        def('F', "##### #.... #.... ####. #.... #.... #....");
        def('G', ".###. #...# #.... #.### #...# #...# .####");
        def('H', "#...# #...# #...# ##### #...# #...# #...#");
        def('I', ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.");
        def('J', "..### ...#. ...#. ...#. ...#. #..#. .##..");
        def('K', "#...# #..#. #.#.. ##... #.#.. #..#. #...#");
        def('L', "#.... #.... #.... #.... #.... #.... #####");
        def('M', "#...# ##.## #.#.# #.#.# #...# #...# #...#");
        def('N', "#...# #...# ##..# #.#.# #..## #...# #...#");
        def('O', ".###. #...# #...# #...# #...# #...# .###.");
        def('P', "####. #...# #...# ####. #.... #.... #....");
        def('Q', ".###. #...# #...# #...# #.#.# #..#. .##.#");
        def('R', "####. #...# #...# ####. #.#.. #..#. #...#");
        def('S', ".#### #.... #.... .###. ....# ....# ####.");
        def('T', "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..");
        def('U', "#...# #...# #...# #...# #...# #...# .###.");
        def('V', "#...# #...# #...# #...# #...# .#.#. ..#..");
        def('W', "#...# #...# #...# #.#.# #.#.# #.#.# .#.#.");
        def('X', "#...# #...# .#.#. ..#.. .#.#. #...# #...#");
        def('Y', "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..");
        def('Z', "##### ....# ...#. ..#.. .#... #.... #####");
        def('0', ".###. #...# #..## #.#.# ##..# #...# .###.");
        def('1', "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.");
        def('2', ".###. #...# ....# ...#. ..#.. .#... #####");
        def('3', "##### ...#. ..#.. ...#. ....# #...# .###.");
        def('4', "...#. ..##. .#.#. #..#. ##### ...#. ...#.");
        def('5', "##### #.... ####. ....# ....# #...# .###.");
        def('6', "..##. .#... #.... ####. #...# #...# .###.");
        def('7', "##### ....# ...#. ..#.. .#... .#... .#...");
        def('8', ".###. #...# #...# .###. #...# #...# .###.");
        def('9', ".###. #...# #...# .#### ....# ...#. .##..");
        def(' ', "..... ..... ..... ..... ..... ..... .....");
        def('.', "..... ..... ..... ..... ..... .##.. .##..");
        def(',', "..... ..... ..... ..... .##.. ..#.. .#...");
        def(':', "..... .##.. .##.. ..... .##.. .##.. .....");
        def(';', "..... .##.. .##.. ..... .##.. ..#.. .#...");
        def('!', "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..");
        def('?', ".###. #...# ....# ...#. ..#.. ..... ..#..");
        def('-', "..... ..... ..... .###. ..... ..... .....");
        def('+', "..... ..#.. ..#.. ##### ..#.. ..#.. .....");
        def('/', "....# ....# ...#. ..#.. .#... #.... #....");
        def('(', "...#. ..#.. .#... .#... .#... ..#.. ...#.");
        def(')', ".#... ..#.. ...#. ...#. ...#. ..#.. .#...");
        def('[', ".###. .#... .#... .#... .#... .#... .###.");
        def(']', ".###. ...#. ...#. ...#. ...#. ...#. .###.");
        def('<', "...#. ..#.. .#... #.... .#... ..#.. ...#.");
        def('>', ".#... ..#.. ...#. ....# ...#. ..#.. .#...");
        def('=', "..... ..... ##### ..... ##### ..... .....");
        def('_', "..... ..... ..... ..... ..... ..... #####");
        def('\'', "..#.. ..#.. .#... ..... ..... ..... .....");
        def('"', ".#.#. .#.#. ..... ..... ..... ..... .....");
        def('%', "##..# ##..# ...#. ..#.. .#... #..## #..##");
        def('#', ".#.#. .#.#. ##### .#.#. ##### .#.#. .#.#.");
        def('*', "..... #.#.# .###. ##### .###. #.#.# .....");
        def('&', ".##.. #..#. #.#.. .#... #.#.# #..#. .##.#");
        def('@', ".###. #...# #.### #.#.# #.### #.... .####");
    }

    /** True if the character has a glyph (after upper-casing). */
    static boolean supports(char c) {
        return GLYPHS.containsKey(Character.toUpperCase(c));
    }

    static int width(String text, int scale) {
        return text.isEmpty() ? 0 : (text.length() * ADVANCE - 1) * scale;
    }

    /** Draws text with its top-left corner at (x, y). */
    static void draw(Screen s, String text, int x, int y, int scale, int argb) {
        for (int i = 0; i < text.length(); i++) {
            boolean[][] glyph = GLYPHS.get(Character.toUpperCase(text.charAt(i)));
            if (glyph == null) glyph = GLYPHS.get('?');
            int gx = x + i * ADVANCE * scale;
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (glyph[row][col]) s.fill(gx + col * scale, y + row * scale, scale, scale, argb);
                }
            }
        }
    }

    /** Draws text with a one-pixel drop shadow so it reads over anything. */
    static void drawShadowed(Screen s, String text, int x, int y, int scale, int argb) {
        draw(s, text, x + scale, y + scale, scale, 0xFF000000);
        draw(s, text, x, y, scale, argb);
    }

    static void drawCentered(Screen s, String text, int cx, int y, int scale, int argb) {
        drawShadowed(s, text, cx - width(text, scale) / 2, y, scale, argb);
    }
}
