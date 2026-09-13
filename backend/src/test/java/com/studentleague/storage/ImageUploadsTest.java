package com.studentleague.storage;

import com.studentleague.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ImageUploadsTest {

    public static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
            0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00,
            0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, (byte) 0xFE,
            (byte) 0xD4, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
            0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    public static final byte[] JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    @Test
    void acceptsPngEvenWhenBrowserSendsOctetStream() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "moment.PNG", "application/octet-stream", PNG);
        ImageUploads.requireRasterImage(file);
        assertThat(ImageUploads.safeFilename(file)).isEqualTo("moment.png");
    }

    @Test
    void acceptsJpegAndJpg() {
        ImageUploads.requireRasterImage(new MockMultipartFile(
                "file", "hero.jpeg", "image/jpeg", JPEG));
        ImageUploads.requireRasterImage(new MockMultipartFile(
                "file", "hero.jpg", "image/jpg", JPEG));
        assertThat(ImageUploads.safeFilename(new MockMultipartFile(
                "file", "hero.jpeg", "image/jpeg", JPEG))).isEqualTo("hero.jpg");
    }

    @Test
    void rejectsSvgAndEmpty() {
        assertThatThrownBy(() -> ImageUploads.requireRasterImage(new MockMultipartFile(
                "file", "x.svg", "image/svg+xml", "<svg></svg>".getBytes())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ImageUploads.requireRasterImage(new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(ApiException.class);
    }
}
