package lasertag;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Draws a {@link Session} and turns keyboard/mouse into {@link Input}s. */
final class GamePanel extends JPanel {
    enum Controls {
        /** One player: WASD/arrows to move, mouse to aim, click or Space to fire. */
        MOUSE,
        /** Two players sharing a keyboard; each faces the way they move. */
        SPLIT_KEYBOARD
    }

    private static final Color BG = new Color(0x07070F);
    private static final Color GRID = new Color(0x12122A);
    private static final Color WALL_FILL = new Color(0x0E1C33);
    private static final Color WALL_EDGE = new Color(0x2DE2E6);
    private static final Font HUD_BIG = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private static final Font HUD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font HUD_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font NAME = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final int TOP_BAR = 40, BOTTOM_BAR = 70;

    private static final class Particle {
        double x, y, vx, vy, life, maxLife;
        Color color;
    }

    private final LaserTag app;
    private final Session session;
    private final Controls controls;
    private final Timer timer;
    private final Area walls;

    private final Set<Integer> keys = new HashSet<>();
    private Point mouse;
    private boolean mouseDown;
    private long lastNanos;
    private AffineTransform worldToScreen = new AffineTransform();

    private final List<Particle> particles = new ArrayList<>();
    private final Set<Integer> seenBeams = new HashSet<>();
    private final Map<Integer, Boolean> wasTagged = new HashMap<>();
    private boolean wasRoundOver;

    GamePanel(LaserTag app, Session session, Controls controls) {
        this.app = app;
        this.session = session;
        this.controls = controls;
        this.walls = buildWalls(session.world().arena);
        setBackground(BG);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        if (controls == Controls.MOUSE) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keys.add(e.getKeyCode());
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> leave();
                    case KeyEvent.VK_P -> session.togglePause();
                    case KeyEvent.VK_M -> Sound.muted = !Sound.muted;
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

    private void leave() {
        app.showMenu();
    }

    private static Area buildWalls(Arena arena) {
        Area area = new Area();
        for (int r = 0; r < arena.rows; r++) {
            for (int c = 0; c < arena.cols; c++) {
                if (arena.solid(c, r)) {
                    area.add(new Area(new Rectangle2D.Double(c * Arena.CELL, r * Arena.CELL, Arena.CELL, Arena.CELL)));
                }
            }
        }
        return area;
    }

    // ---------------------------------------------------------------- loop

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;

        if (session.error() == null) {
            readInputs();
            session.update(dt);
            detectEvents();
        }
        updateParticles(dt);
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
                double[] w = screenToWorld(mouse.x, mouse.y);
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

    /** Spawns sparks and plays sounds for things that happened since the last frame. */
    private void detectEvents() {
        GameWorld world = session.world();
        Set<Integer> current = new HashSet<>();
        for (Beam b : world.beams) {
            current.add(b.id);
            if (seenBeams.contains(b.id) || b.points.isEmpty()) continue;
            Player owner = world.players.get(b.owner);
            Color c = owner != null ? Rules.COLORS[owner.color] : Color.WHITE;
            double[] end = b.points.get(b.points.size() - 1);
            burst(end[0], end[1], c, 8, 120);
            Sound.play(Sound.ZAP);
        }
        seenBeams.clear();
        seenBeams.addAll(current);

        for (Player p : world.players.values()) {
            Boolean before = wasTagged.put(p.id, p.tagged);
            if (p.tagged && before != null && !before) {
                burst(p.x, p.y, Rules.COLORS[p.color], 26, 220);
                Sound.play(Sound.TAG);
            }
        }
        wasTagged.keySet().retainAll(world.players.keySet());

        if (world.roundOver && !wasRoundOver) Sound.play(Sound.ROUND);
        wasRoundOver = world.roundOver;
    }

    private void burst(double x, double y, Color color, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double a = Math.random() * Math.PI * 2, s = speed * (0.3 + Math.random());
            p.x = x;
            p.y = y;
            p.vx = Math.cos(a) * s;
            p.vy = Math.sin(a) * s;
            p.maxLife = p.life = 0.25 + Math.random() * 0.35;
            p.color = color;
            particles.add(p);
        }
    }

    private void updateParticles(double dt) {
        particles.removeIf(p -> {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 1 - 3 * dt;
            p.vy *= 1 - 3 * dt;
            return (p.life -= dt) <= 0;
        });
    }

