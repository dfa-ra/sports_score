package com.studentleague.storage;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaDeliveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void mediaIsCachedForAYearAndJsonApiStaysNoStore() throws Exception {
        byte[] original = wideJpeg();
        String token = registerAndLogin("avatar-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String url = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/uploads/players/me/avatar")
                        .file(new MockMultipartFile("file", "portrait.jpg", "image/jpeg", original))
                        .header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("url").asText();

        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(served.length).isLessThan(original.length);
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(served));
        assertThat(image.getWidth()).isLessThanOrEqualTo(128);
        assertThat(image.getHeight()).isLessThanOrEqualTo(128);
        assertThat(image.getWidth()).isEqualTo(128);
        assertThat(image.getHeight()).isEqualTo(64);

        Path root = Path.of(environment.getProperty("app.local-storage.root-dir", "./data/uploads"));
        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path stored = root.resolve("avatars").resolve(filename);
        assertThat(Files.size(stored)).isEqualTo(original.length);
        Path variant = stored.resolveSibling(filename.replace(".jpg", ".__v128.jpg"));
        assertThat(Files.isRegularFile(variant)).isTrue();

        Files.delete(variant);
        byte[] lazy = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(Files.isRegularFile(variant)).isTrue();
        assertThat(lazy.length).isLessThan(original.length);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get("/api/v1/statistics/board"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("31536000"))));
    }

    private static byte[] wideJpeg() throws Exception {
        BufferedImage image = new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                graphics.setColor(new Color((y * 3) % 256, 40, (y * 5) % 256));
                graphics.drawLine(0, y, image.getWidth() - 1, y);
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
