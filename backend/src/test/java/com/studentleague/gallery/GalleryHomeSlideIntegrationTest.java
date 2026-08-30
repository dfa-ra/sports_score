package com.studentleague.gallery;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GalleryHomeSlideIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminCanPublishHeroSlideToHome() throws Exception {
        String admin = createAdminAndLogin("hero-admin-" + System.nanoTime() + "@example.com", "Str0ngPass!");

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "/media/gallery/hero.jpg",
                                  "title": "Финал сезона",
                                  "caption": "Все дороги ведут на главный матч",
                                  "slot": "HERO",
                                  "linkLabel": "Календарь",
                                  "linkUrl": "/calendar",
                                  "source": "URL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slot").value("HERO"))
                .andExpect(jsonPath("$.title").value("Финал сезона"));

        mockMvc.perform(post("/api/v1/admin/gallery")
                        .header("Authorization", auth(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "/media/gallery/story.jpg",
                                  "title": "Недельный обзор",
                                  "slot": "STORY",
                                  "linkUrl": "/statistics",
                                  "source": "URL"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroes[0].title").value("Финал сезона"))
                .andExpect(jsonPath("$.heroes[0].linkUrl").value("/calendar"))
                .andExpect(jsonPath("$.stories[0].title").value("Недельный обзор"))
                .andExpect(jsonPath("$.photos").isArray());
    }
}