    private double[] screenToWorld(double sx, double sy) {
        try {
            double[] pt = {sx, sy};
            worldToScreen.inverseTransform(pt, 0, pt, 0, 1);
            return pt;
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return new double[] {sx, sy};
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        GameWorld world = session.world();
        Arena arena = world.arena;
        int w = getWidth(), h = getHeight();
        int playH = h - TOP_BAR - BOTTOM_BAR;
        double scale = Math.min(w / arena.width(), playH / arena.height());
        double ox = (w - arena.width() * scale) / 2, oy = TOP_BAR + (playH - arena.height() * scale) / 2;
        worldToScreen = new AffineTransform(scale, 0, 0, scale, ox, oy);

        Graphics2D wg = (Graphics2D) g.create();
        wg.transform(worldToScreen);
        drawArena(wg, arena);
        drawBeams(wg, world);
        for (Player p : world.players.values()) drawPlayer(wg, p);
        drawParticles(wg);
        wg.dispose();

        drawHud(g, world, w, h);
        g.dispose();
    }

    private void drawArena(Graphics2D g, Arena arena) {
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1));
        for (int c = 0; c <= arena.cols; c++) g.draw(new Line2D.Double(c * Arena.CELL, 0, c * Arena.CELL, arena.height()));
        for (int r = 0; r <= arena.rows; r++) g.draw(new Line2D.Double(0, r * Arena.CELL, arena.width(), r * Arena.CELL));

