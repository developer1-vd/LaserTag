package lasertag;

import java.util.ArrayList;
import java.util.List;

/** The web version's title screen, drawn in the same style as the desktop menu. */
final class WebMenu {
    private static final int W = GameRenderer.W;
    private static final int LINE = 12, LEFT = 28, VALUE_X = 138;
    private static final int GREY = GameRenderer.GREY, DIM = GameRenderer.DIM, YELLOW = GameRenderer.YELLOW;
    private static final int CYAN = GameRenderer.CYAN, WHITE = GameRenderer.WHITE, GREEN = Screen.rgb(0x00E436);
    private static final int NAVY = Screen.rgb(0x1D2B53), PURPLE = Screen.rgb(0x7E2553);

    private interface Change {
        void by(int delta);
    }

    private static final class Item {
        final String label, help;
        final int color;
        Runnable action;
        Change change;
        java.util.function.Supplier<String> value;
        int x0, y0, x1, y1;

        Item(String label, String help, int color) {
            this.label = label;
            this.help = help;
            this.color = color;
        }
    }

    private final WebGame game;
    private final List<Item> items = new ArrayList<>();
    private int selected, map, bots;
    private Difficulty difficulty;
    private double clock;
    private double lastMouseX = -1, lastMouseY = -1;

    WebMenu(WebGame game) {
        this.game = game;
        map = Math.floorMod(Web.loadInt("map", 0), Arena.count());
        bots = Math.max(0, Math.min(Rules.MAX_PLAYERS - 1, Web.loadInt("bots", 3)));
        difficulty = Difficulty.values()[Math.floorMod(Web.loadInt("difficulty", 1), Difficulty.values().length)];

        boolean touch = Web.coarsePointer();
        action("SOLO VS BOTS", touch ? "LEFT THUMB MOVES, RIGHT THUMB AIMS AND FIRES."
                : "YOU AGAINST BOTS. WASD MOVE, MOUSE AIM, CLICK FIRE.", GREEN, () -> play(false));
        action("2 PLAYERS 1 KEYBOARD", "P1: WASD + SPACE   P2: ARROWS + ENTER   (PLUS BOTS)", GREEN, () -> play(true));
        action("PLAY ONLINE", "ONLINE PLAY NEEDS THE FREE DESKTOP APP - OPENS THE DOWNLOAD PAGE.", CYAN,
                () -> Web.open(WebGame.DOWNLOAD_URL));
        choice("ARENA", "PICK AN ARENA WITH LEFT / RIGHT.", () -> "< " + Arena.NAMES[map] + " >",
                d -> map = Math.floorMod(map + d, Arena.count()));
        choice("BOTS", "HOW MANY BOTS (SOLO ALWAYS HAS AT LEAST 1).", () -> "< " + bots + " >",
                d -> bots = Math.max(0, Math.min(Rules.MAX_PLAYERS - 1, bots + d)));
        choice("BOT SKILL", "HOW SHARP THE BOTS ARE.", () -> "< " + difficulty.label + " >",
                d -> difficulty = Difficulty.values()[Math.floorMod(difficulty.ordinal() + d, Difficulty.values().length)]);
        choice("SOUND", "SOUND EFFECTS ON OR OFF (ALSO M IN GAME).", () -> "< " + (game.muted ? "OFF" : "ON") + " >",
                d -> game.setMuted(!game.muted));
        choice("CRT", "SCANLINES ON OR OFF (ALSO C IN GAME).", () -> "< " + (game.scanlines ? "ON" : "OFF") + " >",
                d -> game.setScanlines(!game.scanlines));
    }

    private void action(String label, String help, int color, Runnable run) {
        Item i = new Item(label, help, color);
        i.action = run;
        items.add(i);
    }

    private void choice(String label, String help, java.util.function.Supplier<String> value, Change change) {
        Item i = new Item(label, help, GREY);
        i.value = value;
        i.change = change;
        items.add(i);
    }

    /** Called when coming back from a game: only real mouse movement should change the selection. */
    void onShow() {
        lastMouseX = Web.mouseX();
        lastMouseY = Web.mouseY();
    }

    private void play(boolean twoPlayers) {
        Web.save("map", Integer.toString(map));
        Web.save("bots", Integer.toString(bots));
        Web.save("difficulty", Integer.toString(difficulty.ordinal()));
        game.startGame(map, bots, difficulty, twoPlayers);
    }

    // ---------------------------------------------------------------- input

