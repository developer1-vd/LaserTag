package lasertag;

import java.util.function.Consumer;

/**
 * The few drawing operations the game needs, so the same renderer can draw with Swing on the
 * desktop and onto an HTML canvas in the browser. Colours are 0xAARRGGBB.
 */
interface Screen {
    void fill(int x, int y, int w, int h, int argb);

    void line(int x0, int y0, int x1, int y1, int width, int argb);

    /**
     * Draws an image that {@code painter} draws once into a w x h buffer and that is then reused
     * under {@code key} (e.g. the arena walls, which never change).
     */
    void cached(String key, int x, int y, int w, int h, Consumer<Screen> painter);

    /** Draws a one-pixel rectangle outline. */
    default void outline(int x, int y, int w, int h, int argb) {
        fill(x, y, w, 1, argb);
        fill(x, y + h - 1, w, 1, argb);
        fill(x, y, 1, h, argb);
        fill(x + w - 1, y, 1, h, argb);
    }

    /** Opaque colour from 0xRRGGBB. */
    static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** The same colour with a new alpha (0-255). */
    static int alpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Darkens a colour, like java.awt.Color.darker(). */
    static int darker(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * 0.7), g = (int) (((argb >> 8) & 0xFF) * 0.7), b = (int) ((argb & 0xFF) * 0.7);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
