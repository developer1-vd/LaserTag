package lasertag;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private double scale = 1;
    private int offsetX, offsetY;
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

        // Whole-number scaling keeps every pixel the same size; small windows fall back to fractional.
        double fit = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        scale = fit >= 2 ? Math.floor(fit) : fit;
        int dw = (int) (W * scale), dh = (int) (H * scale);
        offsetX = (getWidth() - dw) / 2;
        offsetY = (getHeight() - dh) / 2;

        Graphics2D g = (Graphics2D) graphics.create();
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
        return new double[] {(x - offsetX) / scale, (y - offsetY) / scale};
    }
}
