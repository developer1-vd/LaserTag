package lasertag;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * A screen drawn at a fixed low resolution (480x360, 4:3) and blown up with chunky
 * nearest-neighbour pixels and optional CRT scanlines, like an old arcade monitor.
 */
abstract class RetroScreen extends JPanel {
    static final int W = 480, H = 360;

    /** Shared CRT scanline toggle (C key). */
    static boolean scanlines = true;

    private final BufferedImage canvas = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    /** Canvas-pixel size in physical screen pixels, and the canvas origin in physical pixels. */
    private double scale = 1;
    private int offsetX, offsetY;
    /** Physical pixels per logical pixel (e.g. 1.5 with Windows display scaling at 150%, 2 on Retina). */
    private double deviceScale = 1;
    /** Screen shake offset in canvas pixels, applied when blitting. */
    protected int shakeX, shakeY;

    RetroScreen() {
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
    }

    /** Draws one frame onto the low-resolution canvas. */
    protected abstract void render(Graphics2D g);

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D c = canvas.createGraphics();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        c.setColor(Color.BLACK);
        c.fillRect(0, 0, W, H);
        render(c);
        c.dispose();

        // Work in physical pixels so high-DPI displays (Windows scaling, Retina) keep pixels crisp.
        Graphics2D g = (Graphics2D) graphics.create();
        AffineTransform t = g.getTransform();
        deviceScale = t.getScaleX() > 0 ? t.getScaleX() : 1;
        g.setTransform(new AffineTransform(1, 0, 0, 1, t.getTranslateX(), t.getTranslateY()));
        int pw = (int) Math.round(getWidth() * deviceScale), ph = (int) Math.round(getHeight() * deviceScale);

        // Whole-number scaling keeps every pixel the same size; small windows fall back to fractional.
        double fit = Math.min(pw / (double) W, ph / (double) H);
        scale = fit >= 2 ? Math.floor(fit) : fit;
        int dw = (int) (W * scale), dh = (int) (H * scale);
        offsetX = (pw - dw) / 2;
        offsetY = (ph - dh) / 2;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pw, ph);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int sx = (int) (shakeX * scale), sy = (int) (shakeY * scale);
        g.drawImage(canvas, offsetX + sx, offsetY + sy, dw, dh, null);
        if (scanlines && scale >= 2) {
            g.setColor(new Color(0, 0, 0, scale >= 3 ? 110 : 70));
            int line = Math.max(1, (int) scale / 3);
            for (int y = 0; y < H; y++) g.fillRect(offsetX, offsetY + (int) ((y + 1) * scale) - line, dw, line);
        }
        g.dispose();
    }

    /** Converts a point on this component to canvas pixel coordinates. */
    protected double[] toCanvas(int x, int y) {
        return new double[] {(x * deviceScale - offsetX) / scale, (y * deviceScale - offsetY) / scale};
    }
}
