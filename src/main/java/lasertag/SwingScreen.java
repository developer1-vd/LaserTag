package lasertag;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** {@link Screen} on top of a Swing Graphics2D. */
final class SwingScreen implements Screen {
    private final Graphics2D g;
    private final Map<String, BufferedImage> cache;

    SwingScreen(Graphics2D g) {
        this(g, new HashMap<>());
    }

    /** {@code cache} keeps pre-drawn images between frames; pass the same map every frame. */
    SwingScreen(Graphics2D g, Map<String, BufferedImage> cache) {
        this.g = g;
        this.cache = cache;
    }

    private void color(int argb) {
        g.setColor(new Color(argb, true));
    }

    @Override
    public void fill(int x, int y, int w, int h, int argb) {
        color(argb);
        g.fillRect(x, y, w, h);
    }

    @Override
    public void line(int x0, int y0, int x1, int y1, int width, int argb) {
        color(argb);
        g.setStroke(new BasicStroke(width));
        g.drawLine(x0, y0, x1, y1);
        g.setStroke(new BasicStroke(1));
    }

    @Override
    public void cached(String key, int x, int y, int w, int h, Consumer<Screen> painter) {
        g.drawImage(cache.computeIfAbsent(key, k -> image(w, h, painter)), x, y, null);
    }

    /** Draws {@code painter} into a new image. */
    static BufferedImage image(int w, int h, Consumer<Screen> painter) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = img.createGraphics();
        painter.accept(new SwingScreen(ig));
        ig.dispose();
        return img;
    }
}
