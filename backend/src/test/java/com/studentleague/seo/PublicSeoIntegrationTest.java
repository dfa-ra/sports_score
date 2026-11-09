package com.studentleague.seo;

import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicSeoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

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
    void descriptionsLeadWithTheLeagueAndTitlesStayNominative() throws Exception {
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/")
                        .header("Host", "itmoliga.ru").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Главная — Лига ИТМО</title>")))
                .andExpect(content().string(containsString("Лига ИТМО: бомбардиры и лучшие по передачам")))
                .andExpect(content().string(not(containsString("Лиги ИТМО"))));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/players"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Игроки — Лига ИТМО</h1>")))
                .andExpect(content().string(containsString("Лига ИТМО: поиск игроков")));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/calendar"))
                .andExpect(content().string(containsString("Лига ИТМО: календарь матчей")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/tournaments"))
                .andExpect(content().string(containsString("Лига ИТМО: турниры, вид спорта, сезон и формат")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/teams"))
                .andExpect(content().string(containsString("Лига ИТМО: команды")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/terms"))
                .andExpect(content().string(containsString("Лига ИТМО: условия использования сайта")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/privacy"))
                .andExpect(content().string(containsString("Как Лига ИТМО обрабатывает персональные данные.")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/table"))
                .andExpect(content().string(containsString("лидеры по передачам.")))
                .andExpect(content().string(not(containsString("Лига ИТМО:"))));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/live"))
                .andExpect(content().string(containsString("Матчи, которые идут сейчас.")));
    }

    @Test
    void playerPreviewUsesTheNameAndOmitsAMissingTeam() throws Exception {
        String email = "seo-player-" + System.nanoTime() + "@example.com";
        registerAndLogin(email, "Str0ngPass!");
        PlayerProfile profile = playerProfileRepository.findByUserId(
                userRepository.findByEmailIgnoreCase(email).orElseThrow().getId()).orElseThrow();

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/players/" + profile.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Студент Тест — Лига ИТМО</title>")))
                .andExpect(content().string(containsString("Игрок Лиги ИТМО: Студент Тест")))
                .andExpect(content().string(not(containsString("Студент Тест,"))));

        Team team = new Team();
        team.setName("Физтех");
        team = teamRepository.saveAndFlush(team);
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setPlayerId(profile.getId());
        member.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.saveAndFlush(member);

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/players/" + profile.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Игрок Лиги ИТМО: Студент Тест, Физтех")));
    }
}
