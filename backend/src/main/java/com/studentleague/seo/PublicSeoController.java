package com.studentleague.seo;

import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.repository.TournamentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class PublicSeoController {

    static final int SITEMAP_LIMIT = 500;
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.-]+(?::\\d+)?$");
    private static final String SITE = "Лига ИТМО";

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public PublicSeoController(
            TournamentRepository tournamentRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            PlayerProfileRepository playerProfileRepository
    ) {
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.playerProfileRepository = playerProfileRepository;
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> robots(HttpServletRequest request) {
        String origin = origin(request);
        String body = """
                User-agent: *
                Allow: /
                Disallow: /admin
                Disallow: /referee
                Disallow: /analyst
                Disallow: /profile
                Disallow: /my-team
                Disallow: /login
                Disallow: /register
                Disallow: /forgot-password
                Disallow: /reset-password

                Sitemap: %s/sitemap.xml
                """.formatted(origin);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String origin = origin(request);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : List.of(
                "/", "/table", "/calendar", "/live", "/tournaments", "/teams", "/players", "/privacy", "/terms"
        )) {
            appendUrl(xml, origin, path);
        }
        for (Tournament tournament : tournamentRepository.findAll(PageRequest.of(0, SITEMAP_LIMIT))) {
            appendUrl(xml, origin, "/tournaments/" + tournament.getId());
        }
        for (Team team : teamRepository.findByDisbandedFalse(PageRequest.of(0, SITEMAP_LIMIT))) {
            appendUrl(xml, origin, "/teams/" + team.getId());
        }
        PageRequest matches = PageRequest.of(0, SITEMAP_LIMIT, Sort.by(Sort.Direction.DESC, "scheduledAt"));
        for (Match match : matchRepository.findAll(matches)) {
            appendUrl(xml, origin, "/matches/" + match.getId());
        }
        PageRequest players = PageRequest.of(0, SITEMAP_LIMIT, Sort.by(Sort.Direction.ASC, "lastName"));
        for (PlayerProfile profile : playerProfileRepository.findAll(players)) {
            appendUrl(xml, origin, "/players/" + profile.getId());
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .body(xml.toString());
    }

    @GetMapping(value = "/api/v1/link-preview", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> preview(
            HttpServletRequest request,
            @RequestHeader(value = "X-Preview-Path", required = false) String headerPath,
            @RequestParam(value = "path", required = false) String queryPath
    ) {
        String raw = headerPath != null && !headerPath.isBlank() ? headerPath : queryPath;
        String path = normalize(raw);
        if (path == null) {
            return html(HttpStatus.NOT_FOUND, request, "/404", "Такой страницы нет", "Страница не найдена.");
        }
        PageCopy copy = copyFor(path);
        if (copy == null) {
            return html(HttpStatus.NOT_FOUND, request, path, "Такой страницы нет", "Страница не найдена.");
        }
        return html(HttpStatus.OK, request, path, copy.title(), copy.description());
    }

    private PageCopy copyFor(String path) {
        if ("/".equals(path)) {
            return new PageCopy("Главная", "Таблица, ближайшие матчи и бомбардиры " + SITE + ".");
        }
        return switch (path) {
            case "/table" -> new PageCopy("Таблица", "Турнирная таблица, результаты, бомбардиры и ассистенты.");
            case "/calendar" -> new PageCopy("Игры", "Календарь матчей " + SITE + ".");
            case "/live" -> new PageCopy("Live", "Матчи, которые идут сейчас.");
            case "/tournaments" -> new PageCopy("Турниры", "Турниры " + SITE + ": вид спорта, сезон и формат.");
            case "/teams" -> new PageCopy("Команды", "Команды " + SITE + ".");
            case "/players" -> new PageCopy("Игроки", "Поиск игроков " + SITE + ".");
            case "/privacy" -> new PageCopy("Политика конфиденциальности", "Как " + SITE + " обрабатывает персональные данные.");
            case "/terms" -> new PageCopy("Условия пользования", "Условия использования сайта " + SITE + ".");
            default -> dynamicCopy(path);
        };
    }

    private PageCopy dynamicCopy(String path) {
        UUID id = idAfter(path, "/tournaments/");
        if (id != null) {
            return tournamentRepository.findById(id)
                    .map(tournament -> new PageCopy(tournament.getName(), tournament.getName() + " — турнир " + SITE + "."))
                    .orElse(null);
        }
        id = idAfter(path, "/teams/");
        if (id != null) {
            return teamRepository.findById(id)
                    .filter(team -> !team.isDisbanded())
                    .map(team -> new PageCopy(team.getName(), team.getName() + " — команда " + SITE + "."))
                    .orElse(null);
        }
        id = idAfter(path, "/players/");
        if (id != null) {
            return playerProfileRepository.findById(id)
                    .map(profile -> {
                        String name = playerName(profile);
                        return new PageCopy(name, name + " — игрок " + SITE + ".");
                    })
                    .orElse(null);
        }
        id = idAfter(path, "/matches/");
        if (id != null) {
            return matchRepository.findById(id)
                    .map(this::matchCopy)
                    .orElse(null);
        }
        return null;
    }

    private PageCopy matchCopy(Match match) {
        String home = teamRepository.findById(match.getHomeTeamId()).map(Team::getName).orElse("Хозяева");
        String away = teamRepository.findById(match.getAwayTeamId()).map(Team::getName).orElse("Гости");
        String title = home + " — " + away;
        String description = "Матч " + title + ". Счёт " + match.getHomeScore() + ":" + match.getAwayScore() + ".";
        return new PageCopy(title, description);
    }

    private static String playerName(PlayerProfile profile) {
        String last = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String first = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String legal = (last + " " + first).trim();
        if (!legal.isEmpty()) {
            return legal;
        }
        if (profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
            return profile.getDisplayName().trim();
        }
        return "Игрок";
    }

    private static UUID idAfter(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        String raw = path.substring(prefix.length());
        if (raw.isEmpty() || raw.indexOf('/') >= 0) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ResponseEntity<String> html(
            HttpStatus status,
            HttpServletRequest request,
            String path,
            String title,
            String description
    ) {
        String origin = origin(request);
        String url = origin + path;
        String image = origin + "/brand/logo-mark.png";
        String fullTitle = title.contains(SITE) ? title : title + " — " + SITE;
        String body = """
                <!doctype html>
                <html lang="ru">
                <head>
                <meta charset="utf-8">
                <title>%s</title>
                <meta name="description" content="%s">
                <link rel="canonical" href="%s">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:type" content="website">
                <meta property="og:locale" content="ru_RU">
                <meta name="twitter:card" content="summary">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:description" content="%s">
                <meta name="twitter:image" content="%s">
                </head>
                <body>
                <h1>%s</h1>
                <p>%s</p>
                </body>
                </html>
                """.formatted(
                esc(fullTitle), esc(description), esc(url),
                esc(fullTitle), esc(description), esc(url), esc(image),
                esc(fullTitle), esc(description), esc(image),
                esc(fullTitle), esc(description)
        );
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(body);
    }

    private static void appendUrl(StringBuilder xml, String origin, String path) {
        xml.append("  <url><loc>").append(esc(origin + path)).append("</loc></url>\n");
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String path = raw.trim();
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("\\") || path.contains("//") || path.contains("..")) {
            return null;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String origin(HttpServletRequest request) {
        String proto = firstHeader(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String host = firstHeader(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        proto = proto.split(",")[0].trim().toLowerCase(Locale.ROOT);
        host = host.split(",")[0].trim();
        if (!"https".equals(proto) && !"http".equals(proto)) {
            proto = "https";
        }
        if (!HOST.matcher(host).matches()) {
            return "https://itmoliga.ru";
        }
        return proto + "://" + host;
    }

    private static String firstHeader(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "itmoliga.ru";
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record PageCopy(String title, String description) {
    }
}
