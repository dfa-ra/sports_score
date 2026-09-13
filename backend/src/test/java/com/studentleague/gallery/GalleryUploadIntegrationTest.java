package com.studentleague.gallery;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static com.studentleague.storage.ImageUploadsTest.JPEG;
import static com.studentleague.storage.ImageUploadsTest.PNG;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GalleryUploadIntegrationTest extends AbstractIntegrationTest {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.local-storage.root-dir", () -> uploads.toString());
    }

    @Test
    void adminCanUploadPngAndJpegForCarousel() throws Exception {
        String admin = createAdminAndLogin("gal-up-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        String pngUrl = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/uploads/admin/gallery")
                        .file(new MockMultipartFile("file", "slide.PNG", "application/octet-stream", PNG))
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".png")))
                .andReturn()
                .getResponse()
                .getContentAsString()).get("url").asText();

        mockMvc.perform(multipart("/api/v1/uploads/admin/gallery")
                        .file(new MockMultipartFile("file", "hero.jpg", "image/jpeg", JPEG))
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString(".jpg")));

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"%s","title":"PNG в карусели","slot":"HERO","source":"UPLOAD"}
                                """.formatted(pngUrl)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroes[?(@.title=='PNG в карусели')].url").value(org.hamcrest.Matchers.hasItem(pngUrl)));
    }

    @Test
    void storyWithoutLinkStillPublished() throws Exception {
        String admin = createAdminAndLogin("story-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"/media/gallery/event.png","title":"Событие без ссылки","slot":"STORY","source":"URL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories[?(@.title=='Событие без ссылки')]").isNotEmpty());
    }

    @Test
    void rejectsSvgForGallery() throws Exception {
        String admin = createAdminAndLogin("svg-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        mockMvc.perform(multipart("/api/v1/uploads/admin/gallery")
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes()))
                        .header("Authorization", auth(admin)))
                .andExpect(status().isBadRequest());
    }
}
