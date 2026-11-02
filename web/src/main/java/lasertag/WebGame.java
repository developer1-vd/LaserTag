package lasertag;

import java.util.Arrays;

/**
 * The web version: offline Laser Tag (solo vs bots, or two players on one keyboard) running in
 * the browser. The game rules, bots and drawing are the same Java code as the desktop game,
 * compiled to JavaScript with TeaVM. Online play needs raw network sockets, which browsers
 * don't allow, so it stays in the desktop app.
 */
public final class WebGame {
    static final String DOWNLOAD_URL = "https://github.com/developer1-vd/LaserTag/releases/latest";

    private static final String[] MOUSE_HINTS = {"WASD MOVE  MOUSE AIM  CLICK FIRE", "HOLD TAB: SCORES   ESC: MENU"};
    private static final String[] SPLIT_HINTS = {"P1 WASD+SPACE   P2 ARROWS+ENTER", "HOLD TAB: SCORES   ESC: MENU"};
    private static final String[] TOUCH_HINTS = {"LEFT THUMB: MOVE   RIGHT THUMB: AIM + FIRE", "TAP MENU (TOP LEFT) TO QUIT"};

    static final Sfx SFX = Web::sfx;

    private final WebScreen screen = new WebScreen();
    private final WebMenu menu;
    private OfflineSession session;
    private GameRenderer renderer;
    private boolean split;
    boolean scanlines, muted;

    private WebGame() {
        scanlines = !"0".equals(Web.load("crt"));
        muted = "1".equals(Web.load("muted"));
        Web.scanlines(scanlines);
        Web.setMuted(muted);
        menu = new WebMenu(this);
    }

    public static void main(String[] args) {
        WebGame game = new WebGame();
        Web.start(game::frame);
    }

    void setScanlines(boolean on) {
        scanlines = on;
        Web.scanlines(on);
        Web.save("crt", on ? "1" : "0");
    }

    void setMuted(boolean on) {
        muted = on;
        Web.setMuted(on);
        Web.save("muted", on ? "1" : "0");
    }

    void startGame(int map, int bots, Difficulty difficulty, boolean twoPlayers) {
        split = twoPlayers;
        session = twoPlayers
                ? new OfflineSession(map, Arrays.asList("Player 1", "Player 2"), Math.min(bots, Rules.MAX_PLAYERS - 2), difficulty)
                : new OfflineSession(map, Arrays.asList("You"), Math.max(1, bots), difficulty);
        renderer = new GameRenderer(session, SFX);
    }

    boolean inMenu() {
        return session == null;
    }

    private void toMenu() {
        session = null;
        renderer = null;
        menu.onShow();
    }

    private void frame(double dt) {
        if (session == null) {
            Web.begin(0, 0);
            menu.frame(dt, screen);
            return;
        }
        String key;
        while ((key = Web.nextKey()) != null) {
            switch (key) {
                case "Escape" -> {
                    toMenu();
                    return;
                }
                case "KeyP" -> session.togglePause();
                case "KeyM" -> setMuted(!muted);
                case "KeyC" -> setScanlines(!scanlines);
                default -> { }
            }
        }
        boolean touch = Web.touchMode();
        while (Web.takeClick()) {
            if (!touch) continue;
            if (Web.clickY() < GameRenderer.TOP && Web.clickX() < 60) {
                toMenu();
                return;
            }
            if (session.paused()) session.togglePause();
        }

        readInputs(touch);
        session.update(dt);
        renderer.tick(dt);
        Web.begin(renderer.shakeX(), renderer.shakeY());
        renderer.render(screen, Web.key("Tab"), split ? SPLIT_HINTS : touch ? TOUCH_HINTS : MOUSE_HINTS);
        if (touch) drawTouchControls();
    }

    private void readInputs(boolean touch) {
        if (split) {
            session.setInput(0, new Input(axis("KeyA", "KeyD"), axis("KeyW", "KeyS"), Double.NaN,
                    Web.key("Space") || Web.key("KeyF")));
            session.setInput(1, new Input(axis("ArrowLeft", "ArrowRight"), axis("ArrowUp", "ArrowDown"), Double.NaN,
                    Web.key("Enter") || Web.key("Slash") || Web.key("Numpad0")));
            return;
        }
        Player me = session.world().players.get(session.localPlayerId(0));
        if (touch) {
            double aim = Double.NaN;
            boolean aiming = Web.aimActive();
            if (me != null && aiming) {
                double[] w = renderer.toWorld(Web.aimX(), Web.aimY());
                aim = Math.atan2(w[1] - me.ry, w[0] - me.rx);
            }
            session.setInput(0, new Input(Web.stickX(), Web.stickY(), aim, aiming));
            return;
        }
        double mx = axis("KeyA", "KeyD") + axis("ArrowLeft", "ArrowRight");
        double my = axis("KeyW", "KeyS") + axis("ArrowUp", "ArrowDown");
        double aim = Double.NaN;
        if (me != null && Web.mouseX() >= 0) {
            double[] w = renderer.toWorld(Web.mouseX(), Web.mouseY());
            aim = Math.atan2(w[1] - me.ry, w[0] - me.rx);
        }
        boolean fire = Web.mouseDown() || Web.key("Space");
        session.setInput(0, new Input(clamp(mx), clamp(my), aim, fire));
    }

    private static double axis(String negative, String positive) {
        return (Web.key(positive) ? 1 : 0) - (Web.key(negative) ? 1 : 0);
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    /** A MENU button, the virtual stick under the left thumb and a target under the right. */
    private void drawTouchControls() {
        screen.fill(0, 0, 60, GameRenderer.TOP, GameRenderer.BLACK);
        Text.draw(screen, "MENU", 6, 7, 1, GameRenderer.YELLOW);
        if (Web.stickActive()) {
            int ox = (int) Web.stickOriginX(), oy = (int) Web.stickOriginY();
            ring(ox, oy, 24, Screen.alpha(GameRenderer.WHITE, 140));
            int kx = ox + (int) Math.round(Web.stickX() * 24), ky = oy + (int) Math.round(Web.stickY() * 24);
            screen.fill(kx - 4, ky - 4, 9, 9, Screen.alpha(GameRenderer.WHITE, 160));
        }
        if (Web.aimActive()) {
            int ax = (int) Web.aimX(), ay = (int) Web.aimY();
            ring(ax, ay, 7, GameRenderer.RED);
            screen.fill(ax, ay, 1, 1, GameRenderer.RED);
        }
    }

    private void ring(int cx, int cy, int r, int argb) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            screen.fill(cx + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r), 1, 1, argb);
        }
    }
}
