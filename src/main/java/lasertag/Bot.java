package lasertag;

import java.util.List;
import java.util.Random;

/** Computer-controlled opponent. Produces the same {@link Input} a human would. */
final class Bot {
    final int playerId;
    private final Difficulty difficulty;
    private final Random rng = new Random();

    private int targetId = -1;
    private double seenTime;
    private double aimAngle;
    private double aimError, aimErrorTimer;
    private double strafeDir = 1, strafeTimer;

    private List<double[]> path;
    private double repathTimer;
    private double[] lastSeen;
    private double stuckTimer, lastX, lastY;

    Bot(int playerId, Difficulty difficulty) {
        this.playerId = playerId;
        this.difficulty = difficulty;
    }

    Input think(GameWorld world, double dt) {
        Player me = world.players.get(playerId);
        if (me == null || me.tagged || world.roundOver) {
            targetId = -1;
            path = null;
            if (me != null) aimAngle = me.angle;
            return Input.NONE;
        }

        boolean stuck = isStuck(me, dt);
        Player target = findTarget(world, me);
        if (target != null) return engage(me, target, dt, stuck);

        targetId = -1;
        seenTime = 0;
        return roam(world, me, dt, stuck);
    }

    private Player findTarget(GameWorld world, Player me) {
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        for (Player p : world.players.values()) {
            if (p == me || p.tagged) continue;
            double d = Math.hypot(p.x - me.x, p.y - me.y);
            if (d > 900 || !world.arena.lineOfSight(me.x, me.y, p.x, p.y)) continue;
            double score = d + (p.shield > 0 ? 400 : 0) + (p.id == targetId ? -150 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private Input engage(Player me, Player target, double dt, boolean stuck) {
        if (target.id != targetId) {
            targetId = target.id;
            seenTime = 0;
        }
        seenTime += dt;
        lastSeen = new double[] {target.x, target.y};
        path = null;

        double dx = target.x - me.x, dy = target.y - me.y, d = Math.max(1, Math.hypot(dx, dy));
        double desired = Math.atan2(dy, dx);
        if ((aimErrorTimer -= dt) <= 0) {
            aimError = rng.nextGaussian() * difficulty.aimError;
            aimErrorTimer = 0.4 + rng.nextDouble() * 0.5;
        }
        aimAngle = GameWorld.turnToward(aimAngle, desired + aimError, difficulty.turnSpeed * dt);
        boolean onTarget = Math.abs(GameWorld.angleDiff(aimAngle, desired)) < 0.08 + difficulty.aimError;
        boolean fire = seenTime > difficulty.reaction && onTarget && target.shield <= 0
                && me.energy >= Rules.SHOT_COST;

        // Circle-strafe around the target, keeping a comfortable distance.
        if ((strafeTimer -= dt) <= 0) {
            strafeDir = -strafeDir;
            strafeTimer = 0.6 + rng.nextDouble();
        }
        double fx = dx / d, fy = dy / d;
        double approach = d > 380 ? 0.7 : d < 200 ? -0.7 : 0;
        double mx = -fy * strafeDir * 0.8 + fx * approach;
        double my = fx * strafeDir * 0.8 + fy * approach;
        if (stuck) strafeDir = -strafeDir;
        return new Input(mx, my, aimAngle, fire);
    }

    private Input roam(GameWorld world, Player me, double dt, boolean stuck) {
        repathTimer -= dt;
        if (path == null || path.isEmpty() || repathTimer <= 0 || stuck) {
            double[] goal = pickGoal(world, me);
            path = world.arena.findPath(me.x, me.y, goal[0], goal[1]);
            repathTimer = 1.5 + rng.nextDouble();
        }
        if (path == null || path.isEmpty()) {
            aimAngle = me.angle;
            return Input.NONE;
        }
        double[] next = path.get(0);
        double dx = next[0] - me.x, dy = next[1] - me.y, d = Math.hypot(dx, dy);
        if (d < 10) {
            path.remove(0);
            if (path.isEmpty()) lastSeen = null;
        }
        if (d < 1e-6) return new Input(0, 0, aimAngle, false);
        aimAngle = GameWorld.turnToward(aimAngle, Math.atan2(dy, dx), difficulty.turnSpeed * dt);
        return new Input(dx / d, dy / d, aimAngle, false);
    }

    private double[] pickGoal(GameWorld world, Player me) {
        if (lastSeen != null) return lastSeen;
        if (rng.nextDouble() < 0.3) return world.arena.randomOpenCell(rng);
        // Hunt the nearest active opponent.
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : world.players.values()) {
            if (p == me || p.tagged) continue;
            double d = Math.hypot(p.x - me.x, p.y - me.y);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best != null ? new double[] {best.x, best.y} : world.arena.randomOpenCell(rng);
    }

    private boolean isStuck(Player me, double dt) {
        double moved = Math.hypot(me.x - lastX, me.y - lastY);
        lastX = me.x;
        lastY = me.y;
        stuckTimer = moved < Rules.PLAYER_SPEED * dt * 0.2 ? stuckTimer + dt : 0;
        if (stuckTimer > 0.5) {
            stuckTimer = 0;
            lastSeen = null;
            return true;
        }
        return false;
    }
}
