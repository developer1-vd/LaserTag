package lasertag;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Title screen: player settings plus offline and online game launchers. */
final class MenuPanel extends JPanel {
    private static final Color BG = new Color(0x07070F);
    private static final Color CARD = new Color(0x10142A);
    private static final Color TEXT = new Color(0xC8CEE6);
    private static final Color ACCENT = new Color(0x2DE2E6);
    private static final Color PINK = new Color(0xFF3BF2);
    private static final Font LABEL = new Font(Font.SANS_SERIF, Font.BOLD, 13);

    private final LaserTag app;
    private final Preferences prefs = Preferences.userNodeForPackage(MenuPanel.class);

    private final JTextField nameField = field(prefs.get("name", System.getProperty("user.name", "Player")));
    private final JComboBox<String> mapBox = new JComboBox<>(Arena.NAMES);
    private final JComboBox<Difficulty> difficultyBox = new JComboBox<>(Difficulty.values());
    private final JSpinner botsSpinner = new JSpinner(new SpinnerNumberModel(3, 0, Rules.MAX_PLAYERS - 1, 1));
    private final JTextField portField = field(Integer.toString(prefs.getInt("port", Rules.DEFAULT_PORT)));
    private final JTextField addressField = field(prefs.get("address", "localhost"));
    private final DefaultListModel<LanDiscovery.Game> lanModel = new DefaultListModel<>();
    private final JList<LanDiscovery.Game> lanList = new JList<>(lanModel);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final Timer lanRefresh;
    private LanDiscovery.Listener listener;
    private boolean busy;

