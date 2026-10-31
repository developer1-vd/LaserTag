package lasertag;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The whole match state. Whoever owns the match (offline game or online host) calls
 * {@link #update}; online clients only receive {@link #snapshot()} strings and apply them.
 */
final class GameWorld {
    static final class FeedItem {
        final String text;
        double ttl;

        FeedItem(String text, double ttl) {
            this.text = text;
            this.ttl = ttl;
        }
    }

    final Arena arena;
    final Map<Integer, Player> players = new LinkedHashMap<>();
    final List<Beam> beams = new ArrayList<>();
    final List<FeedItem> feed = new ArrayList<>();
    boolean roundOver;
    double timeLeft = Rules.ROUND_TIME;
    double overTimer;
    int round = 1;

    private int nextPlayerId = 1, nextBeamId = 1;
    private final Random rng = new Random();

    GameWorld(Arena arena) {
        this.arena = arena;
    }

    // ---------------------------------------------------------------- players

    Player addPlayer(String name, boolean bot) {
        if (players.size() >= Rules.MAX_PLAYERS) return null;
        Player p = new Player(nextPlayerId++, uniqueName(sanitizeName(name)), freeColor(), bot);
        players.put(p.id, p);
        spawn(p);
        addFeed(p.name + " joined");
        return p;
    }

    void removePlayer(int id) {
        Player p = players.remove(id);
        if (p != null) addFeed(p.name + " left");
    }

    static String sanitizeName(String name) {
        String s = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").trim();
        if (s.isEmpty()) s = "Player";
        return s.length() > 14 ? s.substring(0, 14) : s;
    }

    private String uniqueName(String base) {
        String name = base;
        for (int n = 2; nameTaken(name); n++) name = base + " " + n;
        return name;
    }

    private boolean nameTaken(String name) {
        for (Player p : players.values()) if (p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    private int freeColor() {
        boolean[] used = new boolean[Rules.COLORS.length];
        for (Player p : players.values()) used[p.color] = true;
        for (int i = 0; i < used.length; i++) if (!used[i]) return i;
        return 0;
    }

    /** Places a player at the spawn point that is furthest from active opponents (with a little randomness). */
    private void spawn(Player p) {
        List<double[]> ranked = new ArrayList<>(arena.spawns);
        Map<double[], Double> safety = new LinkedHashMap<>();
        for (double[] s : ranked) {
            double nearest = 1e9;
            for (Player o : players.values()) {
                if (o == p || o.tagged) continue;
                nearest = Math.min(nearest, Math.hypot(o.x - s[0], o.y - s[1]));
            }
            safety.put(s, nearest);
        }
        ranked.sort((a, b) -> Double.compare(safety.get(b), safety.get(a)));
        double[] s = ranked.get(rng.nextInt(Math.min(3, ranked.size())));
        p.x = s[0];
        p.y = s[1];
        p.angle = Math.atan2(arena.height() / 2 - p.y, arena.width() / 2 - p.x);
        p.energy = Rules.MAX_ENERGY;
        p.cooldown = 0.4;
        p.shield = Rules.SPAWN_SHIELD;
        p.tagged = false;
        p.respawnTimer = 0;
        p.snapRender();
    }

    void addFeed(String text) {
        feed.add(new FeedItem(text, 5));
        while (feed.size() > 6) feed.remove(0);
    }

    // ---------------------------------------------------------------- simulation

    void update(double dt, Map<Integer, Input> inputs) {
        tickEffects(dt);
        if (roundOver) {
            overTimer -= dt;
            if (overTimer <= 0) startNewRound();
            return;
        }
        timeLeft = Math.max(0, timeLeft - dt);
        for (Player p : players.values()) {
            if (p.tagged) {
                p.respawnTimer -= dt;
                if (p.respawnTimer <= 0) spawn(p);
                continue;
            }
            Input in = inputs.getOrDefault(p.id, Input.NONE);
            p.cooldown -= dt;
            p.shield = Math.max(0, p.shield - dt);
            p.energy = Math.min(Rules.MAX_ENERGY, p.energy + Rules.ENERGY_REGEN * dt);

            double mx = in.mx, my = in.my, len = Math.hypot(mx, my);
            if (len > 1) {
                mx /= len;
                my /= len;
            }
            p.x += mx * Rules.PLAYER_SPEED * dt;
            p.y += my * Rules.PLAYER_SPEED * dt;
            arena.collide(p);

            if (!Double.isNaN(in.aim)) p.angle = in.aim;
            else if (len > 0.1) p.angle = turnToward(p.angle, Math.atan2(my, mx), Rules.TURN_RATE * dt);

            if (in.fire && p.cooldown <= 0 && p.energy >= Rules.SHOT_COST) fire(p);
        }
        separatePlayers();
        for (Player p : players.values()) p.snapRender();

        boolean limitReached = players.values().stream().anyMatch(p -> p.score >= Rules.SCORE_LIMIT);
        if (timeLeft <= 0 || limitReached) endRound();
    }

    private void fire(Player p) {
        p.cooldown = Rules.FIRE_COOLDOWN;
        p.energy -= Rules.SHOT_COST;
        p.shield = 0; // shooting drops your spawn protection
        double dx = Math.cos(p.angle), dy = Math.sin(p.angle);
        double x = p.x, y = p.y;
        Beam beam = new Beam(nextBeamId++, p.id);
        double muzzle = Math.min(Rules.PLAYER_RADIUS + 4, arena.castRay(x, y, dx, dy, 40)[0] - 1);
        beam.add(x + dx * muzzle, y + dy * muzzle);

        double remaining = Rules.LASER_RANGE;
        for (int bounce = 0; bounce <= Rules.LASER_BOUNCES; bounce++) {
            double[] wall = arena.castRay(x, y, dx, dy, remaining);
            Player victim = null;
            double victimT = wall[0];
            for (Player o : players.values()) {
                if (o == p || o.tagged) continue;
                double t = rayCircle(x, y, dx, dy, o.x, o.y, Rules.PLAYER_RADIUS);
                if (t >= 0 && t < victimT) {
                    victimT = t;
                    victim = o;
                }
            }
            if (victim != null) {
                beam.add(x + dx * victimT, y + dy * victimT);
                if (victim.shield <= 0) tag(p, victim, bounce > 0);
                break;
            }
            beam.add(x + dx * wall[0], y + dy * wall[0]);
            if (wall[1] < 0) break;
            remaining -= wall[0];
            // Back off a hair so the next ray starts in open space, then reflect.
            x += dx * (wall[0] - 0.01);
            y += dy * (wall[0] - 0.01);
            if (wall[1] == 0) dx = -dx;
            else dy = -dy;
        }
        beams.add(beam);
    }

    private void tag(Player shooter, Player victim, boolean ricochet) {
        victim.tagged = true;
        victim.respawnTimer = Rules.TAGGED_TIME;
        victim.timesTagged++;
        shooter.score++;
        addFeed(shooter.name + " tagged " + victim.name + (ricochet ? " (ricochet!)" : ""));
    }

    /** Distance along a unit ray to a circle, or -1 if it misses. */
    static double rayCircle(double ox, double oy, double dx, double dy, double cx, double cy, double r) {
        double fx = ox - cx, fy = oy - cy;
        double b = fx * dx + fy * dy;
        double c = fx * fx + fy * fy - r * r;
        double disc = b * b - c;
        if (disc < 0) return -1;
        double t = -b - Math.sqrt(disc);
        if (t < 0) t = c <= 0 ? 0 : -1;
        return t;
    }

    private void separatePlayers() {
        List<Player> list = new ArrayList<>(players.values());
        double min = Rules.PLAYER_RADIUS * 2;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Player a = list.get(i), b = list.get(j);
                if (a.tagged || b.tagged) continue;
                double dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy);
                if (d >= min) continue;
                if (d < 1e-6) {
                    dx = 1;
                    dy = 0;
                    d = 1;
                }
                double push = (min - d) / 2;
                a.x -= dx / d * push;
                a.y -= dy / d * push;
                b.x += dx / d * push;
                b.y += dy / d * push;
                arena.collide(a);
                arena.collide(b);
            }
        }
    }

    private void endRound() {
        roundOver = true;
        overTimer = Rules.ROUND_OVER_TIME;
        List<Player> ranking = ranking();
        if (ranking.isEmpty()) return;
        if (ranking.size() > 1 && ranking.get(0).score == ranking.get(1).score) {
            addFeed("Round " + round + " is a draw!");
        } else {
            addFeed(ranking.get(0).name + " wins round " + round + "!");
        }
    }

    private void startNewRound() {
        round++;
        roundOver = false;
        timeLeft = Rules.ROUND_TIME;
        beams.clear();
        for (Player p : players.values()) {
            p.score = 0;
            p.timesTagged = 0;
            p.tagged = false;
        }
        for (Player p : players.values()) spawn(p);
        addFeed("Round " + round + " - go!");
    }

    List<Player> ranking() {
        List<Player> list = new ArrayList<>(players.values());
        list.sort((a, b) -> a.score != b.score ? b.score - a.score : a.timesTagged - b.timesTagged);
        return list;
    }

    private void tickEffects(double dt) {
        beams.removeIf(b -> (b.life -= dt) <= 0);
        for (Iterator<FeedItem> it = feed.iterator(); it.hasNext(); ) {
            if ((it.next().ttl -= dt) <= 0) it.remove();
        }
    }

    /** Client-side per-frame tick: fades effects and eases drawn positions toward the latest snapshot. */
    void clientTick(double dt) {
        tickEffects(dt);
        double k = Math.min(1, dt * 18);
        for (Player p : players.values()) {
            p.rx += (p.x - p.rx) * k;
            p.ry += (p.y - p.ry) * k;
            p.ra = turnToward(p.ra, p.angle, Math.abs(angleDiff(p.angle, p.ra)) * k + 1e-9);
        }
    }

    static double angleDiff(double a, double b) {
        double d = (a - b) % (2 * Math.PI);
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    static double turnToward(double from, double to, double maxStep) {
        double d = angleDiff(to, from);
        return Math.abs(d) <= maxStep ? to : from + Math.signum(d) * maxStep;
    }

    // ---------------------------------------------------------------- network snapshot

    String snapshot() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("S ").append(roundOver ? 1 : 0).append(' ').append(Math.round(timeLeft * 10))
                .append(' ').append(Math.round(overTimer * 10)).append(' ').append(round)
                .append(' ').append(players.size());
        for (Player p : players.values()) {
            sb.append(' ').append(p.id).append(' ').append(p.color).append(' ').append(p.bot ? 1 : 0)
                    .append(' ').append(Math.round(p.x * 10)).append(' ').append(Math.round(p.y * 10))
                    .append(' ').append(Math.round(p.angle * 1000)).append(' ').append(Math.round(p.energy))
                    .append(' ').append(p.tagged ? 1 : 0).append(' ').append(Math.round(p.respawnTimer * 10))
                    .append(' ').append(Math.round(p.shield * 10)).append(' ').append(p.score)
                    .append(' ').append(p.timesTagged).append(' ').append(enc(p.name));
        }
        sb.append(' ').append(beams.size());
        for (Beam b : beams) {
            sb.append(' ').append(b.id).append(' ').append(b.owner).append(' ').append(Math.round(b.life * 1000))
                    .append(' ').append(b.points.size());
            for (double[] pt : b.points) sb.append(' ').append(Math.round(pt[0])).append(' ').append(Math.round(pt[1]));
        }
        sb.append(' ').append(feed.size());
        for (FeedItem f : feed) sb.append(' ').append(Math.round(f.ttl * 10)).append(' ').append(enc(f.text));
        return sb.toString();
    }

    void applySnapshot(String line) {
        Tokens t = new Tokens(line);
        t.next(); // "S"
        roundOver = t.nextInt() == 1;
        timeLeft = t.nextInt() / 10.0;
        overTimer = t.nextInt() / 10.0;
        round = t.nextInt();
        int n = t.nextInt();
        Set<Integer> present = new HashSet<>();
        for (int i = 0; i < n; i++) {
            int id = t.nextInt();
            present.add(id);
            Player p = players.get(id);
            boolean fresh = p == null;
            if (fresh) {
                p = new Player(id, "", 0, false);
                players.put(id, p);
            }
            p.color = Math.floorMod(t.nextInt(), Rules.COLORS.length);
            p.bot = t.nextInt() == 1;
            p.x = t.nextInt() / 10.0;
            p.y = t.nextInt() / 10.0;
            p.angle = t.nextInt() / 1000.0;
            p.energy = t.nextInt();
            p.tagged = t.nextInt() == 1;
            p.respawnTimer = t.nextInt() / 10.0;
            p.shield = t.nextInt() / 10.0;
            p.score = t.nextInt();
            p.timesTagged = t.nextInt();
            p.name = dec(t.next());
            if (fresh || Math.hypot(p.x - p.rx, p.y - p.ry) > 120) p.snapRender();
        }
        players.keySet().retainAll(present);

        beams.clear();
        int nb = t.nextInt();
        for (int i = 0; i < nb; i++) {
            Beam b = new Beam(t.nextInt(), t.nextInt());
            b.life = t.nextInt() / 1000.0;
            int np = t.nextInt();
            for (int j = 0; j < np; j++) b.add(t.nextInt(), t.nextInt());
            beams.add(b);
        }

        feed.clear();
        int nf = t.nextInt();
        for (int i = 0; i < nf; i++) {
            double ttl = t.nextInt() / 10.0;
            feed.add(new FeedItem(dec(t.next()), ttl));
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static final class Tokens {
        private final String[] parts;
        private int i;

        Tokens(String line) {
            parts = line.split(" ");
        }

        String next() {
            return parts[i++];
        }

        int nextInt() {
            return Integer.parseInt(parts[i++]);
        }
    }
}
