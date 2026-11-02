package lasertag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a {@link Session} as a 480x360 arcade screen, and adds the effects (sparks, screen shake,
 * sounds) for what happened each frame. Shared by the desktop game and the web version, so it
 * only talks to {@link Screen} and {@link Sfx}.
 */
final class GameRenderer {
    static final int W = 480, H = 360, TOP = 20, BOTTOM = 20;

    static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF;
    static final int FLOOR_DOT = Screen.rgb(0x1D2B53), WALL = Screen.rgb(0x1D2B53);
    static final int WALL_LIGHT = Screen.rgb(0x6A89D8), WALL_DARK = Screen.rgb(0x0B1230), WALL_TEXTURE = Screen.rgb(0x253666);
    static final int DIM = Screen.rgb(0x83769C), GREY = Screen.rgb(0xC2C3C7), RED = Screen.rgb(0xFF004D);
    static final int CYAN = Screen.rgb(0x29ADFF), YELLOW = Screen.rgb(0xFFEC27);

    /** 11x11 circular sprite mask: 0 empty, 1 outline, 2 body. */
    private static final int[][] SPRITE = buildSprite();

    private static final class Particle {
        double x, y, vx, vy, life;
        int color;
    }

    private final Session session;
    private final Sfx sfx;
    private final double ws;

    private double clock, shake;
    private int shakeX, shakeY;
    private final List<Particle> particles = new ArrayList<>();
    private final Set<Integer> seenBeams = new HashSet<>();
    private final Map<Integer, Boolean> wasTagged = new HashMap<>();
    private boolean wasRoundOver;

    GameRenderer(Session session, Sfx sfx) {
        this.session = session;
        this.sfx = sfx;
        this.ws = W / session.world().arena.width();
    }

    static int playerColor(int index) {
        return Screen.rgb(Rules.COLORS[index]);
    }

    // ---------------------------------------------------------------- per frame

    /** Advances effects. Call once per frame after the session updated. */
    void tick(double dt) {
        clock += dt;
        if (session.error() == null) detectEvents();
        particles.removeIf(p -> {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 1 - 3 * dt;
            p.vy *= 1 - 3 * dt;
            return (p.life -= dt) <= 0;
        });
        shake = Math.max(0, shake - dt);
        shakeX = shake > 0 ? (int) Math.round((Math.random() - 0.5) * 4) : 0;
        shakeY = shake > 0 ? (int) Math.round((Math.random() - 0.5) * 4) : 0;
    }

    int shakeX() {
        return shakeX;
    }

    int shakeY() {
        return shakeY;
    }

    double clock() {
        return clock;
    }

    /** World coordinates of a point on the 480x360 screen. */
    double[] toWorld(double sx, double sy) {
        return new double[] {sx / ws, (sy - TOP) / ws};
    }

    /** Screen coordinates of a point in the world. */
    int[] toScreen(double x, double y) {
        return new int[] {sx(x), sy(y)};
    }

    private void detectEvents() {
        GameWorld world = session.world();
        Set<Integer> current = new HashSet<>();
        for (Beam b : world.beams) {
            current.add(b.id);
            if (seenBeams.contains(b.id) || b.points.isEmpty()) continue;
            Player owner = world.players.get(b.owner);
            double[] end = b.points.get(b.points.size() - 1);
            burst(end[0], end[1], owner != null ? playerColor(owner.color) : WHITE, 6, 140);
            sfx.play(Sfx.ZAP);
        }
        seenBeams.clear();
        seenBeams.addAll(current);

        for (Player p : world.players.values()) {
            Boolean before = wasTagged.put(p.id, p.tagged);
            if (p.tagged && before != null && !before) {
                burst(p.x, p.y, playerColor(p.color), 24, 260);
                burst(p.x, p.y, WHITE, 8, 160);
                sfx.play(Sfx.TAG);
                if (isLocal(p.id)) shake = 0.3;
            }
        }
        wasTagged.keySet().retainAll(world.players.keySet());

        if (world.roundOver && !wasRoundOver) sfx.play(Sfx.ROUND);
        wasRoundOver = world.roundOver;
    }

