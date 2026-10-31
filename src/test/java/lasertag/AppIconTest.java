package lasertag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppIconTest {
    @Test
    void icoHasAReadablePngForEverySize() throws Exception {
        int[] sizes = {16, 32, 256};
        ByteBuffer ico = ByteBuffer.wrap(AppIcon.ico(sizes)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, ico.getShort(0));
        assertEquals(1, ico.getShort(2)); // icon, not cursor
        assertEquals(sizes.length, ico.getShort(4));
        for (int i = 0; i < sizes.length; i++) {
            int entry = 6 + i * 16;
            int stated = Byte.toUnsignedInt(ico.get(entry));
            assertEquals(sizes[i] >= 256 ? 0 : sizes[i], stated);
            int length = ico.getInt(entry + 8), offset = ico.getInt(entry + 12);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(ico.array(), offset, length));
            assertNotNull(img);
            assertEquals(sizes[i], img.getWidth());
        }
    }

    @Test
    void icnsEntriesAddUpAndDecode() throws Exception {
        byte[] icns = AppIcon.icns();
        ByteBuffer b = ByteBuffer.wrap(icns);
        assertEquals("icns", new String(icns, 0, 4, "US-ASCII"));
        assertEquals(icns.length, b.getInt(4));
        int pos = 8, entries = 0;
        while (pos < icns.length) {
            int length = b.getInt(pos + 4);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Arrays.copyOfRange(icns, pos + 8, pos + length)));
            assertNotNull(img, new String(icns, pos, 4, "US-ASCII"));
            pos += length;
            entries++;
        }
        assertEquals(icns.length, pos);
        assertEquals(7, entries);
    }

    @Test
    void writesAllIconFilesWithHardPixelEdges(@TempDir Path dir) throws Exception {
        AppIcon.writeIconFiles(dir);
        for (String f : new String[] {"icon.png", "icon.ico", "icon.icns"}) assertNotEquals(0, Files.size(dir.resolve(f)), f);
        BufferedImage big = AppIcon.image(256);
        // Scaled 16x, so each art pixel is a solid 16x16 block.
        assertEquals(big.getRGB(16 * 4, 16 * 11), big.getRGB(16 * 4 + 15, 16 * 11 + 15));
        assertEquals(0, big.getRGB(0, 0) >>> 24); // transparent corner
    }
}
