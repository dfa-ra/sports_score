package com.studentleague.seo;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicSeoIntegrationTest extends AbstractIntegrationTest {

    @Test
    void robotsAndSitemapAreRealFiles() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("User-agent: *")))
                .andExpect(content().string(containsString("Sitemap:")))
                .andExpect(content().string(containsString("/sitemap.xml")))
                .andExpect(content().string(not(containsString("<div id=\"app\">"))));

        mockMvc.perform(get("/sitemap.xml").header("Host", "itmoliga.ru").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("https://itmoliga.ru/tournaments")))
                .andExpect(content().string(containsString("https://itmoliga.ru/calendar")));
    }

    @Test
    void linkPreviewUsesTheRouteAndRejectsUnknownPaths() throws Exception {
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/table")
                        .header("Host", "itmoliga.ru")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<h1>Таблица — Лига ИТМО</h1>")))
                .andExpect(content().string(containsString("og:title")))
                .andExpect(content().string(containsString("https://itmoliga.ru/table")));

        mockMvc.perform(get("/api/v1/link-preview").header("X-Preview-Path", "/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Такой страницы нет")));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/matches/not-a-uuid"))
                .andExpect(status().isNotFound());
    }
}