        g.setColor(WALL_FILL);
        g.fill(walls);
        g.setColor(new Color(WALL_EDGE.getRed(), WALL_EDGE.getGreen(), WALL_EDGE.getBlue(), 40));
        g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(walls);
        g.setColor(WALL_EDGE);
        g.setStroke(new BasicStroke(2));
        g.draw(walls);
    }

    private void drawBeams(Graphics2D g, GameWorld world) {
        for (Beam b : world.beams) {
            if (b.points.size() < 2) continue;
            Path2D.Double path = new Path2D.Double();
            path.moveTo(b.points.get(0)[0], b.points.get(0)[1]);
            for (int i = 1; i < b.points.size(); i++) path.lineTo(b.points.get(i)[0], b.points.get(i)[1]);
            Player owner = world.players.get(b.owner);
            Color c = owner != null ? Rules.COLORS[owner.color] : Color.WHITE;
            float a = (float) Math.max(0, Math.min(1, b.life / Rules.BEAM_LIFE));
            BasicStroke wide = new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            BasicStroke mid = new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            BasicStroke core = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            g.setColor(withAlpha(c, a * 0.22f));
            g.setStroke(wide);
            g.draw(path);
            g.setColor(withAlpha(c, a * 0.6f));
            g.setStroke(mid);
            g.draw(path);
            g.setColor(withAlpha(Color.WHITE, a));
            g.setStroke(core);
            g.draw(path);
        }
    }

    private void drawPlayer(Graphics2D g, Player p) {
        Color c = Rules.COLORS[p.color];
        double r = Rules.PLAYER_RADIUS, x = p.rx, y = p.ry;
        boolean local = isLocal(p.id);

        if (p.tagged) {
            boolean blink = (System.currentTimeMillis() / 150) % 2 == 0;
            g.setColor(withAlpha(c, blink ? 0.55f : 0.25f));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {4, 4}, 0));
            g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            drawCentered(g, String.format("%.1f", Math.max(0, p.respawnTimer)), x, y + 4, NAME, withAlpha(c, 0.8f));
            return;
        }

        g.setColor(withAlpha(c, 0.18f));
        g.fill(new Ellipse2D.Double(x - r - 9, y - r - 9, (r + 9) * 2, (r + 9) * 2));
        if (local) {
            g.setColor(withAlpha(Color.WHITE, 0.5f));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2));
        }
        g.setColor(new Color(0x14141F));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(c);
        g.setStroke(new BasicStroke(3));
        g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));

        double dx = Math.cos(p.ra), dy = Math.sin(p.ra);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + dx * 4, y + dy * 4, x + dx * (r + 7), y + dy * (r + 7)));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + dx * 6, y + dy * 6, x + dx * (r + 6), y + dy * (r + 6)));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));

        if (p.shield > 0) {
            float pulse = (float) (0.4 + 0.3 * Math.sin(System.currentTimeMillis() / 80.0));
            g.setColor(withAlpha(new Color(0xBFE9FF), pulse));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(x - r - 5, y - r - 5, (r + 5) * 2, (r + 5) * 2));
        }

        drawCentered(g, p.name, x, y - r - 12, NAME, withAlpha(c, 0.9f));
    }

    private void drawParticles(Graphics2D g) {
        for (Particle p : particles) {
            float a = (float) (p.life / p.maxLife);
            g.setColor(withAlpha(p.color, a));
            double s = 2 + 2 * a;
            g.fill(new Ellipse2D.Double(p.x - s / 2, p.y - s / 2, s, s));
        }
    }

    private void drawHud(Graphics2D g, GameWorld world, int w, int h) {
        // Top bar: round and clock.
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, TOP_BAR);
        g.fillRect(0, h - BOTTOM_BAR, w, BOTTOM_BAR);
        int secs = (int) Math.ceil(world.timeLeft);
        String clock = String.format("%d:%02d", secs / 60, secs % 60);
        drawCentered(g, clock, w / 2.0, 30, HUD_BIG, world.timeLeft < 20 ? new Color(0xFF6B6B) : Color.WHITE);
        g.setFont(HUD);
        g.setColor(new Color(0x9AA4C7));
        g.drawString("ROUND " + world.round + "   -   first to " + Rules.SCORE_LIMIT + "   -   " + world.arena.name, 14, 26);
        String hint = "Esc menu   " + (session.canPause() ? "P pause   " : "") + "M " + (Sound.muted ? "unmute" : "mute");
        FontMetrics fm = g.getFontMetrics(HUD_SMALL);
        g.setFont(HUD_SMALL);
        g.drawString(hint, w - fm.stringWidth(hint) - 14, 25);

        drawScoreboard(g, world, w);
        drawFeed(g, world);
        drawLocalStatus(g, world, w, h);

        String status = session.status();
        if (status != null) {
            g.setFont(HUD_SMALL);
            g.setColor(new Color(0x7F8AB0));
            g.drawString(status, 14, h - BOTTOM_BAR / 2 + 4);
        }

        if (world.roundOver) drawRoundOver(g, world, w, h);
        if (session.paused()) drawBanner(g, w, h, "PAUSED", "Press P to resume");
        if (session.error() != null) drawBanner(g, w, h, "DISCONNECTED", session.error() + "  -  press Esc");
    }

    private void drawScoreboard(Graphics2D g, GameWorld world, int w) {
        List<Player> ranking = world.ranking();
        int rowH = 20, bw = 190, x = w - bw - 12, y = 52;
        Graphics2D original = g;
        g = (Graphics2D) g.create();
        if (coversPlayer(world, x, y, bw, 10 + rowH * ranking.size())) g.setComposite(AlphaComposite.SrcOver.derive(0.3f));
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x, y, bw, 10 + rowH * ranking.size(), 10, 10);
        g.setFont(HUD);
        FontMetrics fm = g.getFontMetrics();
        int row = 0;
        for (Player p : ranking) {
            int ty = y + 20 + row * rowH;
            Color c = Rules.COLORS[p.color];
            g.setColor(c);
            g.fillOval(x + 10, ty - 10, 10, 10);
            g.setColor(isLocal(p.id) ? Color.WHITE : new Color(0xC8CEE6));
            g.drawString(p.name + (p.bot ? " •" : ""), x + 28, ty);
            String score = Integer.toString(p.score);
            g.setColor(c);
            g.drawString(score, x + bw - 12 - fm.stringWidth(score), ty);
            row++;
        }
        g.dispose();
        g = original;
    }

    private void drawFeed(Graphics2D g, GameWorld world) {
        g = (Graphics2D) g.create();
        g.setFont(HUD);
        int widest = 0;
        for (GameWorld.FeedItem f : world.feed) widest = Math.max(widest, g.getFontMetrics().stringWidth(f.text));
        if (coversPlayer(world, 10, 49, widest + 16, world.feed.size() * 25)) g.setComposite(AlphaComposite.SrcOver.derive(0.3f));
        int y = 64;
        for (GameWorld.FeedItem f : world.feed) {
            float a = (float) Math.min(1, f.ttl);
            g.setColor(withAlpha(Color.BLACK, a * 0.5f));
            g.fillRoundRect(10, y - 15, g.getFontMetrics().stringWidth(f.text) + 16, 21, 8, 8);
            g.setColor(withAlpha(Color.WHITE, a));
            g.drawString(f.text, 18, y);
            y += 25;
        }
        g.dispose();
    }

    /** True if any player is drawn under the given screen rectangle, so overlays can fade out of the way. */
    private boolean coversPlayer(GameWorld world, int x, int y, int width, int height) {
        java.awt.Rectangle r = new java.awt.Rectangle(x - 20, y - 20, width + 40, height + 40);
        for (Player p : world.players.values()) {
            java.awt.geom.Point2D pt = worldToScreen.transform(new java.awt.geom.Point2D.Double(p.rx, p.ry), null);
            if (r.contains(pt)) return true;
        }
        return false;
    }

    private void drawLocalStatus(Graphics2D g, GameWorld world, int w, int h) {
        int n = session.localCount();
        for (int slot = 0; slot < n; slot++) {
            Player p = world.players.get(session.localPlayerId(slot));
            if (p == null) continue;
            Color c = Rules.COLORS[p.color];
            int bw = 220, bh = 54;
            int x = n == 1 ? (w - bw) / 2 : slot == 0 ? 14 : w - bw - 14;
            int y = h - bh - 8;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(x, y, bw, bh, 12, 12);
            g.setColor(c);
            g.setFont(HUD);
            String label = p.name + (n > 1 ? (slot == 0 ? "  [WASD + Space]" : "  [Arrows + Enter]") : "");
            g.drawString(label, x + 12, y + 20);

            // Energy, drawn as one cell per available shot.
            int cells = (int) (Rules.MAX_ENERGY / Rules.SHOT_COST);
            int cw = (bw - 24 - (cells - 1) * 4) / cells;
            for (int i = 0; i < cells; i++) {
                double fill = Math.max(0, Math.min(1, (p.energy - i * Rules.SHOT_COST) / Rules.SHOT_COST));
                int cx = x + 12 + i * (cw + 4), cy = y + 30;
                g.setColor(new Color(0x22263A));
                g.fillRoundRect(cx, cy, cw, 12, 4, 4);
                g.setColor(fill >= 1 ? c : withAlpha(c, 0.45f));
                g.fillRoundRect(cx, cy, (int) (cw * fill), 12, 4, 4);
            }

            if (p.tagged && !world.roundOver) {
                String msg = "TAGGED!  back in " + String.format("%.1f", Math.max(0, p.respawnTimer));
                if (n == 1) {
                    drawCentered(g, msg, w / 2.0, h / 2.0 - 60, HUD_BIG, c);
                } else {
                    drawCentered(g, msg, x + bw / 2.0, y - 10, HUD, c);
                }
            }
        }
    }

    private void drawRoundOver(Graphics2D g, GameWorld world, int w, int h) {
        List<Player> ranking = world.ranking();
        int bw = 380, bh = 120 + ranking.size() * 26;
        int x = (w - bw) / 2, y = (h - bh) / 2;
        g.setColor(new Color(5, 5, 15, 225));
        g.fillRoundRect(x, y, bw, bh, 18, 18);
        g.setColor(WALL_EDGE);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, bw, bh, 18, 18);

        boolean draw = ranking.size() > 1 && ranking.get(0).score == ranking.get(1).score;
        String title = ranking.isEmpty() ? "ROUND OVER" : draw ? "IT'S A DRAW" : ranking.get(0).name.toUpperCase() + " WINS";
        drawCentered(g, title, w / 2.0, y + 42, HUD_BIG, draw || ranking.isEmpty() ? Color.WHITE : Rules.COLORS[ranking.get(0).color]);

        g.setFont(HUD);
        int ty = y + 80;
        int place = 1;
        for (Player p : ranking) {
            g.setColor(Rules.COLORS[p.color]);
            g.drawString(place++ + ".  " + p.name, x + 40, ty);
            g.setColor(Color.WHITE);
            String stats = plural(p.score, "tag") + "   tagged " + p.timesTagged + "x";
            g.drawString(stats, x + bw - 40 - g.getFontMetrics().stringWidth(stats), ty);
            ty += 26;
        }
        drawCentered(g, "Next round in " + (int) Math.ceil(Math.max(0, world.overTimer)), w / 2.0, y + bh - 18,
                HUD_SMALL, new Color(0x9AA4C7));
    }

    private void drawBanner(Graphics2D g, int w, int h, String title, String sub) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, w, h);
        drawCentered(g, title, w / 2.0, h / 2.0 - 6, HUD_BIG, Color.WHITE);
        drawCentered(g, sub, w / 2.0, h / 2.0 + 24, HUD, new Color(0xC8CEE6));
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private boolean isLocal(int id) {
        for (int i = 0; i < session.localCount(); i++) if (session.localPlayerId(i) == id) return true;
        return false;
    }

    private static void drawCentered(Graphics2D g, String text, double cx, double baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baseline);
    }

    private static Color withAlpha(Color c, float a) {
        int alpha = Math.max(0, Math.min(255, Math.round(a * 255)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
