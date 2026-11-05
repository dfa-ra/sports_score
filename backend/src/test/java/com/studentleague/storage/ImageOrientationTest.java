package com.studentleague.storage;

import com.studentleague.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOrientationTest {

    @TempDir
    Path dir;

    @Test
    void phoneJpegTaggedRotate90IsStoredUpright() throws Exception {
        byte[] sideways = jpeg(halfAndHalf(40, 20), 6);
        assertThat(ImageOrientation.jpegOrientation(sideways)).isEqualTo(6);

        ImageOrientation.Prepared prepared = ImageOrientation.prepare(sideways, "avatars", true);

        assertThat(prepared.changed()).isTrue();
        assertThat(prepared.extension()).isEqualTo("jpg");
        BufferedImage upright = ImageIO.read(new java.io.ByteArrayInputStream(prepared.bytes()));
        assertThat(upright.getWidth()).isEqualTo(20);
        assertThat(upright.getHeight()).isEqualTo(40);
        assertThat(redness(upright.getRGB(10, 4))).isGreaterThan(180);
        assertThat(blueness(upright.getRGB(10, 4))).isLessThan(80);
        assertThat(blueness(upright.getRGB(10, 36))).isGreaterThan(180);
        assertThat(ImageOrientation.jpegOrientation(prepared.bytes())).isEqualTo(1);
    }

    @Test
    void rotate270AndAlreadyUprightFiles() throws Exception {
        byte[] sideways = jpeg(halfAndHalf(40, 20), 8);
        ImageOrientation.Prepared prepared = ImageOrientation.prepare(sideways, "registration", true);
        BufferedImage upright = ImageIO.read(new java.io.ByteArrayInputStream(prepared.bytes()));
        assertThat(upright.getWidth()).isEqualTo(20);
        assertThat(upright.getHeight()).isEqualTo(40);
        assertThat(blueness(upright.getRGB(10, 4))).isGreaterThan(180);
        assertThat(redness(upright.getRGB(10, 36))).isGreaterThan(180);

        byte[] plain = jpeg(halfAndHalf(40, 20), 1);
        ImageOrientation.Prepared same = ImageOrientation.prepare(plain, "avatars", true);
        assertThat(same.changed()).isFalse();
        assertThat(same.bytes()).isEqualTo(plain);
    }

    @Test
    void logoIsPaddedToASquareTransparentCanvasAndGalleryIsLeftAlone() throws Exception {
        BufferedImage strip = new BufferedImage(8, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            strip.setRGB(x, 0, 0xFFFF0000);
            strip.setRGB(x, 1, 0xFFFF0000);
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(strip, "png", png);

        ImageOrientation.Prepared logo = ImageOrientation.prepare(png.toByteArray(), "logos", true);
        assertThat(logo.changed()).isTrue();
        assertThat(logo.extension()).isEqualTo("png");
        BufferedImage square = ImageIO.read(new java.io.ByteArrayInputStream(logo.bytes()));
        assertThat(square.getWidth()).isEqualTo(8);
        assertThat(square.getHeight()).isEqualTo(8);
        assertThat(square.getRGB(0, 0) >>> 24).isZero();
        assertThat(redness(square.getRGB(4, 3))).isGreaterThan(200);

        byte[] gallery = jpeg(halfAndHalf(40, 20), 6);
        ImageOrientation.Prepared skipped = ImageOrientation.prepare(gallery, "gallery", true);
        assertThat(skipped.changed()).isFalse();
        assertThat(skipped.bytes()).isEqualTo(gallery);
    }

    @Test
    void uploadStoresUprightAvatarPixels() throws Exception {
        LocalDiskStorageService storage = new LocalDiskStorageService(properties(dir));
        MockMultipartFile file = new MockMultipartFile(
                "file", "phone.jpg", "image/jpeg", jpeg(halfAndHalf(40, 20), 6));

        String url = storage.store("avatars", file);

        assertThat(url).startsWith("/media/avatars/");
        Path stored = dir.resolve(url.substring("/media/".length()));
        BufferedImage image = ImageIO.read(stored.toFile());
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(40);
        assertThat(redness(image.getRGB(10, 4))).isGreaterThan(180);
    }

    @Test
    void backfillRewritesTaggedMediaInPlaceAndIsOptIn() throws Exception {
        ConditionalOnProperty gate = MediaOrientBackfillRunner.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(gate).isNotNull();
        assertThat(gate.name()).containsExactly("app.media.orient-backfill");
        assertThat(gate.havingValue()).isEqualTo("true");
        assertThat(gate.matchIfMissing()).isFalse();
        assertThat(MediaOrient.DEV_ONLY).isEqualTo("dev only until Захар says да + backup");

        Path logos = Files.createDirectories(dir.resolve("logos"));
        Path gallery = Files.createDirectories(dir.resolve("gallery"));
        Path sideways = logos.resolve("side.jpg");
        Path upright = logos.resolve("ok.jpg");
        Path story = gallery.resolve("story.jpg");
        Files.write(sideways, jpeg(halfAndHalf(40, 20), 6));
        byte[] plain = jpeg(halfAndHalf(16, 16), 1);
        Files.write(upright, plain);
        byte[] storyBytes = jpeg(halfAndHalf(40, 20), 6);
        Files.write(story, storyBytes);
        Path sidecar = logos.resolve("side.__v128.jpg");
        Files.write(sidecar, new byte[] {1, 2, 3});

        assertThat(MediaOrient.rewriteTree(dir)).isEqualTo(1);

        BufferedImage fixed = ImageIO.read(sideways.toFile());
        assertThat(fixed.getWidth()).isEqualTo(20);
        assertThat(fixed.getHeight()).isEqualTo(40);
        assertThat(redness(fixed.getRGB(10, 4))).isGreaterThan(180);
        assertThat(Files.readAllBytes(upright)).isEqualTo(plain);
        assertThat(Files.readAllBytes(story)).isEqualTo(storyBytes);
        assertThat(Files.exists(sidecar)).isFalse();

        new MediaOrientBackfillRunner(dir).run(new DefaultApplicationArguments(new String[0]));
        assertThat(ImageIO.read(sideways.toFile()).getWidth()).isEqualTo(20);
    }

    private static AppProperties properties(Path root) {
        return new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-algorithms-123456", 60_000, 3_600_000),
                new AppProperties.RateLimit(30, 5, 15, 10, 15),
                new AppProperties.Redis(false),
                new AppProperties.LocalStorage(root.toString(), "/media"),
                new AppProperties.Admin("", "", null),
                new AppProperties.Auth(false),
                new AppProperties.DemoData(false),
                new AppProperties.Google(""),
                "http://localhost:5173",
                new AppProperties.Mail("", 587, "", "", "", true)
        );
    }

    private static BufferedImage halfAndHalf(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, x < width / 2 ? 0xFFFF0000 : 0xFF0000FF);
            }
        }
        return image;
    }

    private static byte[] jpeg(BufferedImage image, int orientation) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        byte[] raw = out.toByteArray();
        if (orientation == 1) {
            return raw;
        }
        byte[] app = exifApp1(orientation);
        byte[] merged = new byte[raw.length + app.length];
        merged[0] = raw[0];
        merged[1] = raw[1];
        System.arraycopy(app, 0, merged, 2, app.length);
        System.arraycopy(raw, 2, merged, 2 + app.length, raw.length - 2);
        return merged;
    }

    private static byte[] exifApp1(int orientation) {
        byte[] payload = new byte[] {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0,
                0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0,
                0, 0, 0, 0
        };
        int length = payload.length + 2;
        byte[] app = new byte[4 + payload.length];
        app[0] = (byte) 0xFF;
        app[1] = (byte) 0xE1;
        app[2] = (byte) ((length >> 8) & 0xFF);
        app[3] = (byte) (length & 0xFF);
        System.arraycopy(payload, 0, app, 4, payload.length);
        return app;
    }

    private static int redness(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int blueness(int rgb) {
        return rgb & 0xFF;
    }
}
