package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.matches.service.MatchService;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.CalendarImportResponse;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTable;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTableRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarImportService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );
    private static final List<DateTimeFormatter> TIMES = List.of(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss")
    );

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final TournamentTableRepository tournamentTableRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final TournamentRosterGuard tournamentRosterGuard;

    public CalendarImportService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TournamentTableRepository tournamentTableRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            MatchService matchService,
            TournamentRosterGuard tournamentRosterGuard
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.tournamentTableRepository = tournamentTableRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.tournamentRosterGuard = tournamentRosterGuard;
    }

    @Transactional
    public CalendarImportResponse importCalendar(UUID tournamentId, MultipartFile file) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Турнир не найден"));
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Файл не приложен");
        }
        List<SheetRow> rows = readRows(file);
        if (rows.isEmpty()) {
            throw ApiException.badRequest("В файле нет строк. Нужны колонки: date, time, home, away");
        }
        Header header = Header.parse(rows.getFirst().cells());
        if (!header.hasAny("date", "дата") || !header.hasAny("home", "хозяева", "хозяин") || !header.hasAny("away", "гости", "гость")) {
            throw ApiException.badRequest("Не удалось прочитать календарь. Нужны колонки: date, time, home, away");
        }
        boolean venueColumn = header.hasAny("venue", "зал", "площадка", "место");
        boolean roundColumn = header.hasAny("round", "тур", "круг", "tour");
        boolean groupColumn = header.hasAny("group", "группа", "stage", "этап", "стадия");
        List<UUID> created = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int updated = 0;
        int acknowledged = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            SheetRow sheetRow = rows.get(i);
            String[] row = sheetRow.cells();
            if (isBlank(row)) {
                continue;
            }
            try {
                String date = header.get(row, "date", "дата");
                String time = header.get(row, "time", "время");
                String home = header.get(row, "home", "хозяева", "хозяин");
                String away = header.get(row, "away", "гости", "гость");
                Team homeTeam = resolveTeam(home);
                Team awayTeam = resolveTeam(away);
                ensureApproved(tournament, homeTeam);
                ensureApproved(tournament, awayTeam);
                Instant kickoff = parseKickoff(date, time);
                String venue = venueColumn ? blankToNull(header.get(row, "venue", "зал", "площадка", "место")) : null;
                Integer round = roundColumn ? parseRound(header.get(row, "round", "тур", "круг", "tour")) : null;
                UUID stageId = stageOf(
                        tournament,
                        homeTeam,
                        awayTeam,
                        groupColumn ? header.get(row, "group", "группа", "stage", "этап", "стадия") : null
                );
                RowOutcome outcome = upsert(tournament, homeTeam, awayTeam, kickoff, venue, venueColumn, round, stageId);
                switch (outcome.kind()) {
                    case CREATED -> created.add(outcome.id());
                    case UPDATED -> updated++;
                    case UNCHANGED -> acknowledged++;
                }
            } catch (ApiException ex) {
                if (ex.getStatus() == HttpStatus.CONFLICT) {
                    String message = ex.getMessage() == null ? "" : ex.getMessage();
                    if (message.startsWith("Строка ")) {
                        throw ex;
                    }
                    throw ApiException.conflict("Строка " + sheetRow.number() + ": " + message);
                }
                skipped++;
                warnings.add("Строка " + sheetRow.number() + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                skipped++;
                warnings.add("Строка " + sheetRow.number() + ": " + ex.getMessage());
            }
        }
        if (created.isEmpty() && updated == 0 && acknowledged == 0) {
            String detail = warnings.isEmpty()
                    ? "В файле нет матчей"
                    : warnings.getFirst();
            throw ApiException.badRequest("Календарь не импортирован. " + detail);
        }
        return new CalendarImportResponse(created.size(), updated, skipped, created, warnings);
    }

    private RowOutcome upsert(
            Tournament tournament,
            Team homeTeam,
            Team awayTeam,
            Instant kickoff,
            String venue,
            boolean venueColumn,
            Integer round,
            UUID stageId
    ) {
        List<Match> same = matchRepository
                .findByTournamentIdAndHomeTeamIdAndAwayTeamId(tournament.getId(), homeTeam.getId(), awayTeam.getId())
                .stream()
                .filter(match -> Objects.equals(match.getTableId(), stageId))
                .filter(match -> Objects.equals(match.getRoundNumber(), round))
                .toList();
        if (same.isEmpty()) {
            MatchResponse created = matchService.create(new CreateMatchRequest(
                    tournament.getId(),
                    homeTeam.getId(),
                    awayTeam.getId(),
                    kickoff,
                    null,
                    null,
                    venue,
                    stageId
            ));
            if (round != null) {
                Match entity = matchRepository.findById(created.id()).orElseThrow();
                entity.setRoundNumber(round);
                matchRepository.save(entity);
            }
            return RowOutcome.created(created.id());
        }
        for (Match match : same) {
            if (playedOrLive(match) && changes(match, kickoff, venue, venueColumn)) {
                throw ApiException.conflict("матч уже сыгран");
            }
        }
        Match editable = null;
        for (Match match : same) {
            if (!playedOrLive(match)) {
                editable = match;
                break;
            }
        }
        if (editable == null) {
            return RowOutcome.unchanged();
        }
        editable.setScheduledAt(kickoff);
        if (venueColumn) {
            editable.setVenue(venue);
        }
        matchRepository.save(editable);
        return RowOutcome.updated();
    }

    private UUID stageOf(Tournament tournament, Team home, Team away, String groupName) {
        if (groupName != null && !groupName.isBlank()) {
            String wanted = groupName.trim();
            for (TournamentTable table : tournamentTableRepository.findByTournamentIdOrderBySortOrderAscIdAsc(tournament.getId())) {
                if (table.getName() != null && table.getName().trim().equalsIgnoreCase(wanted)) {
                    return table.getId();
                }
            }
            throw ApiException.badRequest("Группа не найдена: " + wanted);
        }
        UUID homeTable = tableOf(tournament.getId(), home.getId());
        UUID awayTable = tableOf(tournament.getId(), away.getId());
        if (homeTable != null && homeTable.equals(awayTable)) {
            return homeTable;
        }
        return null;
    }

    private UUID tableOf(UUID tournamentId, UUID teamId) {
        return tournamentTeamRepository.findByTournamentIdAndTeamId(tournamentId, teamId)
                .map(TournamentTeam::getTableId)
                .orElse(null);
    }

    private static boolean playedOrLive(Match match) {
        MatchStatus status = match.getStatus();
        return status == MatchStatus.LIVE || status == MatchStatus.PAUSED || status == MatchStatus.FINISHED;
    }

    private static boolean changes(Match match, Instant kickoff, String venue, boolean venueColumn) {
        if (!Objects.equals(match.getScheduledAt(), kickoff)) {
            return true;
        }
        return venueColumn && !Objects.equals(blankToNull(match.getVenue()), venue);
    }

    private static Integer parseRound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw ApiException.badRequest("Непонятный тур: " + raw);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Непонятный тур: " + raw);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private enum OutcomeKind {
        CREATED, UPDATED, UNCHANGED
    }

    private record RowOutcome(OutcomeKind kind, UUID id) {
        static RowOutcome created(UUID id) {
            return new RowOutcome(OutcomeKind.CREATED, id);
        }

        static RowOutcome updated() {
            return new RowOutcome(OutcomeKind.UPDATED, null);
        }

        static RowOutcome unchanged() {
            return new RowOutcome(OutcomeKind.UNCHANGED, null);
        }
    }

    private void ensureApproved(Tournament tournament, Team team) {
        Optional<TournamentTeam> existing = tournamentTeamRepository.findByTournamentIdAndTeamId(tournament.getId(), team.getId());
        if (existing.isPresent() && existing.get().getStatus() == TournamentTeamStatus.WITHDRAWN) {
            throw ApiException.conflict("команда «" + team.getName() + "» снята с турнира, сначала верните её");
        }
        if (team.isDisbanded()) {
            throw ApiException.badRequest("Команда «" + team.getName() + "» расформирована");
        }
        tournamentRosterGuard.rejectSquadAlreadyInTournament(tournament.getId(), team.getId());
        existing.ifPresentOrElse(entry -> {
            if (entry.getStatus() == TournamentTeamStatus.APPROVED) {
                return;
            }
            entry.setStatus(TournamentTeamStatus.APPROVED);
            entry.setApprovedAt(Instant.now());
            tournamentTeamRepository.save(entry);
        }, () -> {
            TournamentTeam entry = new TournamentTeam();
            entry.setTournamentId(tournament.getId());
            entry.setTeamId(team.getId());
            entry.setStatus(TournamentTeamStatus.APPROVED);
            entry.setApprovedAt(Instant.now());
            tournamentTeamRepository.save(entry);
        });
    }

    private Team resolveTeam(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Пустое название команды");
        }
        String name = raw.trim();
        return teamRepository.findFirstByDisbandedFalseAndNameIgnoreCase(name)
                .or(() -> {
                    List<Team> fuzzy = teamRepository.findByDisbandedFalseAndNameContainingIgnoreCase(name);
                    if (fuzzy.size() == 1) {
                        return java.util.Optional.of(fuzzy.getFirst());
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> ApiException.notFound("Команда не найдена: " + name));
    }

    private static Instant parseKickoff(String dateRaw, String timeRaw) {
        LocalDate date = parseDate(dateRaw);
        LocalTime time = parseTime(timeRaw);
        return LocalDateTime.of(date, time).atZone(MOSCOW).toInstant();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Нет даты");
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATES) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        throw ApiException.badRequest("Непонятная дата: " + raw);
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.of(18, 0);
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : TIMES) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        throw ApiException.badRequest("Непонятное время: " + raw);
    }

    private static List<SheetRow> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(file);
            }
            return readCsv(file);
        } catch (IOException ex) {
            throw ApiException.badRequest("Не удалось прочитать файл: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("Не удалось прочитать файл календаря");
        }
    }

    private static List<SheetRow> readCsv(MultipartFile file) throws IOException {
        List<SheetRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                rows.add(new SheetRow(lineNumber, splitCsv(line)));
            }
        }
        return rows;
    }

    private static List<SheetRow> readExcel(MultipartFile file) throws IOException {
        List<SheetRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int last = row.getLastCellNum();
                String[] values = new String[Math.max(last, 0)];
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.getCell(i);
                    values[i] = cell == null ? "" : formatter.formatCellValue(cell).trim();
                }
                rows.add(new SheetRow(row.getRowNum() + 1, values));
            }
        }
        return rows;
    }

    private static String[] splitCsv(String line) {
        String delimiter = line.contains(";") && !line.contains(",") ? ";" : ",";
        return line.split(delimiter, -1);
    }

    private static boolean isBlank(String[] row) {
        if (row == null) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private record SheetRow(int number, String[] cells) {
    }

    private record Header(Map<String, Integer> columns) {
        static Header parse(String[] row) {
            Map<String, Integer> columns = new java.util.HashMap<>();
            for (int i = 0; i < row.length; i++) {
                if (row[i] != null && !row[i].isBlank()) {
                    columns.put(row[i].trim().toLowerCase(Locale.ROOT), i);
                }
            }
            return new Header(columns);
        }

        boolean hasAny(String... aliases) {
            for (String alias : aliases) {
                if (columns.containsKey(alias)) {
                    return true;
                }
            }
            return false;
        }

        String get(String[] row, String... aliases) {
            for (String alias : aliases) {
                Integer index = columns.get(alias);
                if (index != null && index < row.length) {
                    return row[index];
                }
            }
            return "";
        }
    }
}
