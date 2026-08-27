package com.studentleague.tournaments.service;

import com.studentleague.common.exception.ApiException;
import com.studentleague.matches.dto.CreateMatchRequest;
import com.studentleague.matches.dto.MatchResponse;
import com.studentleague.matches.service.MatchService;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.dto.CalendarImportResponse;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
    private final TeamRepository teamRepository;
    private final MatchService matchService;

    public CalendarImportService(
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            TeamRepository teamRepository,
            MatchService matchService
    ) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.teamRepository = teamRepository;
        this.matchService = matchService;
    }

    @Transactional
    public CalendarImportResponse importCalendar(UUID tournamentId, MultipartFile file) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Tournament not found"));
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Файл календаря пустой");
        }
        List<String[]> rows = readRows(file);
        if (rows.isEmpty()) {
            throw ApiException.badRequest("В файле нет строк. Нужны колонки: date, time, home, away");
        }
        Header header = Header.parse(rows.getFirst());
        List<UUID> created = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
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
                MatchResponse match = matchService.create(new CreateMatchRequest(
                        tournament.getId(), homeTeam.getId(), awayTeam.getId(), kickoff, null, null
                ));
                created.add(match.id());
            } catch (RuntimeException ex) {
                skipped++;
                warnings.add("Строка " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new CalendarImportResponse(created.size(), skipped, created, warnings);
    }

    private void ensureApproved(Tournament tournament, Team team) {
        if (team.isDisbanded()) {
            throw ApiException.badRequest("Команда «" + team.getName() + "» расформирована");
        }
        tournamentTeamRepository.findByTournamentIdAndTeamId(tournament.getId(), team.getId())
                .ifPresentOrElse(entry -> {
                    if (entry.getStatus() != TournamentTeamStatus.APPROVED) {
                        entry.setStatus(TournamentTeamStatus.APPROVED);
                        entry.setApprovedAt(Instant.now());
                        tournamentTeamRepository.save(entry);
                    }
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

    private static List<String[]> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(file);
            }
            return readCsv(file);
        } catch (IOException ex) {
            throw ApiException.badRequest("Не удалось прочитать файл: " + ex.getMessage());
        }
    }

    private static List<String[]> readCsv(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                rows.add(splitCsv(line));
            }
        }
        return rows;
    }

    private static List<String[]> readExcel(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
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
                rows.add(values);
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
