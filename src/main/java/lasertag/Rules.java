package lasertag;

import java.awt.Color;

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

    static final Color[] COLORS = {
        new Color(0xFF3B5C), new Color(0x3BFF7A), new Color(0xFFD23B), new Color(0x3BB6FF),
        new Color(0xFF3BF2), new Color(0xFF8C3B), new Color(0xB07BFF), new Color(0x3BFFE8),
    };

    static final String[] BOT_NAMES = {"Photon", "Prism", "Quark", "Lumen", "Flicker", "Spark", "Neon", "Zappy"};
}
