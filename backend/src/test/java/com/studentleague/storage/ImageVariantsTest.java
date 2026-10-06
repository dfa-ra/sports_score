package com.studentleague.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageVariantsTest {

    @TempDir
    Path dir;

    @Test
    void avatarCopyFitsInside128AndKeepsAspect() throws Exception {
        Path original = dir.resolve("player.png");
        writePng(original, 400, 200);
        long originalBytes = Files.size(original);

        Path served = ImageVariants.servedFile(original, "avatars/player.png");

        assertThat(served).isNotEqualTo(original);
        assertThat(Files.isRegularFile(original)).isTrue();
        assertThat(Files.size(original)).isEqualTo(originalBytes);
        BufferedImage image = ImageIO.read(served.toFile());
        assertThat(image.getWidth()).isEqualTo(128);
        assertThat(image.getHeight()).isEqualTo(64);
        assertThat(Files.size(served)).isLessThan(originalBytes);
    }

    @Test
    void portraitCrestFitsInsideTheBoxWithoutCropping() throws Exception {
        Path original = dir.resolve("crest.png");
        writePng(original, 100, 400);

        Path served = ImageVariants.servedFile(original, "logos/crest.png");

        BufferedImage image = ImageIO.read(served.toFile());
        assertThat(image.getWidth()).isEqualTo(32);
        assertThat(image.getHeight()).isEqualTo(128);
    }

    @Test
    void galleryCopyIsNoWiderThan1600() throws Exception {
        Path original = dir.resolve("hero.png");
        writePng(original, 2000, 1000);
        long originalBytes = Files.size(original);

        Path served = ImageVariants.servedFile(original, "gallery/hero.png");

        BufferedImage image = ImageIO.read(served.toFile());
        assertThat(image.getWidth()).isEqualTo(1600);
        assertThat(image.getHeight()).isEqualTo(800);
        assertThat(Files.size(served)).isLessThan(originalBytes);
        assertThat(Files.size(original)).isEqualTo(originalBytes);
    }

    @Test
    void missingCopyIsCreatedOnceOnRead() throws Exception {
        Path original = dir.resolve("old.png");
        writePng(original, 300, 300);
        Path served = ImageVariants.servedFile(original, "avatars/old.png");
        Files.delete(served);

        Path again = ImageVariants.servedFile(original, "avatars/old.png");

        assertThat(Files.isRegularFile(again)).isTrue();
        assertThat(ImageIO.read(again.toFile()).getWidth()).isLessThanOrEqualTo(128);
        assertThat(ImageIO.read(again.toFile()).getHeight()).isLessThanOrEqualTo(128);
    }

    @Test
    void undecodableFileIsServedAsTheOriginal() throws Exception {
        Path original = dir.resolve("broken.jpg");
        byte[] junk = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4};
        Files.write(original, junk);

        Path served = ImageVariants.servedFile(original, "avatars/broken.jpg");
        Path second = ImageVariants.servedFile(original, "avatars/broken.jpg");

        assertThat(served).isEqualTo(original);
        assertThat(second).isEqualTo(original);
        assertThat(Files.readAllBytes(original)).isEqualTo(junk);
    }

    private static void writePng(Path target, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < height; y++) {
                graphics.setColor(new Color((y * 13) % 256, (y * 7) % 256, 90));
                graphics.drawLine(0, y, width - 1, y);
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }
}
