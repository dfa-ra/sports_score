package com.studentleague.storage;

import com.studentleague.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOrientTest {

    @TempDir
    Path dir;

    @Test
    void uprightAndPngBytesAreLeftAlone() throws Exception {
        byte[] jpeg = jpeg(80, 40);
        assertThat(ImageOrient.readOrientation(jpeg)).isEqualTo(1);
        assertThat(ImageOrient.apply(jpeg)).isSameAs(jpeg);
        assertThat(ImageOrient.apply(ImageUploadsTest.PNG)).isSameAs(ImageUploadsTest.PNG);
        assertThat(ImageOrient.readOrientation(exifOnly(6, true))).isEqualTo(6);
        assertThat(ImageOrient.readOrientation(exifOnly(8, false))).isEqualTo(8);
    }

    @Test
    void phoneJpegIsStoredUprightForLogosAndAvatars() throws Exception {
        byte[] sideways = withOrientation(jpeg(80, 40), 6);
        assertThat(ImageOrient.readOrientation(sideways)).isEqualTo(6);

        byte[] upright = ImageOrient.apply(sideways);
        assertThat(upright).isNotSameAs(sideways);
        assertThat(ImageOrient.readOrientation(upright)).isEqualTo(1);
        assertThat(ImageOrient.apply(upright)).isSameAs(upright);
        BufferedImage turned = ImageIO.read(new java.io.ByteArrayInputStream(upright));
        assertThat(turned.getWidth()).isEqualTo(40);
        assertThat(turned.getHeight()).isEqualTo(80);
        assertThat(redderThanBlue(turned.getRGB(turned.getWidth() / 2, turned.getHeight() / 4))).isTrue();
        assertThat(bluerThanRed(turned.getRGB(turned.getWidth() / 2, turned.getHeight() * 3 / 4))).isTrue();

        AppProperties properties = new AppProperties(
                null, null, null, null,
                new AppProperties.LocalStorage(dir.toString(), "/media"),
                null, null, null, null, null, null
        );
        LocalDiskStorageService storage = new LocalDiskStorageService(properties);
        String logoUrl = storage.store("logos", new MockMultipartFile("file", "crest.jpg", "image/jpeg", sideways));
        String avatarUrl = storage.store("avatars", new MockMultipartFile("file", "face.jpg", "image/jpeg", sideways));

        assertStoredUpright(logoUrl);
        assertStoredUpright(avatarUrl);
    }

    @Test
    void variantOfAnOldSidewaysFileIsUpright() throws Exception {
        byte[] sideways = withOrientation(jpeg(80, 40), 6);
        Path original = dir.resolve("old.jpg");
        Files.write(original, sideways);

        Path served = ImageVariants.servedFile(original, "logos/old.jpg");

        assertThat(served).isNotEqualTo(original);
        BufferedImage image = ImageIO.read(served.toFile());
        assertThat(image.getWidth()).isLessThanOrEqualTo(128);
        assertThat(image.getHeight()).isGreaterThan(image.getWidth());
        assertThat(redderThanBlue(image.getRGB(image.getWidth() / 2, image.getHeight() / 4))).isTrue();
        assertThat(Files.readAllBytes(original)).isEqualTo(sideways);
    }

    @Test
    void rewriteTreeFixesSidewaysJpegAndLeavesEverythingElse() throws Exception {
        Path media = dir.resolve("media");
        Files.createDirectories(media.resolve("logos"));
        Path notes = media.resolve("notes.txt");
        Files.writeString(notes, "keep");
        Path plain = media.resolve("logos").resolve("plain.jpg");
        byte[] plainBytes = jpeg(30, 20);
        Files.write(plain, plainBytes);
        Path sideways = media.resolve("logos").resolve("bubis.jpg");
        Files.write(sideways, withOrientation(jpeg(80, 40), 6));
        Path sidecar = media.resolve("logos").resolve("bubis.__v128.jpg");
        Files.write(sidecar, new byte[] {1, 2, 3});
        Path skip = media.resolve("logos").resolve("bubis.__v128.jpg.skip");
        Files.write(skip, new byte[0]);

        ImageOrient.Summary first = ImageOrient.rewriteTree(media);

        assertThat(first.rewritten()).isEqualTo(1);
        assertThat(first.failed()).isZero();
        assertThat(Files.readString(notes)).isEqualTo("keep");
        assertThat(Files.readAllBytes(plain)).isEqualTo(plainBytes);
        assertThat(Files.exists(sidecar)).isFalse();
        assertThat(Files.exists(skip)).isFalse();
        assertThat(ImageOrient.readOrientation(Files.readAllBytes(sideways))).isEqualTo(1);
        BufferedImage fixed = ImageIO.read(sideways.toFile());
        assertThat(fixed.getWidth()).isEqualTo(40);
        assertThat(fixed.getHeight()).isEqualTo(80);

        ImageOrient.Summary second = ImageOrient.rewriteTree(media);
        assertThat(second.rewritten()).isZero();
        assertThat(second.failed()).isZero();
        assertThat(ImageIO.read(sideways.toFile()).getWidth()).isEqualTo(40);
    }

    private void assertStoredUpright(String url) throws Exception {
        String name = url.substring(url.lastIndexOf('/') + 1);
        String folder = url.contains("/avatars/") ? "avatars" : "logos";
        Path stored = dir.resolve(folder).resolve(name);
        assertThat(ImageOrient.readOrientation(Files.readAllBytes(stored))).isEqualTo(1);
        BufferedImage image = ImageIO.read(stored.toFile());
        assertThat(image.getHeight()).isGreaterThan(image.getWidth());
        assertThat(redderThanBlue(image.getRGB(image.getWidth() / 2, image.getHeight() / 4))).isTrue();
    }

    private static boolean redderThanBlue(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > color.getBlue() + 40;
    }

    private static boolean bluerThanRed(int rgb) {
        Color color = new Color(rgb);
        return color.getBlue() > color.getRed() + 40;
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(220, 20, 20));
            graphics.fillRect(0, 0, width / 2, height);
            graphics.setColor(new Color(20, 20, 220));
            graphics.fillRect(width / 2, 0, width - width / 2, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = exifApp1(orientation, true);
        byte[] out = new byte[jpeg.length + app1.length];
        out[0] = jpeg[0];
        out[1] = jpeg[1];
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }

    private static byte[] exifOnly(int orientation, boolean little) {
        byte[] app1 = exifApp1(orientation, little);
        byte[] out = new byte[2 + app1.length];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        System.arraycopy(app1, 0, out, 2, app1.length);
        return out;
    }

    /** Minimal EXIF APP1 whose only IFD0 tag is Orientation. */
    private static byte[] exifApp1(int orientation, boolean little) {
        byte[] tiff = new byte[8 + 2 + 12 + 4];
        if (little) {
            tiff[0] = 'I';
            tiff[1] = 'I';
            put16(tiff, 2, 42, true);
            put32(tiff, 4, 8, true);
            put16(tiff, 8, 1, true);
            put16(tiff, 10, 0x0112, true);
            put16(tiff, 12, 3, true);
            put32(tiff, 14, 1, true);
            put16(tiff, 18, orientation, true);
        } else {
            tiff[0] = 'M';
            tiff[1] = 'M';
            put16(tiff, 2, 42, false);
            put32(tiff, 4, 8, false);
            put16(tiff, 8, 1, false);
            put16(tiff, 10, 0x0112, false);
            put16(tiff, 12, 3, false);
            put32(tiff, 14, 1, false);
            put16(tiff, 18, orientation, false);
        }
        byte[] payload = new byte[6 + tiff.length];
        payload[0] = 'E';
        payload[1] = 'x';
        payload[2] = 'i';
        payload[3] = 'f';
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        byte[] app1 = new byte[4 + payload.length];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        int length = payload.length + 2;
        app1[2] = (byte) ((length >> 8) & 0xFF);
        app1[3] = (byte) (length & 0xFF);
        System.arraycopy(payload, 0, app1, 4, payload.length);
        return app1;
    }

    private static void put16(byte[] data, int offset, int value, boolean little) {
        if (little) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        } else {
            data[offset] = (byte) ((value >> 8) & 0xFF);
            data[offset + 1] = (byte) (value & 0xFF);
        }
    }

    private static void put32(byte[] data, int offset, int value, boolean little) {
        if (little) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
            data[offset + 2] = (byte) ((value >> 16) & 0xFF);
            data[offset + 3] = (byte) ((value >> 24) & 0xFF);
        } else {
            data[offset] = (byte) ((value >> 24) & 0xFF);
            data[offset + 1] = (byte) ((value >> 16) & 0xFF);
            data[offset + 2] = (byte) ((value >> 8) & 0xFF);
            data[offset + 3] = (byte) (value & 0xFF);
        }
    }
}