    private void burst(double x, double y, int color, int count, double speed) {
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

    boolean isLocal(int id) {
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

    /**
     * Draws a frame.
     *
     * @param showInfo draw the scoreboard/connection overlay (Tab held)
     * @param hints the controls hint shown at the start of a match, two lines
     */
    void render(Screen s, boolean showInfo, String[] hints) {
        GameWorld world = session.world();
        s.fill(0, 0, W, H, BLACK);
        s.cached("arena" + world.arena.index, 0, TOP, W, H - TOP - BOTTOM,
                img -> drawArena(img, world.arena, W, H - TOP - BOTTOM));
        drawBeams(s, world);
        for (Player p : world.players.values()) drawPlayer(s, p);
        for (Particle p : particles) {
            int size = p.life > 0.25 ? 2 : 1;
            s.fill(sx(p.x), sy(p.y), size, size, p.color);
        }
        drawHud(s, world, showInfo, hints);
    }

    /** Draws the floor and bevelled walls; cached by callers since it never changes. */
    static void drawArena(Screen g, Arena arena, int width, int height) {
        double s = width / arena.width();
        g.fill(0, 0, width, height, BLACK);
        for (int r = 0; r <= arena.rows; r++) {
            for (int c = 0; c <= arena.cols; c++) g.fill((int) (c * Arena.CELL * s), (int) (r * Arena.CELL * s), 1, 1, FLOOR_DOT);
        }
        for (int r = 0; r < arena.rows; r++) {
            for (int c = 0; c < arena.cols; c++) {
                if (!arena.solid(c, r)) continue;
                int x0 = (int) Math.round(c * Arena.CELL * s), y0 = (int) Math.round(r * Arena.CELL * s);
                int x1 = (int) Math.round((c + 1) * Arena.CELL * s) - 1, y1 = (int) Math.round((r + 1) * Arena.CELL * s) - 1;
                g.fill(x0, y0, x1 - x0 + 1, y1 - y0 + 1, WALL);
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if ((x + y * 3) % 6 == 0) g.fill(x, y, 1, 1, WALL_TEXTURE);
                if (!arena.solid(c, r - 1)) g.fill(x0, y0, x1 - x0 + 1, 1, WALL_LIGHT);
                if (!arena.solid(c - 1, r)) g.fill(x0, y0, 1, y1 - y0 + 1, WALL_LIGHT);
                if (!arena.solid(c, r + 1)) g.fill(x0, y1, x1 - x0 + 1, 1, WALL_DARK);
                if (!arena.solid(c + 1, r)) g.fill(x1, y0, 1, y1 - y0 + 1, WALL_DARK);
            }
        }
    }

    private void drawBeams(Screen s, GameWorld world) {
        for (Beam b : world.beams) {
            Player owner = world.players.get(b.owner);
            int c = owner != null ? playerColor(owner.color) : WHITE;
            boolean fresh = b.life > Rules.BEAM_LIFE * 0.45;
            for (int i = 1; i < b.points.size(); i++) {
                double[] a = b.points.get(i - 1), z = b.points.get(i);
                if (fresh) s.line(sx(a[0]), sy(a[1]), sx(z[0]), sy(z[1]), 3, c);
                s.line(sx(a[0]), sy(a[1]), sx(z[0]), sy(z[1]), 1, fresh ? WHITE : c);
            }
        }
    }

    private void drawPlayer(Screen s, Player p) {
        int c = playerColor(p.color);
        int x = sx(p.rx), y = sy(p.ry);
        boolean local = isLocal(p.id);

        if (p.tagged) {
            if ((int) (clock * 8) % 2 == 0) drawSprite(s, x, y, c, 0);
            Text.drawCentered(s, Integer.toString((int) Math.ceil(Math.max(0, p.respawnTimer))), x, y - 3, 1, c);
            return;
        }

        double dx = Math.cos(p.ra), dy = Math.sin(p.ra);
        s.line(x, y, (int) Math.round(x + dx * 9), (int) Math.round(y + dy * 9), 2, WHITE);
        drawSprite(s, x, y, c, Screen.darker(Screen.darker(c)));
        s.fill(x - 1, y - 1, 2, 2, WHITE);

        if (p.shield > 0) {
            int segs = 12, phase = (int) (clock * 20);
            for (int i = 0; i < segs; i++) {
                if ((i + phase) % 2 == 0) continue;
                double a = i * Math.PI * 2 / segs;
                s.fill(x + (int) Math.round(Math.cos(a) * 8), y + (int) Math.round(Math.sin(a) * 8), 1, 1, WHITE);
            }
        }

        String label = local ? localLabel(p.id) : shortName(p.name, 6);
        Text.drawCentered(s, label, x, y - 15, 1, local ? WHITE : c);

        if (local) {
            int pips = (int) (Rules.MAX_ENERGY / Rules.SHOT_COST);
            for (int i = 0; i < pips; i++) {
                boolean full = p.energy >= (i + 1) * Rules.SHOT_COST;
                s.fill(x - pips * 2 + i * 4, y + 8, 3, 2, full ? c : WALL);
            }
        }
    }

    private String localLabel(int id) {
        if (session.localCount() == 1) return "YOU";
        return session.localPlayerId(0) == id ? "P1" : "P2";
    }

    /** body == 0 draws only the outline. */
    private static void drawSprite(Screen s, int cx, int cy, int outline, int body) {
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                int v = SPRITE[y][x];
                if (v == 0 || (v == 2 && body == 0)) continue;
                s.fill(cx - 5 + x, cy - 5 + y, 1, 1, v == 1 ? outline : body);
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

    /** "1.4" - one decimal place, without String.format (not available everywhere). */
    static String oneDecimal(double v) {
        int tenths = (int) Math.round(Math.max(0, v) * 10);
        return tenths / 10 + "." + tenths % 10;
    }

    static String clockText(double seconds) {
        int secs = (int) Math.ceil(seconds);
        return secs / 60 + ":" + (secs % 60 < 10 ? "0" : "") + secs % 60;
    }

    private static String padLeft(String s, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) sb.append(' ');
        return sb.append(s).toString();
    }

    private void drawHud(Screen s, GameWorld world, boolean showInfo, String[] hints) {
        // Top bar.
        s.fill(0, 0, W, TOP, BLACK);
        Text.draw(s, "ROUND " + world.round, 6, 7, 1, GREY);
        String goal = "FIRST TO " + Rules.SCORE_LIMIT;
        Text.draw(s, goal, W - 6 - Text.width(goal, 1), 7, 1, GREY);
        boolean hurry = world.timeLeft < 20 && !world.roundOver;
        int clockColor = hurry && (int) (clock * 4) % 2 == 0 ? RED : WHITE;
        Text.drawCentered(s, clockText(world.timeLeft), W / 2, 3, 2, clockColor);

        drawFeed(s, world);
        drawScoreStrip(s, world);

        // Connection info sits inside the bottom border wall.
        String status = session.status();
        if (status != null) Text.draw(s, status, 6, H - BOTTOM - 12, 1, GREY);

        drawLocalMessages(s, world);
        if (clock < 7 && !world.roundOver && hints != null) {
            Text.drawCentered(s, hints[0], W / 2, TOP + 228, 1, CYAN);
            Text.drawCentered(s, hints[1], W / 2, TOP + 240, 1, CYAN);
        }

        if (world.roundOver) drawRoundOver(s, world);
        else if (showInfo) drawInfo(s, world);
        if (session.paused()) drawBanner(s, "PAUSED", "PRESS P TO RESUME", "M SOUND  C CRT  ESC MENU");
        if (session.error() != null) drawBanner(s, "DISCONNECTED", session.error(), "PRESS ESC");
    }

    /** The latest event scrolls along the top border wall like an arcade ticker. */
    private void drawFeed(Screen s, GameWorld world) {
        if (world.feed.isEmpty()) return;
        GameWorld.FeedItem f = world.feed.get(world.feed.size() - 1);
        boolean fresh = f.ttl > 4.5 && (int) (clock * 10) % 2 == 0;
        Text.drawCentered(s, f.text, W / 2, TOP + 5, 1, fresh ? YELLOW : WHITE);
    }

    private void drawScoreStrip(Screen s, GameWorld world) {
        int y0 = H - BOTTOM;
        s.fill(0, y0, W, BOTTOM, BLACK);
        List<Player> ranking = world.ranking();
        if (ranking.isEmpty()) return;
        int slot = W / Math.max(4, ranking.size());
        int nameChars = (slot - 24) / Text.ADVANCE;
        for (int i = 0; i < ranking.size(); i++) {
            Player p = ranking.get(i);
            int c = playerColor(p.color);
            int x = i * slot + 6;
            s.fill(x, y0 + 7, 5, 7, c);
            if (isLocal(p.id) && (int) (clock * 2) % 2 == 0) s.outline(x - 2, y0 + 5, 9, 11, WHITE);
            Text.draw(s, shortName(p.name, Math.max(2, nameChars - 2)), x + 8, y0 + 7, 1, GREY);
            String score = Integer.toString(p.score);
            Text.draw(s, score, x + slot - 10 - Text.width(score, 1), y0 + 7, 1, c);
        }
    }

    private void drawLocalMessages(Screen s, GameWorld world) {
        if (world.roundOver || session.localCount() != 1) return;
        Player me = world.players.get(session.localPlayerId(0));
        if (me == null || !me.tagged) return;
        Text.drawCentered(s, "TAGGED!", W / 2, TOP + 120, 3, (int) (clock * 6) % 2 == 0 ? RED : WHITE);
        Text.drawCentered(s, "BACK IN " + oneDecimal(me.respawnTimer), W / 2, TOP + 148, 1, WHITE);
    }

    static void drawPanel(Screen s, int x, int y, int w, int h, int border) {
        s.fill(x, y, w, h, BLACK);
        s.outline(x + 1, y + 1, w - 2, h - 2, border);
        s.outline(x + 3, y + 3, w - 6, h - 6, border);
    }

    private void drawRanking(Screen s, List<Player> ranking, int x, int y, int w) {
        Text.draw(s, "#  NAME", x, y, 1, DIM);
        Text.draw(s, "TAGS  HIT", x + w - Text.width("TAGS  HIT", 1), y, 1, DIM);
        y += 12;
        int place = 1;
        for (Player p : ranking) {
            Text.draw(s, place++ + "  " + p.name + (p.bot ? " (BOT)" : ""), x, y, 1, playerColor(p.color));
            String stats = padLeft(Integer.toString(p.score), 4) + " " + padLeft(Integer.toString(p.timesTagged), 4);
            Text.draw(s, stats, x + w - Text.width(stats, 1), y, 1, isLocal(p.id) ? WHITE : GREY);
            y += 10;
        }
    }

    private void drawRoundOver(Screen s, GameWorld world) {
        List<Player> ranking = world.ranking();
        int w = 300, h = 86 + ranking.size() * 10, x = (W - w) / 2, y = (H - h) / 2;
        drawPanel(s, x, y, w, h, playerColor((int) (clock * 6) % Rules.COLORS.length));
        boolean draw = ranking.size() > 1 && ranking.get(0).score == ranking.get(1).score;
        String title = ranking.isEmpty() || draw ? "DRAW!" : shortName(ranking.get(0).name, 10) + " WINS!";
        int tc = ranking.isEmpty() || draw ? WHITE : playerColor(ranking.get(0).color);
        Text.drawCentered(s, title, W / 2, y + 12, 2, tc);
        drawRanking(s, ranking, x + 16, y + 36, w - 32);
        Text.drawCentered(s, "NEXT ROUND IN " + (int) Math.ceil(Math.max(0, world.overTimer)), W / 2, y + h - 18, 1, DIM);
    }

    private void drawInfo(Screen s, GameWorld world) {
        List<Player> ranking = world.ranking();
        List<String> details = session.details();
        int w = 400, h = 52 + ranking.size() * 10 + (details.isEmpty() ? 0 : 14 + details.size() * 10);
        int x = (W - w) / 2, y = (H - h) / 2;
        drawPanel(s, x, y, w, h, CYAN);
        Text.drawCentered(s, "SCOREBOARD", W / 2, y + 10, 1, WHITE);
        drawRanking(s, ranking, x + 16, y + 26, w - 32);
        int ty = y + 42 + ranking.size() * 10;
        for (String line : details) {
            Text.draw(s, line, x + 16, ty, 1, GREY);
            ty += 10;
        }
    }

    private void drawBanner(Screen s, String title, String sub, String sub2) {
        s.fill(0, TOP, W, H - TOP - BOTTOM, Screen.alpha(BLACK, 190));
        Text.drawCentered(s, title, W / 2, H / 2 - 30, 3, WHITE);
        String line = sub.length() > 76 ? sub.substring(0, 76) : sub;
        Text.drawCentered(s, line, W / 2, H / 2 + 4, 1, GREY);
        Text.drawCentered(s, sub2, W / 2, H / 2 + 18, 1, CYAN);
    }
}
