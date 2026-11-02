package lasertag;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Arcade-style title screen. Arrow keys / mouse to choose, Enter or click to start, type to edit fields. */
final class MenuPanel extends RetroScreen {
    private static final Color GREY = new Color(0xC2C3C7);
    private static final Color DIM = new Color(0x83769C);
    private static final Color CYAN = new Color(0x29ADFF);
    private static final Color YELLOW = new Color(0xFFEC27);
    private static final Color RED = new Color(0xFF004D);
    private static final Color GREEN = new Color(0x00E436);
    private static final int LINE = 12, LEFT = 28, VALUE_X = 138;

    private enum Kind { ACTION, CHOICE, TEXT }

    /** One selectable line of the menu. */
    private static final class Item {
        final Kind kind;
        final String label, help;
        Runnable action;
        IntConsumer change;
        Supplier<String> value;
        StringBuilder text;
        int maxLength;
        boolean keepCase;
        Predicate<Character> allowed;
        Color color = Color.WHITE;
        Rectangle bounds = new Rectangle();

        Item(Kind kind, String label, String help) {
            this.kind = kind;
            this.label = label;
            this.help = help;
        }
    }

    private final LaserTag app;
    private final Preferences prefs = Preferences.userNodeForPackage(MenuPanel.class);
    private final Timer timer;
    private final List<Item> items = new ArrayList<>();
    private final List<Item> lanItems = new ArrayList<>();
    private Item joinField;
    private int selected;
    private double clock, lanRefresh;
    private String message;
    private Color messageColor = RED;
    private boolean busy;
    private LanDiscovery.Listener listener;

    private final StringBuilder name = new StringBuilder();
    private final StringBuilder port = new StringBuilder();
    private final StringBuilder address = new StringBuilder();
    private int map, bots;
    private Difficulty difficulty;
    private final BufferedImage[] previews = new BufferedImage[Arena.count()];

