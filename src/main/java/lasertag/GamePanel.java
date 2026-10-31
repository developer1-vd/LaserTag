package lasertag;

import java.awt.BasicStroke;
import java.awt.Color;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Timer;

/** Draws a {@link Session} as a low-res arcade screen and turns keyboard/mouse into {@link Input}s. */
final class GamePanel extends RetroScreen {
    enum Controls {
        /** One player: WASD/arrows to move, mouse to aim, click or Space to fire. */
        MOUSE,
        /** Two players sharing a keyboard; each faces the way they move. */
        SPLIT_KEYBOARD
    }

    static final int TOP = 20, BOTTOM = 20;
    private static final Color FLOOR_DOT = new Color(0x1D2B53);
    private static final Color WALL = new Color(0x1D2B53);
    private static final Color WALL_LIGHT = new Color(0x6A89D8);
    private static final Color WALL_DARK = new Color(0x0B1230);
    private static final Color WALL_TEXTURE = new Color(0x253666);
    private static final Color DIM = new Color(0x83769C);
    private static final Color GREY = new Color(0xC2C3C7);
    private static final Color RED = new Color(0xFF004D);
    private static final Color HINT_COLOR = new Color(0x29ADFF);

    /** 11x11 circular sprite mask: 0 empty, 1 outline, 2 body. */
    private static final int[][] SPRITE = buildSprite();

    private static final class Particle {
        double x, y, vx, vy, life;
        Color color;
    }

    private final LaserTag app;
    private final Session session;
    private final Controls controls;
    private final Timer timer;
    private BufferedImage arenaImage;
    private double ws = 1;

    private final Set<Integer> keys = new HashSet<>();
    private Point mouse;
    private boolean mouseDown;
    private long lastNanos;
    private double clock, shake;

    private final List<Particle> particles = new ArrayList<>();
    private final Set<Integer> seenBeams = new HashSet<>();
    private final Map<Integer, Boolean> wasTagged = new HashMap<>();
    private boolean wasRoundOver;

