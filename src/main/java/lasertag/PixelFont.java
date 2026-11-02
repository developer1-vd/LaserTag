package lasertag;

import java.awt.Color;
import java.awt.Graphics2D;

/** Desktop convenience: draws {@link Text} with Swing colours onto a Graphics2D. */
final class PixelFont {
    private PixelFont() {}

    static final int ADVANCE = Text.ADVANCE, HEIGHT = Text.HEIGHT;

    static boolean supports(char c) {
        return Text.supports(c);
    }

    static int width(String text, int scale) {
        return Text.width(text, scale);
    }

    static void draw(Graphics2D g, String text, int x, int y, int scale, Color color) {
        Text.draw(new SwingScreen(g), text, x, y, scale, color.getRGB());
    }

    static void drawShadowed(Graphics2D g, String text, int x, int y, int scale, Color color) {
        Text.drawShadowed(new SwingScreen(g), text, x, y, scale, color.getRGB());
    }

    static void drawCentered(Graphics2D g, String text, int cx, int y, int scale, Color color) {
        Text.drawCentered(new SwingScreen(g), text, cx, y, scale, color.getRGB());
    }
}