    MenuPanel(LaserTag app) {
        this.app = app;
        name.append(GameWorld.sanitizeName(prefs.get("name", System.getProperty("user.name", "Player"))));
        filter(name, PixelFont::supports);
        port.append(prefs.getInt("port", Rules.DEFAULT_PORT));
        address.append(prefs.get("address", "localhost").toUpperCase());
        map = Math.floorMod(prefs.getInt("map", 0), Arena.count());
        bots = Math.max(0, Math.min(Rules.MAX_PLAYERS - 1, prefs.getInt("bots", 3)));
        try {
            difficulty = Difficulty.valueOf(prefs.get("difficulty", "NORMAL"));
        } catch (IllegalArgumentException e) {
            difficulty = Difficulty.NORMAL;
        }
        RetroScreen.scanlines = prefs.getBoolean("crt", true);
        for (int i = 0; i < previews.length; i++) previews[i] = GamePanel.drawArena(new Arena(i), 150, 100);
        buildItems();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                onKey(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                onTyped(e.getKeyChar());
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = itemAt(e);
                if (i >= 0 && i != selected) {
                    selected = i;
                    Sound.play(Sound.BLIP);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int i = itemAt(e);
                if (i < 0) return;
                selected = i;
                Item item = allItems().get(i);
                if (item.kind == Kind.CHOICE) {
                    double[] c = toCanvas(e.getX(), e.getY());
                    change(item, c[0] < VALUE_X + 30 ? -1 : 1);
                } else if (item.kind == Kind.ACTION) {
                    activate(item);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        timer = new Timer(33, e -> {
            clock += 0.033;
            if ((lanRefresh -= 0.033) <= 0) {
                lanRefresh = 0.5;
                refreshLan();
            }
            repaint();
        });
    }

    private void buildItems() {
        Item solo = action("SOLO VS BOTS", "YOU AGAINST BOTS. WASD MOVE, MOUSE AIM, CLICK FIRE.", this::playSolo);
        solo.color = GREEN;
        Item split = action("2 PLAYERS 1 KEYBOARD", "P1: WASD + SPACE   P2: ARROWS + ENTER   (PLUS BOTS)", this::playSplit);
        split.color = GREEN;
        Item host = action("HOST ONLINE GAME", "FRIENDS JOIN OVER THE INTERNET WITH YOUR CODE, OR ON YOUR WIFI.", this::host);
        host.color = CYAN;
        Item join = action("JOIN ONLINE GAME", "JOINS THE CODE OR ADDRESS TYPED IN 'JOIN' BELOW.", this::join);
        join.color = CYAN;
        items.add(solo);
        items.add(split);
        items.add(host);
        items.add(join);

        Item nameField = text("NAME", "TYPE YOUR NAME.", name, 14, PixelFont::supports);
        nameField.keepCase = true;
        items.add(nameField);
        items.add(choice("ARENA", "PICK AN ARENA WITH LEFT / RIGHT.", () -> "< " + Arena.NAMES[map] + " >",
                d -> map = Math.floorMod(map + d, Arena.count())));
        items.add(choice("BOTS", "NUMBER OF BOTS. IN ONLINE GAMES THEY LEAVE WHEN PEOPLE JOIN.",
                () -> "< " + bots + " >", d -> bots = Math.max(0, Math.min(Rules.MAX_PLAYERS - 1, bots + d))));
        items.add(choice("BOT SKILL", "HOW SHARP THE BOTS ARE.", () -> "< " + difficulty.label + " >",
                d -> difficulty = Difficulty.values()[Math.floorMod(difficulty.ordinal() + d, Difficulty.values().length)]));
        joinField = text("JOIN", "TYPE AN INTERNET CODE (XXXXX-XXXXX), IP ADDRESS OR HOSTNAME.", address, 40,
                c -> c < 128 && Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == ':');
        items.add(joinField);
        items.add(text("PORT", "TCP PORT FOR HOSTING / JOINING BY IP (DEFAULT " + Rules.DEFAULT_PORT + ").", port, 5,
                Character::isDigit));
        items.add(choice("CRT", "SCANLINES ON OR OFF (ALSO C IN GAME).",
                () -> "< " + (RetroScreen.scanlines ? "ON" : "OFF") + " >", d -> RetroScreen.scanlines = !RetroScreen.scanlines));
        Item quit = action("QUIT", "SEE YOU NEXT TIME!", () -> System.exit(0));
        quit.color = DIM;
        items.add(quit);
    }

    private static Item action(String label, String help, Runnable run) {
        Item i = new Item(Kind.ACTION, label, help);
        i.action = run;
        return i;
    }

    private static Item choice(String label, String help, Supplier<String> value, IntConsumer change) {
        Item i = new Item(Kind.CHOICE, label, help);
        i.value = value;
        i.change = change;
        return i;
    }

    private static Item text(String label, String help, StringBuilder sb, int max, Predicate<Character> allowed) {
        Item i = new Item(Kind.TEXT, label, help);
        i.text = sb;
        i.maxLength = max;
        i.allowed = allowed;
        return i;
    }

    private static void filter(StringBuilder sb, Predicate<Character> allowed) {
        for (int i = sb.length() - 1; i >= 0; i--) if (!allowed.test(sb.charAt(i))) sb.deleteCharAt(i);
    }

    private List<Item> allItems() {
        List<Item> all = new ArrayList<>(items);
        all.addAll(lanItems);
        return all;
    }

    private Item current() {
        List<Item> all = allItems();
        return all.get(Math.min(selected, all.size() - 1));
    }

    // ---------------------------------------------------------------- lifecycle

    void onShow() {
        busy = false;
        listener = new LanDiscovery.Listener();
        timer.start();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    void onHide() {
        timer.stop();
        if (listener != null) listener.close();
        listener = null;
    }

    void showError(String text) {
        message = text == null ? null : text.toUpperCase();
        messageColor = RED;
    }

    // ---------------------------------------------------------------- input

    private void onKey(KeyEvent e) {
        Item item = current();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> move(-1);
            case KeyEvent.VK_DOWN, KeyEvent.VK_TAB -> move(1);
            case KeyEvent.VK_LEFT -> change(item, -1);
            case KeyEvent.VK_RIGHT -> change(item, 1);
            case KeyEvent.VK_ENTER -> {
                if (item.kind == Kind.ACTION) activate(item);
                else if (item.kind == Kind.CHOICE) change(item, 1);
                else move(1);
            }
            case KeyEvent.VK_BACK_SPACE -> {
                if (item.kind == Kind.TEXT && item.text.length() > 0) item.text.setLength(item.text.length() - 1);
            }
            default -> { }
        }
    }

    private void onTyped(char c) {
        Item item = current();
        if (item.kind != Kind.TEXT || c < 32 || c == 127) return;
        if (item.text.length() < item.maxLength && item.allowed.test(c)) {
            item.text.append(item.keepCase ? c : Character.toUpperCase(c));
            Sound.play(Sound.BLIP);
        }
    }

    private void move(int d) {
        selected = Math.floorMod(selected + d, allItems().size());
        Sound.play(Sound.BLIP);
    }

    private void change(Item item, int d) {
        if (item.kind != Kind.CHOICE) return;
        item.change.accept(d);
        Sound.play(Sound.BLIP);
    }

    private void activate(Item item) {
        if (busy) return;
        Sound.play(Sound.SELECT);
        item.action.run();
    }

    private int itemAt(MouseEvent e) {
        double[] c = toCanvas(e.getX(), e.getY());
        List<Item> all = allItems();
        for (int i = 0; i < all.size(); i++) if (all.get(i).bounds.contains(c[0], c[1])) return i;
        return -1;
    }

    // ---------------------------------------------------------------- actions

    private String playerName() {
        return GameWorld.sanitizeName(name.toString());
    }

    private void savePrefs() {
        prefs.put("name", playerName());
        prefs.putInt("map", map);
        prefs.putInt("bots", bots);
        prefs.put("difficulty", difficulty.name());
        prefs.put("address", address.toString().trim());
        prefs.putBoolean("crt", RetroScreen.scanlines);
        try {
            prefs.putInt("port", port());
        } catch (NumberFormatException ignored) {
            // keep the old port
        }
    }

    private int port() {
        int p = Integer.parseInt(port.toString().trim());
        if (p < 1 || p > 65535) throw new NumberFormatException();
        return p;
    }

    private void playSolo() {
        savePrefs();
        Session s = new OfflineSession(map, List.of(playerName()), Math.max(1, bots), difficulty);
        app.startGame(s, GamePanel.Controls.MOUSE);
    }

    private void playSplit() {
        savePrefs();
        String p1 = playerName();
        Session s = new OfflineSession(map, List.of(p1, p1.equals("Player 2") ? "Player 1" : "Player 2"),
                Math.min(bots, Rules.MAX_PLAYERS - 2), difficulty);
        app.startGame(s, GamePanel.Controls.SPLIT_KEYBOARD);
    }

    private void host() {
        savePrefs();
        int p;
        try {
            p = port();
        } catch (NumberFormatException e) {
            showError("Port must be a number from 1 to 65535");
            return;
        }
        try {
            GameServer server = new GameServer(p, map);
            Session s = new LocalSession(map, List.of(playerName()), bots, difficulty, server, playerName(), true);
            app.startGame(s, GamePanel.Controls.MOUSE);
        } catch (java.io.IOException e) {
            showError("Can't host on port " + p + ": " + e.getMessage());
        }
    }

    private void join() {
        savePrefs();
        String[] target = parseJoinTarget(address.toString(), port.toString());
        if (target.length == 1) {
            showError(target[0]);
            selected = items.indexOf(joinField);
            return;
        }
        connect(target[0], Integer.parseInt(target[1]));
    }

    /**
     * Turns what the player typed into {host, port}: an Internet code, "host:port", or a bare host
     * using the port setting. Returns a single-element array holding an error message otherwise.
     */
    static String[] parseJoinTarget(String typed, String portSetting) {
        String target = typed.trim();
        if (target.isEmpty()) return new String[] {"Type a code or address in JOIN first"};
        if (InternetCode.looksLikeCode(target)) {
            String[] decoded = InternetCode.decode(target);
            return decoded != null ? decoded : new String[] {"That code isn't valid - check for typos"};
        }
        String host = target, port = portSetting.trim();
        int colon = target.lastIndexOf(':');
        if (colon > 0 && target.indexOf(':') == colon) {
            host = target.substring(0, colon);
            port = target.substring(colon + 1);
        }
        try {
            int p = Integer.parseInt(port);
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return new String[] {host.toLowerCase(), Integer.toString(p)};
        } catch (NumberFormatException e) {
            return new String[] {"Port must be a number from 1 to 65535"};
        }
    }

    private void connect(String host, int p) {
        busy = true;
        message = "CONNECTING TO " + host.toUpperCase() + ":" + p + " ...";
        messageColor = CYAN;
        String player = playerName();
        Thread t = new Thread(() -> {
            try {
                ClientSession s = new ClientSession(host, p, player);
                SwingUtilities.invokeLater(() -> app.startGame(s, GamePanel.Controls.MOUSE));
            } catch (java.io.IOException | RuntimeException e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    showError("Can't join: " + msg);
                });
            }
        }, "lt-connect");
        t.setDaemon(true);
        t.start();
    }

    private void refreshLan() {
        List<LanDiscovery.Game> games = listener == null ? List.of() : listener.games();
        Item sel = current();
        lanItems.clear();
        for (LanDiscovery.Game g : games.subList(0, Math.min(4, games.size()))) {
            Item i = action(GamePanel.shortName(g.host(), 12) + "  " + g.players() + "/" + Rules.MAX_PLAYERS,
                    "JOIN " + g.host() + " ON YOUR NETWORK (" + g.map() + ", " + g.address() + ")", () -> {
                        savePrefs();
                        connect(g.address(), g.port());
                    });
            i.color = YELLOW;
            lanItems.add(i);
        }
        int keep = allItems().indexOf(sel);
        selected = keep >= 0 ? keep : Math.min(selected, allItems().size() - 1);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void render(Graphics2D g) {
        drawBackground(g);
        drawTitle(g);

        int y = 80;
        for (int i = 0; i < items.size(); i++) {
            if (i == 4) {
                y += 6;
                PixelFont.draw(g, "- SETTINGS -", LEFT, y, 1, DIM);
                y += LINE + 2;
            }
            drawItem(g, items.get(i), i == selected, LEFT, y);
            y += LINE;
        }

        // Right column: arena preview and LAN games.
        int px = 310, py = 80;
        PixelFont.draw(g, Arena.NAMES[map].toUpperCase(), px, py, 1, DIM);
        g.setColor(CYAN);
        g.drawRect(px - 2, py + 10, 153, 103);
        g.drawImage(previews[map], px, py + 12, null);
        int ly = py + 126;
        PixelFont.draw(g, "ON YOUR NETWORK:", px, ly, 1, DIM);
        ly += LINE + 2;
        if (lanItems.isEmpty()) {
            PixelFont.draw(g, "NO GAMES YET" + ".".repeat((int) (clock * 2) % 4), px, ly, 1, new Color(0x5F574F));
        }
        for (int i = 0; i < lanItems.size(); i++) {
            drawItem(g, lanItems.get(i), items.size() + i == selected, px, ly);
            ly += LINE;
        }

        g.setColor(new Color(0x1D2B53));
        g.fillRect(0, 314, W, 1);
        PixelFont.drawCentered(g, current().help, W / 2, 320, 1, GREY);
        if (message != null) {
            PixelFont.drawCentered(g, message.length() > 78 ? message.substring(0, 78) : message, W / 2, 333, 1, messageColor);
        }
        PixelFont.drawCentered(g, "ARROWS: CHOOSE   ENTER: GO   LEFT/RIGHT: CHANGE", W / 2, 347, 1, DIM);
    }

    private void drawItem(Graphics2D g, Item item, boolean sel, int x, int y) {
        item.bounds = new Rectangle(x - 14, y - 2, x > LEFT ? 166 : 270, LINE);
        if (sel) PixelFont.draw(g, ">", x - 10 + ((int) (clock * 4) % 2), y, 1, YELLOW);
        Color labelColor = sel ? YELLOW : item.kind == Kind.ACTION ? item.color : GREY;
        PixelFont.draw(g, item.label, x, y, 1, labelColor);
        if (item.kind == Kind.CHOICE) {
            PixelFont.draw(g, item.value.get().toUpperCase(), VALUE_X, y, 1, Color.WHITE);
        } else if (item.kind == Kind.TEXT) {
            String t = item.text.toString();
            if (t.length() > 24) t = t.substring(t.length() - 24);
            PixelFont.draw(g, t, VALUE_X, y, 1, Color.WHITE);
            if (sel && (int) (clock * 3) % 2 == 0) {
                g.setColor(YELLOW);
                g.fillRect(VALUE_X + PixelFont.width(t, 1) + (t.isEmpty() ? 0 : 2), y, 5, 7);
            }
        }
    }

    /** An 80s perspective grid scrolling toward the viewer behind the title. */
    private void drawBackground(Graphics2D g) {
        int horizon = 8, bottom = 68;
        g.setColor(new Color(0x1D2B53));
        for (int i = 0; i < 8; i++) {
            double t = (i + (clock * 0.8) % 1) / 8;
            g.fillRect(0, horizon + (int) (t * t * (bottom - horizon)), W, 1);
        }
        for (int x = -12; x <= 12; x++) g.drawLine(W / 2 + x * 10, horizon, W / 2 + x * 48, bottom);
        g.setColor(new Color(0x7E2553));
        g.fillRect(0, bottom, W, 1);
    }

    private void drawTitle(Graphics2D g) {
        String title = "LASER TAG";
        int scale = 5, x = (W - PixelFont.width(title, scale)) / 2, y = 18;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int bounce = (int) Math.round(Math.sin(clock * 4 + i * 0.6) * 2);
            int cx = x + i * PixelFont.ADVANCE * scale;
            PixelFont.draw(g, ch, cx + 3, y + 3 + bounce, scale, Color.BLACK);
            PixelFont.draw(g, ch, cx, y + bounce, scale, new Color(Rules.COLORS[(i + (int) (clock * 3)) % Rules.COLORS.length]));
        }
    }
}