    GamePanel(LaserTag app, Session session, Controls controls) {
        this.app = app;
        this.session = session;
        this.controls = controls;
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

    // ---------------------------------------------------------------- loop

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        clock += dt;

        if (session.error() == null) {
            readInputs();
            session.update(dt);
            detectEvents();
        }
        updateParticles(dt);
        shake = Math.max(0, shake - dt);
        shakeX = shake > 0 ? (int) Math.round((Math.random() - 0.5) * 4) : 0;
        shakeY = shake > 0 ? (int) Math.round((Math.random() - 0.5) * 4) : 0;
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
                aim = Math.atan2((c[1] - TOP) / ws - me.ry, c[0] / ws - me.rx);
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

    /** Spawns sparks, shakes the screen and plays sounds for things that happened since the last frame. */
    private void detectEvents() {
        GameWorld world = session.world();
        Set<Integer> current = new HashSet<>();
        for (Beam b : world.beams) {
            current.add(b.id);
            if (seenBeams.contains(b.id) || b.points.isEmpty()) continue;
            Player owner = world.players.get(b.owner);
            double[] end = b.points.get(b.points.size() - 1);
            burst(end[0], end[1], owner != null ? Rules.COLORS[owner.color] : Color.WHITE, 6, 140);
            Sound.play(Sound.ZAP);
        }
        seenBeams.clear();
        seenBeams.addAll(current);

        for (Player p : world.players.values()) {
            Boolean before = wasTagged.put(p.id, p.tagged);
            if (p.tagged && before != null && !before) {
                burst(p.x, p.y, Rules.COLORS[p.color], 24, 260);
                burst(p.x, p.y, Color.WHITE, 8, 160);
                Sound.play(Sound.TAG);
                if (isLocal(p.id)) shake = 0.3;
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
            p.life = 0.2 + Math.random() * 0.4;
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

    private boolean isLocal(int id) {
        for (int i = 0; i < session.localCount(); i++) if (session.localPlayerId(i) == id) return true;
        return false;
    }

    // ---------------------------------------------------------------- drawing

    private int sx(double x) {
        return (int) Math.round(x * ws);
    }

    private int sy(double y) {
        return TOP + (int) Math.round(y * ws);
    }

    @Override
    protected void render(Graphics2D g) {
        GameWorld world = session.world();
        if (arenaImage == null) {
            ws = W / world.arena.width();
            arenaImage = drawArena(world.arena, W, H - TOP - BOTTOM);
        }
        g.drawImage(arenaImage, 0, TOP, null);
        drawBeams(g, world);
        for (Player p : world.players.values()) drawPlayer(g, p);
        for (Particle p : particles) {
            g.setColor(p.color);
            int s = p.life > 0.25 ? 2 : 1;
            g.fillRect(sx(p.x), sy(p.y), s, s);
        }
        drawHud(g, world);
    }

    /** Pre-renders the floor and bevelled walls once. */
    static BufferedImage drawArena(Arena arena, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        double s = width / arena.width();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        g.setColor(FLOOR_DOT);
        for (int r = 0; r <= arena.rows; r++) {
            for (int c = 0; c <= arena.cols; c++) g.fillRect((int) (c * Arena.CELL * s), (int) (r * Arena.CELL * s), 1, 1);
        }
        for (int r = 0; r < arena.rows; r++) {
            for (int c = 0; c < arena.cols; c++) {
                if (!arena.solid(c, r)) continue;
                int x0 = (int) Math.round(c * Arena.CELL * s), y0 = (int) Math.round(r * Arena.CELL * s);
                int x1 = (int) Math.round((c + 1) * Arena.CELL * s) - 1, y1 = (int) Math.round((r + 1) * Arena.CELL * s) - 1;
                g.setColor(WALL);
                g.fillRect(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
                g.setColor(WALL_TEXTURE);
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if ((x + y * 3) % 6 == 0) g.fillRect(x, y, 1, 1);
                g.setColor(WALL_LIGHT);
                if (!arena.solid(c, r - 1)) g.fillRect(x0, y0, x1 - x0 + 1, 1);
                if (!arena.solid(c - 1, r)) g.fillRect(x0, y0, 1, y1 - y0 + 1);
                g.setColor(WALL_DARK);
                if (!arena.solid(c, r + 1)) g.fillRect(x0, y1, x1 - x0 + 1, 1);
                if (!arena.solid(c + 1, r)) g.fillRect(x1, y0, 1, y1 - y0 + 1);
            }
        }
        g.dispose();
        return img;
    }

    private void drawBeams(Graphics2D g, GameWorld world) {
        BasicStroke wide = new BasicStroke(3), thin = new BasicStroke(1);
        for (Beam b : world.beams) {
            Player owner = world.players.get(b.owner);
            Color c = owner != null ? Rules.COLORS[owner.color] : Color.WHITE;
            boolean fresh = b.life > Rules.BEAM_LIFE * 0.45;
            for (int i = 1; i < b.points.size(); i++) {
                double[] a = b.points.get(i - 1), z = b.points.get(i);
                if (fresh) {
                    g.setStroke(wide);
                    g.setColor(c);
                    g.drawLine(sx(a[0]), sy(a[1]), sx(z[0]), sy(z[1]));
                }
                g.setStroke(thin);
                g.setColor(fresh ? Color.WHITE : c);
                g.drawLine(sx(a[0]), sy(a[1]), sx(z[0]), sy(z[1]));
            }
        }
        g.setStroke(thin);
    }

    private void drawPlayer(Graphics2D g, Player p) {
        Color c = Rules.COLORS[p.color];
        int x = sx(p.rx), y = sy(p.ry);
        boolean local = isLocal(p.id);

        if (p.tagged) {
            if ((int) (clock * 8) % 2 == 0) drawSprite(g, x, y, c, null);
            PixelFont.drawCentered(g, Integer.toString((int) Math.ceil(Math.max(0, p.respawnTimer))), x, y - 3, 1, c);
            return;
        }

        double dx = Math.cos(p.ra), dy = Math.sin(p.ra);
        g.setStroke(new BasicStroke(2));
        g.setColor(Color.WHITE);
        g.drawLine(x, y, (int) Math.round(x + dx * 9), (int) Math.round(y + dy * 9));
        g.setStroke(new BasicStroke(1));
        drawSprite(g, x, y, c, c.darker().darker());
        g.setColor(Color.WHITE);
        g.fillRect(x - 1, y - 1, 2, 2);

        if (p.shield > 0) {
            int segs = 12, phase = (int) (clock * 20);
            g.setColor(Color.WHITE);
            for (int i = 0; i < segs; i++) {
                if ((i + phase) % 2 == 0) continue;
                double a = i * Math.PI * 2 / segs;
                g.fillRect(x + (int) Math.round(Math.cos(a) * 8), y + (int) Math.round(Math.sin(a) * 8), 1, 1);
            }
        }

        String label = local ? localLabel(p.id) : shortName(p.name, 6);
        PixelFont.drawCentered(g, label, x, y - 15, 1, local ? Color.WHITE : c);

        if (local) {
            int pips = (int) (Rules.MAX_ENERGY / Rules.SHOT_COST);
            for (int i = 0; i < pips; i++) {
                boolean full = p.energy >= (i + 1) * Rules.SHOT_COST;
                g.setColor(full ? c : WALL);
                g.fillRect(x - pips * 2 + i * 4, y + 8, 3, 2);
            }
        }
    }

    private String localLabel(int id) {
        if (session.localCount() == 1) return "YOU";
        return session.localPlayerId(0) == id ? "P1" : "P2";
    }

    private static void drawSprite(Graphics2D g, int cx, int cy, Color outline, Color body) {
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                int v = SPRITE[y][x];
                if (v == 0 || (v == 2 && body == null)) continue;
                g.setColor(v == 1 ? outline : body);
                g.fillRect(cx - 5 + x, cy - 5 + y, 1, 1);
            }
        }
    }

    private static int[][] buildSprite() {
        int[][] m = new int[11][11];
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                double d = Math.hypot(x - 5, y - 5);
                m[y][x] = d <= 3.6 ? 2 : d <= 5.4 ? 1 : 0;
            }
        }
        return m;
    }

    static String shortName(String name, int max) {
        return name.length() > max ? name.substring(0, max) : name;
    }

    private void drawHud(Graphics2D g, GameWorld world) {
        // Top bar.
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, TOP);
        PixelFont.draw(g, "ROUND " + world.round, 6, 7, 1, GREY);
        String goal = "FIRST TO " + Rules.SCORE_LIMIT;
        PixelFont.draw(g, goal, W - 6 - PixelFont.width(goal, 1), 7, 1, GREY);
        int secs = (int) Math.ceil(world.timeLeft);
        boolean hurry = world.timeLeft < 20 && !world.roundOver;
        Color clockColor = hurry && (int) (clock * 4) % 2 == 0 ? RED : Color.WHITE;
        PixelFont.drawCentered(g, String.format("%d:%02d", secs / 60, secs % 60), W / 2, 3, 2, clockColor);

        drawFeed(g, world);
        drawScoreStrip(g, world);

        // Connection info sits inside the bottom border wall.
        String status = session.status();
        if (status != null) PixelFont.draw(g, status, 6, H - BOTTOM - 12, 1, GREY);

        drawLocalMessages(g, world);
        if (clock < 7 && !world.roundOver) {
            String hint = controls == Controls.MOUSE ? "WASD MOVE  MOUSE AIM  CLICK FIRE" : "P1 WASD+SPACE   P2 ARROWS+ENTER";
            PixelFont.drawCentered(g, hint, W / 2, TOP + 228, 1, HINT_COLOR);
            PixelFont.drawCentered(g, "HOLD TAB: SCORES + INFO   ESC: MENU", W / 2, TOP + 240, 1, HINT_COLOR);
        }

        if (world.roundOver) drawRoundOver(g, world);
        else if (keys.contains(KeyEvent.VK_TAB)) drawInfo(g, world);
        if (session.paused()) drawBanner(g, "PAUSED", "PRESS P TO RESUME", "M SOUND  C CRT  ESC MENU");
        if (session.error() != null) drawBanner(g, "DISCONNECTED", session.error(), "PRESS ESC");
    }

