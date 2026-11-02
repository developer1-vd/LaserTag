package lasertag;

/** Shared tuning constants. Host and clients must agree on these. */
final class Rules {
    private Rules() {}

    static final String PROTOCOL = "LT1";
    static final int DEFAULT_PORT = 47777;
    static final int DISCOVERY_PORT = 47778;
    static final int MAX_PLAYERS = 8;

    static final double PLAYER_RADIUS = 14;
    static final double PLAYER_SPEED = 210;
    static final double TURN_RATE = 12;

    static final double MAX_ENERGY = 100;
    static final double SHOT_COST = 25;
    static final double ENERGY_REGEN = 30;
    static final double FIRE_COOLDOWN = 0.28;
    static final double LASER_RANGE = 1500;
    static final int LASER_BOUNCES = 2;
    static final double BEAM_LIFE = 0.18;

    static final double TAGGED_TIME = 2.5;
    static final double SPAWN_SHIELD = 1.5;

    static final double ROUND_TIME = 180;
    static final double ROUND_OVER_TIME = 8;
    static final int SCORE_LIMIT = 15;

    /** Player colours, 0xRRGGBB, indexed by {@link Player#color}. */
    static final int[] COLORS = {
        0xFF004D, 0x00E436, 0xFFEC27, 0x29ADFF, 0xFF77A8, 0xFFA300, 0xFFCCAA, 0xA77BFF,
    };

    static final String[] BOT_NAMES = {"Photon", "Prism", "Quark", "Lumen", "Flicker", "Spark", "Neon", "Zappy"};
}
