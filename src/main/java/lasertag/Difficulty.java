package lasertag;

enum Difficulty {
    EASY("Easy", 0.70, 0.22, 4),
    NORMAL("Normal", 0.40, 0.10, 7),
    HARD("Hard", 0.20, 0.04, 12);

    final String label;
    /** Seconds a bot must see a target before it starts firing. */
    final double reaction;
    /** Standard deviation of aim error, radians. */
    final double aimError;
    /** Max aim rotation speed, radians per second. */
    final double turnSpeed;

    Difficulty(String label, double reaction, double aimError, double turnSpeed) {
        this.label = label;
        this.reaction = reaction;
        this.aimError = aimError;
        this.turnSpeed = turnSpeed;
    }

    @Override
    public String toString() {
        return label;
    }
}
