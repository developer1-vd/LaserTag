package lasertag;

/** A running match as seen by the screen: either simulated here or mirrored from a host. */
interface Session {
    GameWorld world();

    /** Number of humans playing on this computer (1, or 2 for split keyboard). */
    int localCount();

    /** Player id for a local slot, or -1 if not (yet) in the match. */
    int localPlayerId(int slot);

    void setInput(int slot, Input input);

    void update(double dt);

    default boolean canPause() {
        return false;
    }

    default void togglePause() {}

    default boolean paused() {
        return false;
    }

    /** Informational line for the HUD (e.g. the address to join), or null. */
    String status();

    /** Non-null once the session has failed (e.g. lost connection). */
    String error();

    void close();
}
