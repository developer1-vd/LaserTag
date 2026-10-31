package lasertag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A tile-based arena. Each map is authored as its top-left quadrant and mirrored both ways,
 * so every map is symmetric and fair. '#' is a wall, 'S' a spawn point, '.' open floor.
 */
final class Arena {
    static final int CELL = 40;

    static final String[] NAMES = {"Crossfire", "Bunkers", "Corridors"};

    private static final String[][] QUADRANTS = {
        {
            "###############",
            "#S.............",
            "#..............",
            "#...####.......",
            "#...#..........",
            "#...#......#...",
            "#..........#...",
            "#......###.....",
            "#S.............",
            "#...#.......###",
        },
        {
            "###############",
            "#S.............",
            "#..##....##....",
            "#..##....##....",
            "#..............",
            "#.......#......",
            "#.##....#...##.",
            "#.##....#...##.",
            "#..............",
            "#S.....##......",
        },
        {
            "###############",
            "#S...#.........",
            "#....#..####...",
            "#....#.....#...",
            "#..........#...",
            "####...#.......",
            "#......#...#...",
            "#..##..#...#..#",
            "#..#.......#...",
            "#S.#...........",
        },
    };

    final int index;
    final String name;
    final int cols, rows;
    private final boolean[][] solid;
    final List<double[]> spawns = new ArrayList<>();

    Arena(int index) {
        this.index = Math.floorMod(index, QUADRANTS.length);
        this.name = NAMES[this.index];
        String[] q = QUADRANTS[this.index];
        int qr = q.length, qc = q[0].length();
        rows = qr * 2;
        cols = qc * 2;
        solid = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = q[r < qr ? r : rows - 1 - r].charAt(c < qc ? c : cols - 1 - c);
                solid[r][c] = ch == '#';
                if (ch == 'S') spawns.add(new double[] {(c + 0.5) * CELL, (r + 0.5) * CELL});
            }
        }
    }

    static int count() {
        return QUADRANTS.length;
    }

    double width() {
        return cols * CELL;
    }

    double height() {
        return rows * CELL;
    }

    boolean solid(int c, int r) {
        return c < 0 || r < 0 || c >= cols || r >= rows || solid[r][c];
    }

    /**
     * Walks a ray through the grid (DDA). Returns {distance, axis}: axis 0 means it hit a wall face
     * with a horizontal normal, 1 a vertical normal, and -1 means nothing was hit within maxDist.
     */
    double[] castRay(double ox, double oy, double dx, double dy, double maxDist) {
        int cx = (int) Math.floor(ox / CELL), cy = (int) Math.floor(oy / CELL);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1;
        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY
                : (dx > 0 ? (cx + 1) * CELL - ox : ox - cx * CELL) / Math.abs(dx);
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY
                : (dy > 0 ? (cy + 1) * CELL - oy : oy - cy * CELL) / Math.abs(dy);
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dy);
        while (true) {
            double t;
            int axis;
            if (tMaxX < tMaxY) {
                t = tMaxX;
                tMaxX += tDeltaX;
                cx += stepX;
                axis = 0;
            } else {
                t = tMaxY;
                tMaxY += tDeltaY;
                cy += stepY;
                axis = 1;
            }
            if (t > maxDist) return new double[] {maxDist, -1};
            if (solid(cx, cy)) return new double[] {t, axis};
        }
    }

    boolean lineOfSight(double x0, double y0, double x1, double y1) {
        double d = Math.hypot(x1 - x0, y1 - y0);
        if (d < 1) return true;
        return castRay(x0, y0, (x1 - x0) / d, (y1 - y0) / d, d)[1] < 0;
    }

    /** Pushes a player's circle out of any wall cells it overlaps. */
    void collide(Player p) {
        double r = Rules.PLAYER_RADIUS;
        for (int pass = 0; pass < 2; pass++) {
            int c0 = (int) Math.floor((p.x - r) / CELL), c1 = (int) Math.floor((p.x + r) / CELL);
            int r0 = (int) Math.floor((p.y - r) / CELL), r1 = (int) Math.floor((p.y + r) / CELL);
            for (int row = r0; row <= r1; row++) {
                for (int col = c0; col <= c1; col++) {
                    if (!solid(col, row)) continue;
                    double x0 = col * CELL, y0 = row * CELL, x1 = x0 + CELL, y1 = y0 + CELL;
                    double nx = Math.max(x0, Math.min(p.x, x1)), ny = Math.max(y0, Math.min(p.y, y1));
                    double dx = p.x - nx, dy = p.y - ny, d2 = dx * dx + dy * dy;
                    if (d2 >= r * r) continue;
                    if (d2 > 1e-9) {
                        double d = Math.sqrt(d2), push = r - d;
                        p.x += dx / d * push;
                        p.y += dy / d * push;
                    } else {
                        // Centre is inside the cell: leave via the nearest face that opens onto floor.
                        double[] dist = {p.x - x0, x1 - p.x, p.y - y0, y1 - p.y};
                        boolean[] open = {!solid(col - 1, row), !solid(col + 1, row), !solid(col, row - 1), !solid(col, row + 1)};
                        int best = -1;
                        for (int i = 0; i < 4; i++) {
                            if (open[i] && (best < 0 || dist[i] < dist[best])) best = i;
                        }
                        if (best < 0) continue;
                        switch (best) {
                            case 0 -> p.x = x0 - r;
                            case 1 -> p.x = x1 + r;
                            case 2 -> p.y = y0 - r;
                            default -> p.y = y1 + r;
                        }
                    }
                }
            }
        }
    }

    /** Breadth-first path over open cells; returns cell centres after the start, or null if unreachable. */
    List<double[]> findPath(double sx, double sy, double gx, double gy) {
        int sc = (int) (sx / CELL), sr = (int) (sy / CELL), gc = (int) (gx / CELL), gr = (int) (gy / CELL);
        if (solid(gc, gr) || solid(sc, sr)) return null;
        int[] prev = new int[rows * cols];
        java.util.Arrays.fill(prev, -2);
        int start = sr * cols + sc, goal = gr * cols + gc;
        prev[start] = -1;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == goal) break;
            int cr = cur / cols, cc = cur % cols;
            for (int[] d : dirs) {
                int nc = cc + d[0], nr = cr + d[1];
                if (solid(nc, nr)) continue;
                int n = nr * cols + nc;
                if (prev[n] != -2) continue;
                prev[n] = cur;
                queue.add(n);
            }
        }
        if (prev[goal] == -2) return null;
        List<double[]> path = new ArrayList<>();
        for (int cur = goal; cur != start; cur = prev[cur]) {
            path.add(new double[] {(cur % cols + 0.5) * CELL, (cur / cols + 0.5) * CELL});
        }
        Collections.reverse(path);
        return path;
    }

    double[] randomOpenCell(Random rng) {
        while (true) {
            int c = rng.nextInt(cols), r = rng.nextInt(rows);
            if (!solid(c, r)) return new double[] {(c + 0.5) * CELL, (r + 0.5) * CELL};
        }
    }
}
