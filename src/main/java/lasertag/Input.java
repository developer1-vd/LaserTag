package lasertag;

/** One frame of player intent. {@code aim} is an absolute angle, or NaN to face the movement direction. */
final class Input {
    static final Input NONE = new Input(0, 0, Double.NaN, false);

    final double mx, my, aim;
    final boolean fire;

    Input(double mx, double my, double aim, boolean fire) {
        this.mx = mx;
        this.my = my;
        this.aim = aim;
        this.fire = fire;
    }

    String encode() {
        return "I " + Math.round(mx * 100) + " " + Math.round(my * 100) + " "
                + (Double.isNaN(aim) ? "N" : Long.toString(Math.round(aim * 1000))) + " " + (fire ? 1 : 0);
    }

    /** Parses an "I ..." line; returns NONE if it is malformed. */
    static Input decode(String line) {
        try {
            String[] t = line.split(" ");
            double mx = clamp(Integer.parseInt(t[1]) / 100.0);
            double my = clamp(Integer.parseInt(t[2]) / 100.0);
            double aim = t[3].equals("N") ? Double.NaN : Integer.parseInt(t[3]) / 1000.0;
            return new Input(mx, my, aim, t[4].equals("1"));
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }
}