    private void handleInput() {
        String key;
        while ((key = Web.nextKey()) != null) {
            Item item = items.get(selected);
            switch (key) {
                case "ArrowUp", "KeyW" -> move(-1);
                case "ArrowDown", "KeyS", "Tab" -> move(1);
                case "ArrowLeft", "KeyA" -> change(item, -1);
                case "ArrowRight", "KeyD" -> change(item, 1);
                case "Enter", "Space" -> {
                    if (item.action != null) {
                        activate(item);
                        return;
                    }
                    change(item, 1);
                }
                case "KeyM" -> game.setMuted(!game.muted);
                case "KeyC" -> game.setScanlines(!game.scanlines);
                default -> { }
            }
        }
        double mx = Web.mouseX(), my = Web.mouseY();
        if (mx != lastMouseX || my != lastMouseY) {
            lastMouseX = mx;
            lastMouseY = my;
            int i = itemAt(mx, my);
            if (i >= 0 && i != selected) {
                selected = i;
                Web.sfx(Sfx.BLIP);
            }
        }
        while (Web.takeClick()) {
            int i = itemAt(Web.clickX(), Web.clickY());
            if (i < 0) continue;
            selected = i;
            Item item = items.get(i);
            if (item.action != null) {
                activate(item);
                return;
            }
            change(item, Web.clickX() < VALUE_X + 30 ? -1 : 1);
        }
    }

    private void move(int d) {
        selected = Math.floorMod(selected + d, items.size());
        Web.sfx(Sfx.BLIP);
    }

    private void change(Item item, int d) {
        if (item.change == null) return;
        item.change.by(d);
        Web.sfx(Sfx.BLIP);
    }

    private void activate(Item item) {
        Web.sfx(Sfx.SELECT);
        item.action.run();
    }

    private int itemAt(double x, double y) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (x >= it.x0 && x < it.x1 && y >= it.y0 && y < it.y1) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- drawing

    void frame(double dt, Screen s) {
        clock += dt;
        handleInput();
        if (!game.inMenu()) return;

        drawBackground(s);
        drawTitle(s);
        int y = 80;
        for (int i = 0; i < items.size(); i++) {
            if (i == 3) {
                y += 6;
                Text.draw(s, "- SETTINGS -", LEFT, y, 1, DIM);
                y += LINE + 2;
            }
            drawItem(s, items.get(i), i == selected, y);
            y += LINE;
        }

        int px = 310, py = 80;
        Text.draw(s, Arena.NAMES[map].toUpperCase(), px, py, 1, DIM);
        s.outline(px - 2, py + 10, 154, 104, CYAN);
        int shown = map;
        s.cached("preview" + shown, px, py + 12, 150, 100, img -> GameRenderer.drawArena(img, new Arena(shown), 150, 100));
        Text.draw(s, "ONLINE PLAY:", px, py + 126, 1, DIM);
        Text.draw(s, "DESKTOP APP", px, py + 140, 1, GREY);
        Text.draw(s, "(WINDOWS/MAC/LINUX)", px, py + 152, 1, GREY);

        s.fill(0, 314, W, 1, NAVY);
        Text.drawCentered(s, items.get(selected).help, W / 2, 320, 1, GREY);
        String footer = Web.touchMode() ? "TAP TO CHOOSE   TAP < OR > SIDE TO CHANGE" : "ARROWS: CHOOSE   ENTER: GO   LEFT/RIGHT: CHANGE";
        Text.drawCentered(s, footer, W / 2, 347, 1, DIM);
    }

    private void drawItem(Screen s, Item item, boolean sel, int y) {
        item.x0 = LEFT - 14;
        item.y0 = y - 2;
        item.x1 = LEFT + 256;
        item.y1 = y - 2 + LINE;
        if (sel) Text.draw(s, ">", LEFT - 10 + ((int) (clock * 4) % 2), y, 1, YELLOW);
        Text.draw(s, item.label, LEFT, y, 1, sel ? YELLOW : item.color);
        if (item.value != null) Text.draw(s, item.value.get().toUpperCase(), VALUE_X, y, 1, WHITE);
    }

    /** An 80s perspective grid scrolling toward the viewer behind the title. */
    private void drawBackground(Screen s) {
        int horizon = 8, bottom = 68;
        for (int i = 0; i < 8; i++) {
            double t = (i + (clock * 0.8) % 1) / 8;
            s.fill(0, horizon + (int) (t * t * (bottom - horizon)), W, 1, NAVY);
        }
        for (int x = -12; x <= 12; x++) s.line(W / 2 + x * 10, horizon, W / 2 + x * 48, bottom, 1, NAVY);
        s.fill(0, bottom, W, 1, PURPLE);
    }

    private void drawTitle(Screen s) {
        String title = "LASER TAG";
        int scale = 5, x = (W - Text.width(title, scale)) / 2, y = 18;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int bounce = (int) Math.round(Math.sin(clock * 4 + i * 0.6) * 2);
            int cx = x + i * Text.ADVANCE * scale;
            Text.draw(s, ch, cx + 3, y + 3 + bounce, scale, GameRenderer.BLACK);
            Text.draw(s, ch, cx, y + bounce, scale, GameRenderer.playerColor((i + (int) (clock * 3)) % Rules.COLORS.length));
        }
    }
}