    MenuPanel(LaserTag app) {
        this.app = app;
        setBackground(BG);
        setLayout(new GridBagLayout());

        mapBox.setSelectedIndex(Math.floorMod(prefs.getInt("map", 0), Arena.count()));
        difficultyBox.setSelectedItem(Difficulty.valueOf(prefs.get("difficulty", Difficulty.NORMAL.name())));
        botsSpinner.setValue(Math.max(0, Math.min(Rules.MAX_PLAYERS - 1, prefs.getInt("bots", 3))));
        for (JComponent c : new JComponent[] {mapBox, difficultyBox, botsSpinner}) style(c);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.insets = new Insets(0, 0, 18, 0);
        add(new Title(), gc);

        JPanel cards = new JPanel(new GridLayout(1, 3, 18, 0));
        cards.setOpaque(false);
        cards.add(settingsCard());
        cards.add(offlineCard());
        cards.add(onlineCard());
        gc.gridy = 1;
        gc.insets = new Insets(0, 24, 12, 24);
        add(cards, gc);

        statusLabel.setForeground(new Color(0xFF6B6B));
        statusLabel.setFont(LABEL);
        gc.gridy = 2;
        add(statusLabel, gc);

        JLabel help = new JLabel("<html><center>Tag opponents with your laser. Beams ricochet off walls twice. "
                + "Each shot drains energy, which recharges over time.<br>Tagged players respawn after "
                + (int) Rules.TAGGED_TIME + "s with a short shield. First to " + Rules.SCORE_LIMIT
                + " tags or most tags in " + (int) Rules.ROUND_TIME / 60 + " minutes wins the round.</center></html>");
        help.setForeground(new Color(0x7F8AB0));
        help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        gc.gridy = 3;
        add(help, gc);

        lanList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lanList.setBackground(new Color(0x0A0E1E));
        lanList.setForeground(TEXT);
        lanList.addListSelectionListener(e -> {
            LanDiscovery.Game g = lanList.getSelectedValue();
            if (g != null) {
                addressField.setText(g.address());
                portField.setText(Integer.toString(g.port()));
            }
        });
        lanList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && lanList.getSelectedValue() != null) join();
            }
        });
        lanRefresh = new Timer(1000, e -> refreshLan());
    }

    // ---------------------------------------------------------------- layout

    private JPanel settingsCard() {
        JPanel p = card("PLAYER", ACCENT);
        row(p, "Name", nameField);
        row(p, "Arena", mapBox);
        row(p, "Bots", botsSpinner);
        row(p, "Bot skill", difficultyBox);
        filler(p);
        return p;
    }

    private JPanel offlineCard() {
        JPanel p = card("OFFLINE", new Color(0x3BFF7A));
        button(p, "Solo vs Bots", "Mouse aims - WASD moves - click / Space fires", this::playSolo);
        button(p, "2 Players, 1 Keyboard", "P1: WASD + Space    P2: Arrows + Enter", this::playSplit);
        filler(p);
        return p;
    }

    private JPanel onlineCard() {
        JPanel p = card("ONLINE", PINK);
        row(p, "Port", portField);
        button(p, "Host Game", "Others join using your IP; bots fill empty slots", this::host);
        row(p, "Host IP", addressField);
        button(p, "Join Game", null, this::join);
        JLabel lan = new JLabel("Games on your network (double-click to join)");
        lan.setForeground(new Color(0x7F8AB0));
        lan.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        add(p, lan, 0);
        JScrollPane scroll = new JScrollPane(lanList);
        scroll.setPreferredSize(new Dimension(280, 70));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x2A3050)));
        add(p, scroll, 4);
        return p;
    }

    private static JPanel card(String title, Color color) {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        p.setPreferredSize(new Dimension(320, 330));
        JLabel l = new JLabel(title);
        l.setForeground(color);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        add(p, l, 10);
        return p;
    }

    private static void add(JPanel p, Component c, int bottomGap) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = nextRow(p);
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, bottomGap, 0);
        p.add(c, gc);
    }

    /** Cards stack their contents in rows; this hands out the next free row index. */
    private static int nextRow(JPanel p) {
        Integer row = (Integer) p.getClientProperty("row");
        int next = row == null ? 0 : row + 1;
        p.putClientProperty("row", next);
        return next;
    }

    private static void row(JPanel p, String label, JComponent field) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = nextRow(p);
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 10);
        JLabel l = new JLabel(label);
        l.setForeground(TEXT);
        l.setFont(LABEL);
        l.setPreferredSize(new Dimension(70, 24));
        p.add(l, gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.insets = new Insets(4, 0, 4, 0);
        p.add(field, gc);
    }

    private static void button(JPanel p, String text, String hint, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0x1E2650));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x3A4680)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        b.addActionListener(e -> action.run());
        add(p, b, hint == null ? 10 : 2);
        if (hint != null) {
            JLabel l = new JLabel(hint);
            l.setForeground(new Color(0x7F8AB0));
            l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            add(p, l, 12);
        }
    }

    private static void filler(JPanel p) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = nextRow(p);
        gc.weighty = 1;
        p.add(new JLabel(), gc);
    }

    private static JTextField field(String text) {
        JTextField f = new JTextField(text, 12);
        style(f);
        f.setCaretColor(Color.WHITE);
        return f;
    }

    private static void style(JComponent c) {
        c.setBackground(new Color(0x0A0E1E));
        c.setForeground(Color.WHITE);
        c.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    // ---------------------------------------------------------------- actions

    void onShow() {
        busy = false;
        listener = new LanDiscovery.Listener();
        lanRefresh.start();
        refreshLan();
    }

    void onHide() {
        lanRefresh.stop();
        if (listener != null) listener.close();
        listener = null;
    }

    void showError(String message) {
        statusLabel.setForeground(new Color(0xFF6B6B));
        statusLabel.setText(message == null ? " " : message);
    }

    private void refreshLan() {
        if (listener == null) return;
        List<LanDiscovery.Game> games = listener.games();
        LanDiscovery.Game selected = lanList.getSelectedValue();
        lanModel.clear();
        games.forEach(lanModel::addElement);
        if (selected != null) {
            for (LanDiscovery.Game g : games) {
                if (g.address().equals(selected.address()) && g.port() == selected.port()) lanList.setSelectedValue(g, false);
            }
        }
        if (games.isEmpty()) {
            lanList.setToolTipText(listener.available() ? "No games found yet" : "LAN discovery unavailable");
        }
    }

    private String playerName() {
        return GameWorld.sanitizeName(nameField.getText());
    }

    private int bots() {
        return (Integer) botsSpinner.getValue();
    }

    private Difficulty difficulty() {
        return (Difficulty) Objects.requireNonNull(difficultyBox.getSelectedItem());
    }

    private void savePrefs() {
        prefs.put("name", playerName());
        prefs.putInt("map", mapBox.getSelectedIndex());
        prefs.putInt("bots", bots());
        prefs.put("difficulty", difficulty().name());
        prefs.put("address", addressField.getText().trim());
        try {
            prefs.putInt("port", port());
        } catch (NumberFormatException ignored) {
            // keep the old port
        }
    }

    private int port() {
        int p = Integer.parseInt(portField.getText().trim());
        if (p < 1 || p > 65535) throw new NumberFormatException();
        return p;
    }

    private void playSolo() {
        if (busy) return;
        savePrefs();
        int bots = Math.max(1, bots());
        Session s = new LocalSession(mapBox.getSelectedIndex(), List.of(playerName()), bots, difficulty(), null, null);
        app.startGame(s, GamePanel.Controls.MOUSE);
    }

    private void playSplit() {
        if (busy) return;
        savePrefs();
        int bots = Math.min(bots(), Rules.MAX_PLAYERS - 2);
        String p1 = playerName();
        Session s = new LocalSession(mapBox.getSelectedIndex(), List.of(p1, p1.equals("Player 2") ? "Player 1" : "Player 2"),
                bots, difficulty(), null, null);
        app.startGame(s, GamePanel.Controls.SPLIT_KEYBOARD);
    }

    private void host() {
        if (busy) return;
        savePrefs();
        int port;
        try {
            port = port();
        } catch (NumberFormatException e) {
            showError("Port must be a number between 1 and 65535");
            return;
        }
        try {
            GameServer server = new GameServer(port, mapBox.getSelectedIndex());
            Session s = new LocalSession(mapBox.getSelectedIndex(), List.of(playerName()), bots(), difficulty(), server,
                    playerName());
            app.startGame(s, GamePanel.Controls.MOUSE);
        } catch (java.io.IOException e) {
            showError("Could not host on port " + port + ": " + e.getMessage());
        }
    }

    private void join() {
        if (busy) return;
        savePrefs();
        String address = addressField.getText().trim();
        int port;
        try {
            port = port();
        } catch (NumberFormatException e) {
            showError("Port must be a number between 1 and 65535");
            return;
        }
        if (address.isEmpty()) {
            showError("Enter the host's IP address");
            return;
        }
        busy = true;
        statusLabel.setForeground(ACCENT);
        statusLabel.setText("Connecting to " + address + ":" + port + " ...");
        String name = playerName();
        Thread t = new Thread(() -> {
            try {
                ClientSession s = new ClientSession(address, port, name);
                javax.swing.SwingUtilities.invokeLater(() -> app.startGame(s, GamePanel.Controls.MOUSE));
            } catch (java.io.IOException | RuntimeException e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    busy = false;
                    showError("Could not join: " + msg);
                });
            }
        }, "lt-connect");
        t.setDaemon(true);
        t.start();
    }

    /** The glowing "LASER TAG" logo. */
    private static final class Title extends JComponent {
        Title() {
            setPreferredSize(new Dimension(700, 110));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 72);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            String text = "LASER TAG";
            int x = (getWidth() - fm.stringWidth(text)) / 2, y = 78;
            for (int i = 6; i >= 1; i--) {
                g.setColor(new Color(PINK.getRed(), PINK.getGreen(), PINK.getBlue(), 12));
                for (int dx = -i; dx <= i; dx += i) for (int dy = -i; dy <= i; dy += i) g.drawString(text, x + dx, y + dy);
            }
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(ACCENT);
            g.drawLine(x - 40, y + 16, x + fm.stringWidth(text) + 40, y + 16);
            g.dispose();
        }
    }
}
