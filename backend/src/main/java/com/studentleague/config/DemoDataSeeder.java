package com.studentleague.config;

import com.studentleague.matches.domain.MatchEventType;
import com.studentleague.matches.domain.MatchStatus;
import com.studentleague.matches.entity.Match;
import com.studentleague.matches.entity.MatchEvent;
import com.studentleague.matches.entity.MatchLineupPlayer;
import com.studentleague.matches.repository.MatchEventRepository;
import com.studentleague.matches.repository.MatchLineupPlayerRepository;
import com.studentleague.matches.repository.MatchRepository;
import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.players.repository.PlayerProfileRepository;
import com.studentleague.sports.entity.Sport;
import com.studentleague.sports.repository.SportRepository;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.Team;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.teams.repository.TeamMemberRepository;
import com.studentleague.teams.repository.TeamRepository;
import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.domain.TournamentStatus;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.Tournament;
import com.studentleague.tournaments.entity.TournamentTeam;
import com.studentleague.tournaments.repository.TournamentRepository;
import com.studentleague.tournaments.repository.TournamentTeamRepository;
import com.studentleague.users.domain.Role;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import com.studentleague.users.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty league so the public site has a table, calendar and scorers.
 * Runs once: skipped when any team already exists, or when {@code app.demo-data.enabled=false}.
 */
