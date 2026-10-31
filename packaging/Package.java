import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds Laser Tag and native, Java-bundled packages for the operating system it runs on.
 * Needs only a JDK (17+). Run from the project folder:
 *
 * <pre>java packaging/Package.java [--no-installer] [--strict] [--version 1.2.3]</pre>
 *
 * Output goes to {@code dist/}:
 * <ul>
 *   <li>all systems: {@code LaserTag-VERSION.jar} (needs Java installed)</li>
 *   <li>Windows: {@code LaserTag-VERSION-windows-ARCH.zip} and {@code .msi} (needs WiX)</li>
 *   <li>macOS: {@code LaserTag-VERSION-macos-ARCH.zip} and {@code .dmg}</li>
 *   <li>Linux: {@code LaserTag-VERSION-linux-ARCH.tar.gz} and a {@code .deb} (needs fakeroot; plus {@code .rpm} if rpmbuild is installed)</li>
 * </ul>
 * ARCH is the CPU of the machine it runs on (x64 or arm64): packages bundle a native Java runtime.
 * <ul>
 * </ul>
 * {@code --no-installer} skips the installer; {@code --strict} fails instead of warning when an installer can't be built;
 * {@code --version} overrides the version from pom.xml (CI passes the release tag).
 */
public class Package {
    static final String NAME = "LaserTag";
    static final String DISPLAY_NAME = "Laser Tag";
    static final String DESCRIPTION = "Retro laser tag arena shooter with bots and online play";
    /** Keeps Windows upgrades replacing the old install instead of adding a second one. Never change it. */
    static final String WINDOWS_UPGRADE_UUID = "6f1d3a52-8c1e-4b8e-9a51-3e5f0c7d2b91";

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path BUILD = ROOT.resolve("build/package");
    static final Path DIST = ROOT.resolve("dist");

    public static void main(String[] args) throws Exception {
        List<String> flags = List.of(args);
        boolean installer = !flags.contains("--no-installer"), strict = flags.contains("--strict");
        if (!Files.exists(ROOT.resolve("pom.xml"))) fail("Run this from the LaserTag project folder.");
        if (Runtime.version().feature() < 17) fail("Java 17 or newer is needed (this is " + Runtime.version() + ").");

        int v = flags.indexOf("--version");
        String os = os(), version = v >= 0 && v + 1 < args.length ? args[v + 1] : version();
        if (!version.matches("[1-9]\\d{0,2}(\\.\\d{1,3}){0,2}")) {
            fail("Version must look like 1.2.3 (installers require it), not '" + version + "'.");
        }
        System.out.println("== Packaging Laser Tag " + version + " for " + os);
        deleteTree(BUILD);
        deleteTree(DIST);
        Path classes = BUILD.resolve("classes"), input = BUILD.resolve("input"), out = BUILD.resolve("out");
        Path icons = BUILD.resolve("icons");
        Files.createDirectories(input);
        Files.createDirectories(DIST);

        System.out.println("-- Compiling");
        List<String> javac = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(ROOT.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> javac.add(p.toString()));
        }
        tool("javac", javac);

        System.out.println("-- Building the jar");
        Path jar = input.resolve("lasertag.jar");
        tool("jar", List.of("--create", "--file", jar.toString(), "--main-class", "lasertag.LaserTag",
                "-C", classes.toString(), "."));
        Files.copy(jar, DIST.resolve(NAME + "-" + version + ".jar"));

        System.out.println("-- Drawing icons");
        writeIcons(classes, icons);
        Path icon = icons.resolve(switch (os) {
            case "windows" -> "icon.ico";
            case "macos" -> "icon.icns";
            default -> "icon.png";
        });

        System.out.println("-- Bundling a Java runtime (jpackage app image)");
        List<String> image = new ArrayList<>(List.of("--type", "app-image", "--input", input.toString(),
                "--main-jar", "lasertag.jar", "--main-class", "lasertag.LaserTag",
                "--add-modules", modules(), "--jlink-options", jlinkOptions()));
        image.addAll(common(version, icon, out));
        if (os.equals("macos")) image.addAll(List.of("--mac-package-name", DISPLAY_NAME));
        tool("jpackage", image);
        Path appImage = out.resolve(os.equals("macos") ? NAME + ".app" : NAME);

        System.out.println("-- Archiving the app");
        String archive = NAME + "-" + version + "-" + os + "-" + arch();
        switch (os) {
            case "windows" -> zip(appImage, DIST.resolve(archive + ".zip"));
            // ditto and tar keep the executable permissions that a plain zip would lose.
            case "macos" -> exec("ditto", "-c", "-k", "--keepParent", appImage.toString(), DIST.resolve(archive + ".zip").toString());
            default -> exec("tar", "-czf", DIST.resolve(archive + ".tar.gz").toString(), "-C", out.toString(), NAME);
        }