    /** The latest event scrolls along the top border wall like an arcade ticker. */
    private void drawFeed(Graphics2D g, GameWorld world) {
        if (world.feed.isEmpty()) return;
        GameWorld.FeedItem f = world.feed.get(world.feed.size() - 1);
        boolean fresh = f.ttl > 4.5 && (int) (clock * 10) % 2 == 0;
        PixelFont.drawCentered(g, f.text, W / 2, TOP + 5, 1, fresh ? Rules.COLORS[2] : Color.WHITE);
    }

    private void drawScoreStrip(Graphics2D g, GameWorld world) {
        int y0 = H - BOTTOM;
        g.setColor(Color.BLACK);
        g.fillRect(0, y0, W, BOTTOM);
        List<Player> ranking = world.ranking();
        if (ranking.isEmpty()) return;
        int slot = W / Math.max(4, ranking.size());
        int nameChars = (slot - 24) / PixelFont.ADVANCE;
        for (int i = 0; i < ranking.size(); i++) {
            Player p = ranking.get(i);
            Color c = Rules.COLORS[p.color];
            int x = i * slot + 6;
            g.setColor(c);
            g.fillRect(x, y0 + 7, 5, 7);
            if (isLocal(p.id) && (int) (clock * 2) % 2 == 0) {
                g.setColor(Color.WHITE);
                g.drawRect(x - 2, y0 + 5, 8, 10);
            }
            PixelFont.draw(g, shortName(p.name, Math.max(2, nameChars - 2)), x + 8, y0 + 7, 1, GREY);
            String score = Integer.toString(p.score);
            PixelFont.draw(g, score, x + slot - 10 - PixelFont.width(score, 1), y0 + 7, 1, c);
        }
    }

