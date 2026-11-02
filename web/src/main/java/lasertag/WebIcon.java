package lasertag;

import java.nio.file.Path;

/** Build step: writes the game's icon into the web site folder for the browser tab (runs on the JVM, not in the browser). */
public final class WebIcon {
    private WebIcon() {}

    public static void main(String[] args) throws Exception {
        AppIcon.writeIconFiles(Path.of(args[0]));
    }
}
