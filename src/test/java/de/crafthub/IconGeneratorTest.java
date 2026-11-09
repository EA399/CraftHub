package de.crafthub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IconGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsWindowsIconWithCraftHubPng() throws Exception {
        Path iconPath = temporaryDirectory.resolve("CraftHub.ico");
        IconGenerator.main(new String[]{iconPath.toString()});

        byte[] icon = Files.readAllBytes(iconPath);
        ByteBuffer header = ByteBuffer.wrap(icon).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, header.getShort());
        assertEquals(1, header.getShort());
        assertEquals(1, header.getShort());
        header.position(14);
        int imageSize = header.getInt();
        assertEquals(22, header.getInt());
        var image = ImageIO.read(new ByteArrayInputStream(Arrays.copyOfRange(icon, 22, 22 + imageSize)));
        assertNotNull(image);
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
    }
}