        if (installer) {
            for (String type : installerTypes(os)) {
                System.out.println("-- Building the " + type + " installer");
                List<String> inst = new ArrayList<>(List.of("--type", type, "--app-image", appImage.toString()));
                inst.addAll(common(version, icon, out));
                inst.addAll(installerOptions(os));
                try {
                    tool("jpackage", inst);
                } catch (IllegalStateException e) {
                    String hint = switch (os) {
                        case "windows" -> " Install the WiX Toolset (https://wixtoolset.org) to build .msi files.";
                        case "linux" -> " Install fakeroot (e.g. sudo apt install fakeroot) to build .deb files.";
                        default -> "";
                    };
                    if (strict) fail("Could not build the " + type + " installer." + hint);
                    System.out.println("!! Skipped the " + type + " installer: " + e.getMessage() + "." + hint);
                }
            }
            try (Stream<Path> made = Files.list(out)) {
                for (Path p : made.filter(Files::isRegularFile).toList()) {
                    // .deb/.rpm names already include the architecture; add it to the others.
                    String file = p.getFileName().toString(), ext = file.substring(file.lastIndexOf('.'));
                    if (List.of(".msi", ".exe", ".dmg", ".pkg").contains(ext)) file = archive + ext;
                    Files.move(p, DIST.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.out.println("== Done. Files in " + ROOT.relativize(DIST) + ":");
        try (Stream<Path> made = Files.list(DIST)) {
            for (Path p : made.sorted().toList()) {
                System.out.printf("   %-40s %6.1f MB%n", p.getFileName(), Files.size(p) / 1048576.0);
            }
        }
    }

    static List<String> common(String version, Path icon, Path out) {
        return List.of("--name", NAME, "--app-version", version, "--vendor", DISPLAY_NAME,
                "--description", DESCRIPTION, "--copyright", "Licensed under the GNU GPL v3",
                "--icon", icon.toString(), "--dest", out.toString());
    }

    static List<String> installerTypes(String os) {
        return switch (os) {
            case "windows" -> List.of("msi");
            case "macos" -> List.of("dmg");
            default -> onPath("rpmbuild") ? List.of("deb", "rpm") : List.of("deb");
        };
    }

    static List<String> installerOptions(String os) {
        return switch (os) {
            case "windows" -> List.of("--win-menu", "--win-menu-group", DISPLAY_NAME, "--win-shortcut",
                    "--win-dir-chooser", "--win-upgrade-uuid", WINDOWS_UPGRADE_UUID,
                    "--license-file", ROOT.resolve("LICENSE").toString());
            case "macos" -> List.of("--license-file", ROOT.resolve("LICENSE").toString());
            default -> List.of("--linux-package-name", "lasertag", "--linux-shortcut", "--linux-menu-group", "Game",
                    "--linux-app-category", "games", "--linux-deb-maintainer", "lasertag@users.noreply.github.com");
        };
    }

    /** Only the Java modules the game uses, which keeps the bundled runtime small. */
    static String modules() {
        List<String> mods = new ArrayList<>(List.of("java.base", "java.desktop", "java.prefs", "java.net.http"));
        // HTTPS (the public-IP lookup) needs elliptic-curve crypto, a separate module before Java 22.
        if (ModuleFinder.ofSystem().find("jdk.crypto.ec").isPresent()) mods.add("jdk.crypto.ec");
        return String.join(",", mods);
    }

    static String jlinkOptions() {
        String compress = Runtime.version().feature() >= 21 ? "--compress=zip-6" : "--compress=2";
        return "--strip-debug --no-man-pages --no-header-files " + compress;
    }

    static String os() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "macos";
        return "linux";
    }

    static String arch() {
        String a = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (a.equals("amd64") || a.equals("x86_64")) return "x64";
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        return a;
    }

    static String version() throws IOException {
        Matcher m = Pattern.compile("<artifactId>lasertag</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(Files.readString(ROOT.resolve("pom.xml")));
        if (!m.find()) fail("Could not find the version in pom.xml");
        return m.group(1).trim().replace("-SNAPSHOT", "");
    }

    /** Uses the game's own AppIcon class to write icon.png/.ico/.icns. */
    static void writeIcons(Path classes, Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()})) {
            Method write = loader.loadClass("lasertag.AppIcon").getDeclaredMethod("writeIconFiles", Path.class);
            write.setAccessible(true);
            write.invoke(null, dir);
        }
    }

    /** Runs a JDK tool (javac, jar, jpackage) in this process. */
    static void tool(String name, List<String> args) {
        ToolProvider tool = ToolProvider.findFirst(name)
                .orElseThrow(() -> new IllegalStateException(name + " not found - is this a full JDK 17+?"));
        int code = tool.run(System.out, System.err, args.toArray(String[]::new));
        if (code != 0) throw new IllegalStateException(name + " failed (exit code " + code + ")");
    }

    static void exec(String... command) throws IOException, InterruptedException {
        int code = new ProcessBuilder(command).inheritIO().start().waitFor();
        if (code != 0) throw new IllegalStateException(command[0] + " failed (exit code " + code + ")");
    }

    static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, program))) return true;
        }
        return false;
    }

    static void zip(Path folder, Path zipFile) throws IOException {
        Path parent = folder.getParent();
        try (OutputStream os = Files.newOutputStream(zipFile); ZipOutputStream zip = new ZipOutputStream(os);
                Stream<Path> files = Files.walk(folder)) {
            for (Path p : files.sorted().toList()) {
                String name = parent.relativize(p).toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    zip.putNextEntry(new ZipEntry(name + "/"));
                } else {
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(p, zip);
                }
                zip.closeEntry();
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(1);
    }
}
