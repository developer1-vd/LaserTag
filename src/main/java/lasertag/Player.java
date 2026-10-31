package lasertag;

final class Player {
    final int id;
    String name;
    int color;
    boolean bot;

    double x, y, angle;
    double energy = Rules.MAX_ENERGY;
    double cooldown, respawnTimer, shield;
    boolean tagged;
    int score, timesTagged;

    /** Smoothed position/angle used for drawing (equal to the real values on the host). */
    double rx, ry, ra;

    Player(int id, String name, int color, boolean bot) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.bot = bot;
    }

    void snapRender() {
        rx = x;
        ry = y;
        ra = angle;
    }
}
