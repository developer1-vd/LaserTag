package lasertag;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.Timer;

/** Desktop game screen: turns keyboard/mouse into {@link Input}s and draws with {@link GameRenderer}. */
final class GamePanel extends RetroScreen {
    enum Controls {
        /** One player: WASD/arrows to move, mouse to aim, click or Space to fire. */
        MOUSE,
        /** Two players sharing a keyboard; each faces the way they move. */
        SPLIT_KEYBOARD
    }

    static final String[] MOUSE_HINTS = {"WASD MOVE  MOUSE AIM  CLICK FIRE", "HOLD TAB: SCORES + INFO   ESC: MENU"};
    static final String[] SPLIT_HINTS = {"P1 WASD+SPACE   P2 ARROWS+ENTER", "HOLD TAB: SCORES + INFO   ESC: MENU"};

    private final Session session;
    private final Controls controls;
    private final GameRenderer renderer;
    private final Timer timer;
    private final Map<String, BufferedImage> imageCache = new HashMap<>();

    private final Set<Integer> keys = new HashSet<>();
    private Point mouse;
    private boolean mouseDown;
    private long lastNanos;

    GamePanel(LaserTag app, Session session, Controls controls) {
        this.session = session;
        this.controls = controls;
        this.renderer = new GameRenderer(session, Sound.SFX);
        if (controls == Controls.MOUSE) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keys.add(e.getKeyCode());
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> app.showMenu();
                    case KeyEvent.VK_P -> session.togglePause();
                    case KeyEvent.VK_M -> Sound.muted = !Sound.muted;
                    case KeyEvent.VK_C -> RetroScreen.scanlines = !RetroScreen.scanlines;
                    default -> { }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keys.remove(e.getKeyCode());
            }
        });
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouse = e.getPoint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mouse = e.getPoint();
                if (e.getButton() == MouseEvent.BUTTON1) mouseDown = true;
                requestFocusInWindow();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) mouseDown = false;
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                keys.clear();
                mouseDown = false;
            }
        });

        timer = new Timer(15, e -> tick());
        timer.setCoalesce(true);
    }

    void start() {
        lastNanos = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    void stop() {
        timer.stop();
        session.close();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (session.error() == null) {
            readInputs();
            session.update(dt);
        }
        renderer.tick(dt);
        shakeX = renderer.shakeX();
        shakeY = renderer.shakeY();
        repaint();
        Toolkit.getDefaultToolkit().sync();
    }

    private void readInputs() {
        if (controls == Controls.MOUSE) {
            double mx = axis(KeyEvent.VK_A, KeyEvent.VK_D) + axis(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT);
            double my = axis(KeyEvent.VK_W, KeyEvent.VK_S) + axis(KeyEvent.VK_UP, KeyEvent.VK_DOWN);
            double aim = Double.NaN;
            Player me = session.world().players.get(session.localPlayerId(0));
            if (me != null && mouse != null) {
                double[] c = toCanvas(mouse.x, mouse.y);
                double[] w = renderer.toWorld(c[0], c[1]);
                aim = Math.atan2(w[1] - me.ry, w[0] - me.rx);
            }
            boolean fire = mouseDown || keys.contains(KeyEvent.VK_SPACE);
            session.setInput(0, new Input(clamp(mx), clamp(my), aim, fire));
        } else {
            session.setInput(0, new Input(axis(KeyEvent.VK_A, KeyEvent.VK_D), axis(KeyEvent.VK_W, KeyEvent.VK_S),
                    Double.NaN, keys.contains(KeyEvent.VK_SPACE) || keys.contains(KeyEvent.VK_F)));
            session.setInput(1, new Input(axis(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT), axis(KeyEvent.VK_UP, KeyEvent.VK_DOWN),
                    Double.NaN, keys.contains(KeyEvent.VK_ENTER) || keys.contains(KeyEvent.VK_SLASH)
                            || keys.contains(KeyEvent.VK_NUMPAD0)));
        }
    }

    private double axis(int negative, int positive) {
        return (keys.contains(positive) ? 1 : 0) - (keys.contains(negative) ? 1 : 0);
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    @Override
    protected void render(Graphics2D g) {
        renderer.render(new SwingScreen(g, imageCache), keys.contains(KeyEvent.VK_TAB),
                controls == Controls.MOUSE ? MOUSE_HINTS : SPLIT_HINTS);
    }

    /** The arena drawing as an image, for the menu's preview. */
    static BufferedImage drawArena(Arena arena, int width, int height) {
        return SwingScreen.image(width, height, s -> GameRenderer.drawArena(s, arena, width, height));
    }

    static String shortName(String name, int max) {
        return GameRenderer.shortName(name, max);
    }
}