    private void drawLocalMessages(Graphics2D g, GameWorld world) {
        if (world.roundOver || session.localCount() != 1) return;
        Player me = world.players.get(session.localPlayerId(0));
        if (me == null || !me.tagged) return;
        PixelFont.drawCentered(g, "TAGGED!", W / 2, TOP + 120, 3, (int) (clock * 6) % 2 == 0 ? RED : Color.WHITE);
        PixelFont.drawCentered(g, String.format("BACK IN %.1f", Math.max(0, me.respawnTimer)), W / 2, TOP + 148, 1, Color.WHITE);
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h, Color border) {
        g.setColor(Color.BLACK);
        g.fillRect(x, y, w, h);
        g.setColor(border);
        g.drawRect(x + 1, y + 1, w - 3, h - 3);
        g.drawRect(x + 3, y + 3, w - 7, h - 7);
    }

    private void drawRanking(Graphics2D g, List<Player> ranking, int x, int y, int w) {
        PixelFont.draw(g, "#  NAME", x, y, 1, DIM);
        PixelFont.draw(g, "TAGS  HIT", x + w - PixelFont.width("TAGS  HIT", 1), y, 1, DIM);
        y += 12;
        int place = 1;
        for (Player p : ranking) {
            Color c = Rules.COLORS[p.color];
            PixelFont.draw(g, place++ + "  " + p.name + (p.bot ? " (BOT)" : ""), x, y, 1, c);
            String stats = String.format("%4d %4d", p.score, p.timesTagged);
            PixelFont.draw(g, stats, x + w - PixelFont.width(stats, 1), y, 1, isLocal(p.id) ? Color.WHITE : GREY);
            y += 10;
        }
    }

    private void drawRoundOver(Graphics2D g, GameWorld world) {
        List<Player> ranking = world.ranking();
        int w = 300, h = 86 + ranking.size() * 10, x = (W - w) / 2, y = (H - h) / 2;
        Color flash = Rules.COLORS[(int) (clock * 6) % Rules.COLORS.length];
        drawPanel(g, x, y, w, h, flash);
        boolean draw = ranking.size() > 1 && ranking.get(0).score == ranking.get(1).score;
        String title = ranking.isEmpty() || draw ? "DRAW!" : shortName(ranking.get(0).name, 10) + " WINS!";
        Color tc = ranking.isEmpty() || draw ? Color.WHITE : Rules.COLORS[ranking.get(0).color];
        PixelFont.drawCentered(g, title, W / 2, y + 12, 2, tc);
        drawRanking(g, ranking, x + 16, y + 36, w - 32);
        PixelFont.drawCentered(g, "NEXT ROUND IN " + (int) Math.ceil(Math.max(0, world.overTimer)), W / 2, y + h - 18, 1,
                DIM);
    }

    private void drawInfo(Graphics2D g, GameWorld world) {
        List<Player> ranking = world.ranking();
        List<String> details = session.details();
        int w = 400, h = 52 + ranking.size() * 10 + (details.isEmpty() ? 0 : 14 + details.size() * 10);
        int x = (W - w) / 2, y = (H - h) / 2;
        drawPanel(g, x, y, w, h, HINT_COLOR);
        PixelFont.drawCentered(g, "SCOREBOARD", W / 2, y + 10, 1, Color.WHITE);
        drawRanking(g, ranking, x + 16, y + 26, w - 32);
        int ty = y + 42 + ranking.size() * 10;
        for (String line : details) {
            PixelFont.draw(g, line, x + 16, ty, 1, GREY);
            ty += 10;
        }
    }

    private void drawBanner(Graphics2D g, String title, String sub, String sub2) {
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, TOP, W, H - TOP - BOTTOM);
        PixelFont.drawCentered(g, title, W / 2, H / 2 - 30, 3, Color.WHITE);
        String line = sub.length() > 76 ? sub.substring(0, 76) : sub;
        PixelFont.drawCentered(g, line, W / 2, H / 2 + 4, 1, GREY);
        PixelFont.drawCentered(g, sub2, W / 2, H / 2 + 18, 1, HINT_COLOR);
    }
}
