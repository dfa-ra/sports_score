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

    @Test
    void descriptionsUseTheGenitiveLeagueNameAndTitlesStayNominative() throws Exception {
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/")
                        .header("Host", "itmoliga.ru").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Главная — Лига ИТМО</title>")))
                .andExpect(content().string(containsString("лучшие по передачам Лиги ИТМО.")))
                .andExpect(content().string(not(containsString("передач Лига ИТМО"))));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/players"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Игроки — Лига ИТМО</h1>")))
                .andExpect(content().string(containsString("Поиск игроков Лиги ИТМО.")));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/calendar"))
                .andExpect(content().string(containsString("Календарь матчей Лиги ИТМО.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/tournaments"))
                .andExpect(content().string(containsString("Турниры Лиги ИТМО: вид спорта, сезон и формат.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/teams"))
                .andExpect(content().string(containsString("Команды Лиги ИТМО.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/terms"))
                .andExpect(content().string(containsString("Условия использования сайта Лиги ИТМО.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/privacy"))
                .andExpect(content().string(containsString("Как Лига ИТМО обрабатывает персональные данные.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/table"))
                .andExpect(content().string(containsString("лидеры по передачам.")))
                .andExpect(content().string(containsString("<title>Таблица — Лига ИТМО</title>")));
    }
}
