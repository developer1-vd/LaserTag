package lasertag;

import java.awt.Dimension;
import java.io.IOException;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point. Run with no arguments for the game, or {@code --server} for a headless dedicated host. */
public final class LaserTag {
    private final JFrame frame = new JFrame("Laser Tag");
    private final MenuPanel menu;
    private GamePanel game;

    private LaserTag() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 700));
        frame.setSize(1280, 860);
        frame.setLocationRelativeTo(null);
        menu = new MenuPanel(this);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--server")) {
            DedicatedServer.run(args);
            return;
        }
        if (args.length > 0) {
            System.out.println("Usage: java -jar lasertag.jar [--server [port] [map 0-" + (Arena.count() - 1)
                    + "] [bots] [easy|normal|hard]]");
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel is fine
        }
        Sound.init();
        SwingUtilities.invokeLater(() -> {
            LaserTag app = new LaserTag();
            app.showMenu();
            app.frame.setVisible(true);
        });
    }

    void showMenu() {
        if (game != null) {
            game.stop();
            game = null;
        }
        frame.setContentPane(menu);
        frame.revalidate();
        frame.repaint();
        menu.onShow();
        menu.showError(null);
    }

    void startGame(Session session, GamePanel.Controls controls) {
        menu.onHide();
        game = new GamePanel(this, session, controls);
        frame.setContentPane(game);
        frame.revalidate();
        game.start();
    }

    /** Runs a match with no window: only bots and remote players. */
    static final class DedicatedServer {
        static void run(String[] args) throws IOException, InterruptedException {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : Rules.DEFAULT_PORT;
            int map = args.length > 2 ? Integer.parseInt(args[2]) : 0;
            int bots = args.length > 3 ? Integer.parseInt(args[3]) : 2;
            Difficulty diff = args.length > 4 ? Difficulty.valueOf(args[4].toUpperCase()) : Difficulty.NORMAL;
            GameServer server = new GameServer(port, map);
            LocalSession session = new LocalSession(map, List.of(), Math.min(bots, Rules.MAX_PLAYERS), diff, server,
                    "Dedicated server");
            System.out.println("Laser Tag server: arena " + session.world().arena.name + ", " + session.status());
            long last = System.nanoTime();
            while (true) {
                long now = System.nanoTime();
                session.update(Math.min(0.05, (now - last) / 1e9));
                last = now;
                Thread.sleep(16);
            }
        }
    }
}
