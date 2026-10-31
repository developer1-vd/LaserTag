package lasertag;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * The app icon: 16x16 pixel art (a player firing a laser that ricochets into a target),
 * scaled up with hard edges. Also writes the .png/.ico/.icns files the installers need.
 */
final class AppIcon {
    private AppIcon() {}

    /** Drawn inside a rounded frame: Y beam, R/r player, W player's eye, G/g target. */
    private static final String[] ART = {
        "................",
        "........Y.......",
        ".......Y.Y......",
        ".......Y.Y......",
        "......Y...Y.....",
        "......Y...Y.....",
        ".....Y.....GGG..",
        ".....Y.....GgG..",
        "....Y......GGG..",
        "...RRR..........",
        "..RrrrR.........",
        "..RrWrR.........",
        "..RrrrR.........",
        "...RRR..........",
        "................",
        "................",
    };

    private static final Color FRAME = new Color(0x29ADFF), BG = new Color(0x1D2B53);
    private static final Color BEAM = new Color(0xFFEC27), RED = new Color(0xFF004D), DARK_RED = new Color(0x7E2553);
    private static final Color GREEN = new Color(0x00E436), DARK_GREEN = new Color(0x008751);

    /** The icon at a given size (a multiple of 16 looks best). */
    static BufferedImage image(int size) {
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // A one-pixel frame with cut corners around a filled background, then the art on top.
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean edgeX = x == 0 || x == 15, edgeY = y == 0 || y == 15;
                if (edgeX && edgeY) continue;
                base.setRGB(x, y, (edgeX || edgeY ? FRAME : BG).getRGB());
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Color c = switch (ART[y].charAt(x)) {
                    case 'Y' -> BEAM;
                    case 'R' -> RED;
                    case 'r' -> DARK_RED;
                    case 'W' -> Color.WHITE;
                    case 'G' -> GREEN;
                    case 'g' -> DARK_GREEN;
                    default -> null;
                };
                if (c != null) base.setRGB(x, y, c.getRGB());
            }
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) out.setRGB(x, y, base.getRGB(x * 16 / size, y * 16 / size));
        }
        return out;
    }

    /** Icons for the window title bar and taskbar, in the sizes operating systems ask for. */
    static List<Image> windowIcons() {
        List<Image> list = new ArrayList<>();
        for (int s : new int[] {16, 32, 48, 64, 128, 256}) list.add(image(s));
        return list;
    }

    /** Writes icon.png (Linux), icon.ico (Windows) and icon.icns (macOS) into a folder. */
    static void writeIconFiles(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve("icon.png"), png(256));
        Files.write(dir.resolve("icon.ico"), ico(16, 24, 32, 48, 64, 128, 256));
        Files.write(dir.resolve("icon.icns"), icns());
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image(size), "png", out);
        return out.toByteArray();
    }

    /** A Windows .ico holding PNG-compressed images (supported since Windows Vista). */
    static byte[] ico(int... sizes) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int s : sizes) images.add(png(s));
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = header.capacity();
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)); // 0 means 256
            header.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            header.putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        for (byte[] img : images) out.write(img);
        return out.toByteArray();
    }

    /** A macOS .icns with PNG entries from 16 to 1024 pixels. */
    static byte[] icns() throws IOException {
        String[] types = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10"};
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (int i = 0; i < types.length; i++) {
            byte[] img = png(sizes[i]);
            data.writeBytes(types[i]);
            data.writeInt(8 + img.length);
            data.write(img);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream file = new DataOutputStream(out);
        file.writeBytes("icns");
        file.writeInt(8 + body.size());
        file.write(body.toByteArray());
        return out.toByteArray();
    }
}
