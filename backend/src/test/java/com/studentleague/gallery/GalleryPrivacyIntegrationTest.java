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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GalleryPrivacyIntegrationTest extends AbstractIntegrationTest {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.local-storage.root-dir", () -> uploads.toString());
    }

    @Test
    void publicGalleryAndMediaHideDisabledPhotos() throws Exception {
        String admin = createAdminAndLogin("gal-hide-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String stamp = String.valueOf(System.nanoTime());
        String heroTitle = "Герой " + stamp;
        String storyTitle = "Сюжет " + stamp;
        String hiddenHero = "Скрытый герой " + stamp;
        String hiddenShot = "Скрытый кадр " + stamp;

        String heroUrl = upload(admin, "hero.jpg");
        String hiddenUrl = upload(admin, "hidden.jpg");

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slide(heroUrl, heroTitle, "HERO", true)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slide("/media/gallery/story-" + stamp + ".jpg", storyTitle, "STORY", true)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slide("/media/gallery/off-hero-" + stamp + ".jpg", hiddenHero, "HERO", false)))
                .andExpect(status().isCreated());

        String hiddenId = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slide(hiddenUrl, hiddenShot, "GALLERY", false)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[?(@.title=='" + heroTitle + "')]").isNotEmpty())
                .andExpect(jsonPath("$.photos[?(@.title=='" + hiddenShot + "')]").isEmpty())
                .andExpect(jsonPath("$.photos[?(@.title=='" + hiddenHero + "')]").isEmpty())
                .andExpect(jsonPath("$.photos[?(@.enabled==false)]").isEmpty());

        mockMvc.perform(get("/api/v1/admin/gallery").header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[?(@.title=='" + hiddenShot + "')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroes[?(@.title=='" + heroTitle + "')]").isNotEmpty())
                .andExpect(jsonPath("$.heroes[?(@.title=='" + hiddenHero + "')]").isEmpty())
                .andExpect(jsonPath("$.stories[?(@.title=='" + storyTitle + "')]").isNotEmpty())
                .andExpect(jsonPath("$.photos[?(@.title=='" + hiddenShot + "')]").isEmpty());

        mockMvc.perform(get(heroUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));

        mockMvc.perform(get(hiddenUrl))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/gallery/" + hiddenId + "/file")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/media/gallery/does-not-exist-" + stamp + ".jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String upload(String admin, String name) throws Exception {
        return objectMapper.readTree(mockMvc.perform(multipart("/api/v1/uploads/admin/gallery")
                        .file(new MockMultipartFile("file", name, "image/jpeg", JPEG))
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("url").asText();
    }

    private static String slide(String url, String title, String slot, boolean enabled) {
        return """
                {"url":"%s","title":"%s","slot":"%s","enabled":%s,"source":"URL"}
                """.formatted(url, title, slot, enabled);
    }
}
