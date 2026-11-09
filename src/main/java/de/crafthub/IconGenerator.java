package de.crafthub;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IconGenerator {
    private IconGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: IconGenerator <output.ico>");
        }
        writeIco(Path.of(args[0]));
    }

    public static BufferedImage createImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        float scale = size / 256f;
        graphics.scale(scale, scale);

        graphics.setPaint(new GradientPaint(24, 18, new Color(31, 44, 58), 232, 240,
                new Color(13, 23, 31)));
        graphics.fillRoundRect(8, 8, 240, 240, 56, 56);

        Path2D.Float crystal = polygon(128, 40, 203, 83, 203, 171, 128, 215, 53, 171, 53, 83);
        graphics.setPaint(new GradientPaint(52, 48, new Color(43, 244, 179),
                202, 202, new Color(5, 177, 143)));
        graphics.fill(crystal);

        graphics.setColor(new Color(141, 255, 207));
        graphics.fill(polygon(128, 40, 203, 83, 128, 105, 53, 83));
        graphics.setColor(new Color(27, 207, 151));
        graphics.fill(polygon(53, 83, 128, 105, 128, 215, 53, 171));
        graphics.setColor(new Color(12, 164, 139));
        graphics.fill(polygon(203, 83, 128, 105, 128, 215, 203, 171));
        graphics.setColor(new Color(211, 255, 232, 175));
        graphics.draw(polygon(128, 40, 203, 83, 203, 171, 128, 215, 53, 171, 53, 83, 128, 40));
        graphics.dispose();
        return image;
    }

    private static Path2D.Float polygon(float... coordinates) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(coordinates[0], coordinates[1]);
        for (int index = 2; index < coordinates.length; index += 2) {
            path.lineTo(coordinates[index], coordinates[index + 1]);
        }
        path.closePath();
        return path;
    }

    private static void writeIco(Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(createImage(256), "png", png);
        byte[] image = png.toByteArray();
        ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.put((byte) 0);
        header.put((byte) 0);
        header.put((byte) 0);
        header.put((byte) 0);
        header.putShort((short) 1);
        header.putShort((short) 32);
        header.putInt(image.length);
        header.putInt(22);
        Files.write(output, ByteBuffer.allocate(header.capacity() + image.length)
                .put(header.array()).put(image).array());
    }
}