package com.studentleague.seo;

import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.support.AbstractIntegrationTest;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

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

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private SportRepository sportRepository;

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
                .andExpect(content().string(containsString("<title>Таблица — Лига ИТМО</title>")))
                .andExpect(content().string(containsString(
                        "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/live"))
                .andExpect(content().string(containsString("Матчи, которые идут сейчас.")));
    }

    @Test
    void staticSpaRoutesPreviewForBotsAndUnknownPathsStayMissing() throws Exception {
        expectPreview("/login", "<title>Вход — Лига ИТМО</title>", "Лига ИТМО: вход");
        expectPreview("/login?redirect=/profile", "<title>Вход — Лига ИТМО</title>", "Лига ИТМО: вход");
        expectPreview("/register", "<h1>Регистрация — Лига ИТМО</h1>", "Лига ИТМО: регистрация");
        expectPreview("/forgot-password", "<title>Восстановление пароля — Лига ИТМО</title>", "Лига ИТМО: восстановление пароля");
        expectPreview("/reset-password", "<title>Новый пароль — Лига ИТМО</title>", "Лига ИТМО: новый пароль");
        expectPreview("/my-team", "<title>Моя команда — Лига ИТМО</title>", "Лига ИТМО: моя команда");
        expectPreview("/profile", "<title>Профиль — Лига ИТМО</title>", "Лига ИТМО: профиль");
        expectPreview("/admin", "<title>Админ — Лига ИТМО</title>", "Лига ИТМО: управление лигой");
        expectPreview("/referee", "<title>Судья — Лига ИТМО</title>", "Лига ИТМО: матчи судьи");
        expectPreview("/analyst", "<title>Аналитик — Лига ИТМО</title>", "Лига ИТМО: матчи аналитика");
        expectPreview("/forbidden", "<title>Недостаточно прав — Лига ИТМО</title>", "Лига ИТМО: недостаточно прав");
        expectPreview("/matches", "<title>Игры — Лига ИТМО</title>", "Лига ИТМО: календарь матчей");
        expectPreview("/statistics", "<title>Таблица — Лига ИТМО</title>",
                "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам");

        mockMvc.perform(get("/api/v1/link-preview").header("X-Preview-Path", "/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Такой страницы нет")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/matches/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/referee/matches/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void matchPreviewNamesTheTeamsAndOmitsAnUnplayedScore() throws Exception {
        Team home = new Team();
        home.setName("Физтех");
        home = teamRepository.saveAndFlush(home);
        Team away = new Team();
        away.setName("Архитекторы");
        away = teamRepository.saveAndFlush(away);
        var sport = sportRepository.findAll().get(0);
        Tournament tournament = new Tournament();
        tournament.setName("SEO");
        tournament.setSportId(sport.getId());
        tournament.setSeasonYear(2026);
        tournament.setStatus(TournamentStatus.REGISTRATION);
        tournament.setFormat("ROUND_ROBIN");
        tournament = tournamentRepository.saveAndFlush(tournament);

        Match played = match(tournament.getId(), sport.getId(), home.getId(), away.getId(), MatchStatus.FINISHED, 2, 1);
        Match scheduled = match(tournament.getId(), sport.getId(), home.getId(), away.getId(), MatchStatus.SCHEDULED, 0, 0);

        String playedText = "Лига ИТМО: матч Физтех — Архитекторы, счёт 2:1";
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/matches/" + played.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Физтех — Архитекторы — Лига ИТМО</title>")))
                .andExpect(content().string(containsString(playedText)))
                .andExpect(content().string(containsString("property=\"og:title\" content=\"Физтех — Архитекторы — Лига ИТМО\"")));
        mockMvc.perform(get("/api/v1/link-preview").param("path", "/referee/matches/" + played.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(playedText)));

        mockMvc.perform(get("/api/v1/link-preview").param("path", "/matches/" + scheduled.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лига ИТМО: матч Физтех — Архитекторы")))
                .andExpect(content().string(not(containsString("счёт"))));
    }

    private Match match(UUID tournamentId, UUID sportId, UUID homeId, UUID awayId, MatchStatus status, int homeScore, int awayScore) {
        Match match = new Match();
        match.setTournamentId(tournamentId);
        match.setSportId(sportId);
        match.setHomeTeamId(homeId);
        match.setAwayTeamId(awayId);
        match.setScheduledAt(Instant.parse("2026-11-03T12:00:00Z"));
        match.setStatus(status);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return matchRepository.saveAndFlush(match);
    }

    private void expectPreview(String path, String title, String description) throws Exception {
        mockMvc.perform(get("/api/v1/link-preview").header("X-Preview-Path", path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"" + description + "\">")))
                .andExpect(content().string(containsString("<meta property=\"og:description\" content=\"" + description + "\">")));
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