@Component
@Order(200)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "DemoPass123!";

    private static final String[][] CLUBS = {
            {"Физтех", "ФТХ"},
            {"Юристы", "ЮРФ"},
            {"Экономика", "ЭКН"},
            {"Медики", "МЕД"},
            {"Архитекторы", "АРХ"},
            {"ИТ-кластер", "ИТК"},
            {"Журналистика", "ЖУР"},
            {"Педагоги", "ПЕД"},
    };

    private static final String[] FIRST = {
            "Артём", "Илья", "Максим", "Кирилл", "Даниил", "Никита", "Егор", "Павел"
    };
    private static final String[] LAST = {
            "Соколов", "Орлов", "Волков", "Белов", "Крылов", "Лебедев", "Морозов", "Новиков"
    };

    private final AppProperties appProperties;
    private final TeamRepository teamRepository;
    private final SportRepository sportRepository;
    private final UserRepository userRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchLineupPlayerRepository lineupRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;

    public DemoDataSeeder(
            AppProperties appProperties,
            TeamRepository teamRepository,
            SportRepository sportRepository,
            UserRepository userRepository,
            PlayerProfileRepository playerProfileRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            TournamentTeamRepository tournamentTeamRepository,
            MatchRepository matchRepository,
            MatchEventRepository matchEventRepository,
            MatchLineupPlayerRepository lineupRepository,
            PasswordEncoder passwordEncoder,
            RoleService roleService
    ) {
        this.appProperties = appProperties;
        this.teamRepository = teamRepository;
        this.sportRepository = sportRepository;
        this.userRepository = userRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.lineupRepository = lineupRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appProperties.demoData() == null || !appProperties.demoData().enabled()) {
            return;
        }
        if (teamRepository.count() > 0) {
            return;
        }
        Sport football = sportRepository.findByCodeIgnoreCase("FOOTBALL")
                .orElseThrow(() -> new IllegalStateException("Sport FOOTBALL is not seeded"));

        List<Squad> squads = new ArrayList<>();
        for (int i = 0; i < CLUBS.length; i++) {
            squads.add(createSquad(i));
        }

        Tournament tournament = new Tournament();
        tournament.setName("KRONBARS Cup 2026");
        tournament.setDescription("Демо-сезон, чтобы таблица и статистика не были пустыми.");
        tournament.setSportId(football.getId());
        tournament.setSeasonYear(2026);
        tournament.setStartDate(LocalDate.of(2026, 8, 1));
        tournament.setEndDate(LocalDate.of(2026, 12, 15));
        tournament.setStatus(TournamentStatus.ACTIVE);
        tournament.setFormat(TournamentFormat.ROUND_ROBIN.name());
        tournament.setMaxSquadSize(25);
        tournamentRepository.save(tournament);

        Instant now = Instant.now();
        for (Squad squad : squads) {
            TournamentTeam row = new TournamentTeam();
            row.setTournamentId(tournament.getId());
            row.setTeamId(squad.team.getId());
            row.setStatus(TournamentTeamStatus.APPROVED);
            row.setApprovedAt(now);
            tournamentTeamRepository.save(row);
        }

        // home, away, homeGoals, awayGoals — already played
        int[][] played = {
                {0, 1, 3, 1},
                {2, 3, 2, 2},
                {4, 5, 1, 0},
                {6, 7, 0, 0},
                {0, 2, 4, 2},
                {1, 3, 2, 0},
                {4, 6, 1, 1},
                {5, 7, 3, 0},
                {0, 4, 2, 1},
                {1, 5, 0, 2},
        };
        for (int i = 0; i < played.length; i++) {
            int[] row = played[i];
            playFinished(
                    tournament,
                    football,
                    squads.get(row[0]),
                    squads.get(row[1]),
                    row[2],
                    row[3],
                    now.minus(14 - i, ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS)
            );
        }

        int[][] upcoming = {
                {2, 6},
                {3, 7},
                {0, 5},
                {1, 4},
        };
        for (int i = 0; i < upcoming.length; i++) {
            schedule(
                    tournament,
                    football,
                    squads.get(upcoming[i][0]),
                    squads.get(upcoming[i][1]),
                    now.plus(2 + i * 2L, ChronoUnit.DAYS).plus(18, ChronoUnit.HOURS)
            );
        }

        log.info("Demo league seeded: {} teams, tournament {}", squads.size(), tournament.getName());
    }

    private Squad createSquad(int index) {
        String teamName = CLUBS[index][0];
        String shortName = CLUBS[index][1];
        String slug = "club" + (index + 1);
        PlayerProfile captain = person(index, 0, "captain." + slug + "@kronbars.local", Role.CAPTAIN, "FW", 10);
        PlayerProfile mid = person(index, 1, "mid." + slug + "@kronbars.local", Role.PLAYER, "MF", 8);
        PlayerProfile fw = person(index, 2, "fw." + slug + "@kronbars.local", Role.PLAYER, "FW", 9);
        PlayerProfile gk = person(index, 3, "gk." + slug + "@kronbars.local", Role.PLAYER, "Вратарь", 1);

        Team team = new Team();
        team.setName(teamName);
        team.setShortName(shortName);
        team.setLogoUrl(avatar(shortName));
        team.setCaptainId(captain.getId());
        team.setFoundedOn(LocalDate.of(2018 + (index % 6), 9, 1));
        teamRepository.save(team);
        addMember(team, captain);
        addMember(team, mid);
        addMember(team, fw);
        addMember(team, gk);
        return new Squad(team, captain, mid, fw, gk);
    }

    private PlayerProfile person(int team, int slot, String email, Role role, String position, int number) {
        String first = FIRST[(team + slot) % FIRST.length];
        String last = LAST[(team * 3 + slot) % LAST.length];
        String photo = avatar(first + " " + last);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRole(role);
        user.setEnabled(true);
        user.setFirstName(first);
        user.setLastName(last);
        user.setPhotoUrl(photo);
        userRepository.save(user);
        roleService.grantApproved(user, Role.FAN, null);
        roleService.grantApproved(user, role, photo);

        PlayerProfile profile = new PlayerProfile();
        profile.setUserId(user.getId());
        profile.setFirstName(first);
        profile.setLastName(last);
        profile.setDisplayName(first + " " + last);
        profile.setPosition(position);
        profile.setJerseyNumber(number);
        profile.setAvatarUrl(photo);
        return playerProfileRepository.save(profile);
    }

    private void addMember(Team team, PlayerProfile player) {
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setPlayerId(player.getId());
        member.setStatus(TeamMemberStatus.ACTIVE);
        teamMemberRepository.save(member);
    }

    private void playFinished(
            Tournament tournament,
            Sport sport,
            Squad home,
            Squad away,
            int homeGoals,
            int awayGoals,
            Instant kickoff
    ) {
        Match match = new Match();
        match.setTournamentId(tournament.getId());
        match.setSportId(sport.getId());
        match.setHomeTeamId(home.team.getId());
        match.setAwayTeamId(away.team.getId());
        match.setScheduledAt(kickoff);
        match.setStartedAt(kickoff);
        match.setFinishedAt(kickoff.plus(2, ChronoUnit.HOURS));
        match.setStatus(MatchStatus.FINISHED);
        match.setHomeScore(homeGoals);
        match.setAwayScore(awayGoals);
        match.setPeriod(2);
        match.setGameTimeSeconds(2400);
        matchRepository.save(match);

        lineup(match, home);
        lineup(match, away);
        addGoals(match, home, homeGoals, 9);
        addGoals(match, away, awayGoals, 14);
    }

    private void schedule(Tournament tournament, Sport sport, Squad home, Squad away, Instant kickoff) {
        Match match = new Match();
        match.setTournamentId(tournament.getId());
        match.setSportId(sport.getId());
        match.setHomeTeamId(home.team.getId());
        match.setAwayTeamId(away.team.getId());
        match.setScheduledAt(kickoff);
        match.setStatus(MatchStatus.SCHEDULED);
        matchRepository.save(match);
    }

    private void lineup(Match match, Squad squad) {
        PlayerProfile[] eleven = {squad.gk, squad.captain, squad.mid, squad.fw};
        for (int i = 0; i < eleven.length; i++) {
            MatchLineupPlayer row = new MatchLineupPlayer();
            row.setMatchId(match.getId());
            row.setTeamId(squad.team.getId());
            row.setPlayerId(eleven[i].getId());
            row.setStarter(true);
            row.setSortOrder(i);
            lineupRepository.save(row);
        }
    }

    private void addGoals(Match match, Squad squad, int goals, int startMinute) {
        for (int i = 0; i < goals; i++) {
            PlayerProfile scorer = i % 2 == 0 ? squad.fw : squad.mid;
            PlayerProfile assist = i % 2 == 0 ? squad.mid : squad.captain;
            MatchEvent event = new MatchEvent();
            event.setMatchId(match.getId());
            event.setEventType(MatchEventType.GOAL);
            event.setTimestamp(match.getScheduledAt().plus(startMinute + i * 8L, ChronoUnit.MINUTES));
            event.setGameTime(startMinute + i * 8);
            event.setPeriod(startMinute + i * 8 > 45 ? 2 : 1);
            event.setTeamId(squad.team.getId());
            event.setPlayerId(scorer.getId());
            event.setSecondaryPlayerId(assist.getId());
            matchEventRepository.save(event);
        }
    }

    private static String avatar(String seed) {
        return "https://api.dicebear.com/9.x/initials/svg?seed=" + URLEncoder.encode(seed, StandardCharsets.UTF_8);
    }

    private record Squad(Team team, PlayerProfile captain, PlayerProfile mid, PlayerProfile fw, PlayerProfile gk) {
    }
}
