package com.studentleague.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageOrientationTest {

    @Test
    void orientation6TurnsWideRedLeftImageIntoTallImage() throws Exception {
        byte[] little = withOrientation(wideRedLeftJpeg(), 6, true);
        byte[] big = withOrientation(wideRedLeftJpeg(), 6, false);
        assertEquals(6, ImageOrientation.orientation(little));
        assertEquals(6, ImageOrientation.orientation(big));

        BufferedImage upright = ImageIO.read(new ByteArrayInputStream(ImageOrientation.storedBytes(little, false)));
        assertTrue(upright.getHeight() > upright.getWidth());
        assertTrue(mostlyRed(upright.getRGB(upright.getWidth() / 2, 1)));
        assertTrue(mostlyBlue(upright.getRGB(upright.getWidth() / 2, upright.getHeight() - 2)));
        assertEquals(1, ImageOrientation.orientation(ImageOrientation.storedBytes(big, false)));
    }

    @Test
    void logoSquareSideEqualsMaxOfWidthAndHeight() throws Exception {
        BufferedImage wide = new BufferedImage(6, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 6; x++) {
                wide.setRGB(x, y, 0xFF0000);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(wide, "png", png));
        byte[] stored = ImageOrientation.storedBytes(png.toByteArray(), true);
        assertTrue(ImageOrientation.isPng(stored));
        BufferedImage square = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(6, square.getWidth());
        assertEquals(6, square.getHeight());
        assertEquals(0, square.getRGB(0, 0) >>> 24);
        assertTrue(mostlyRed(square.getRGB(3, 2)));
    }

    @Test
    void unreadableBytesPassThrough() {
        byte[] raw = new byte[] {1, 2, 3, 4, 5};
        assertSame(raw, ImageOrientation.storedBytes(raw, false));
        assertSame(raw, ImageOrientation.storedBytes(raw, true));
    }

    @Test
    void applyCopiesOriginalAsideBeforeRewriteAndLeavesGallery() throws Exception {
        Path parent = Files.createTempDirectory("orient-parent");
        try {
            Path media = parent.resolve("media");
            Path logos = media.resolve("logos");
            Files.createDirectories(logos);
            byte[] tagged = withOrientation(wideRedLeftJpeg(), 6, true);
            Path crest = logos.resolve("crest.jpg");
            Files.write(crest, tagged);
            Path sidecar = logos.resolve("crest.__v128.jpg");
            Files.write(sidecar, tagged);
            Path gallery = media.resolve("gallery");
            Files.createDirectories(gallery);
            Path slide = gallery.resolve("slide.jpg");
            Files.write(slide, tagged);

            PrintStream quiet = new PrintStream(OutputStream.nullOutputStream());
            assertEquals(0, MediaOrientBackup.run(new String[] {media.toString()}, quiet));
            assertArrayEquals(tagged, Files.readAllBytes(crest));
            assertEquals(1, Files.list(parent).count());

            assertEquals(0, MediaOrientBackup.run(new String[] {"--apply", media.toString()}, quiet));
            BufferedImage upright = ImageIO.read(crest.toFile());
            assertTrue(upright.getHeight() > upright.getWidth());
            assertArrayEquals(tagged, Files.readAllBytes(slide));
            assertTrue(Files.notExists(sidecar));
            try (var children = Files.list(parent)) {
                Path backup = children.filter(path -> path.getFileName().toString().startsWith("media-backup-")).findFirst().orElseThrow();
                assertArrayEquals(tagged, Files.readAllBytes(backup.resolve("logos/crest.jpg")));
                assertArrayEquals(tagged, Files.readAllBytes(backup.resolve("logos/crest.__v128.jpg")));
            }
        } finally {
            try (var walk = Files.walk(parent)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // temp dir cleanup
                    }
                });
            }
        }
    }

    private static byte[] wideRedLeftJpeg() throws Exception {
        BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                image.setRGB(x, y, x < 4 ? 0xFF0000 : 0x0000FF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", out));
        return out.toByteArray();
    }

    private static byte[] withOrientation(byte[] jpeg, int orientation, boolean little) {
        byte[] app1 = exifApp1(orientation, little);
        byte[] out = new byte[jpeg.length + app1.length];
        out[0] = jpeg[0];
        out[1] = jpeg[1];
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }

    private static byte[] exifApp1(int orientation, boolean little) {
        byte[] tiff = little
                ? new byte[] {
                    'I', 'I', 0x2A, 0x00,
                    0x08, 0x00, 0x00, 0x00,
                    0x01, 0x00,
                    0x12, 0x01,
                    0x03, 0x00,
                    0x01, 0x00, 0x00, 0x00,
                    (byte) orientation, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00
                }
                : new byte[] {
                    'M', 'M', 0x00, 0x2A,
                    0x00, 0x00, 0x00, 0x08,
                    0x00, 0x01,
                    0x01, 0x12,
                    0x00, 0x03,
                    0x00, 0x00, 0x00, 0x01,
                    0x00, (byte) orientation, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00
                };
        byte[] header = new byte[] {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        byte[] app1 = new byte[2 + length];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) ((length >> 8) & 0xFF);
        app1[3] = (byte) (length & 0xFF);
        System.arraycopy(header, 0, app1, 4, header.length);
        System.arraycopy(tiff, 0, app1, 4 + header.length, tiff.length);
        return app1;
    }

    private static boolean mostlyRed(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 180 && green < 80 && blue < 80;
    }

    private static boolean mostlyBlue(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return blue > 180 && red < 80 && green < 80;
    }
}
