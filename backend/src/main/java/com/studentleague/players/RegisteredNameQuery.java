package com.studentleague.players;

import com.studentleague.players.entity.PlayerProfile;
import com.studentleague.teams.domain.TeamMemberStatus;
import com.studentleague.teams.entity.TeamMember;
import com.studentleague.tournaments.domain.TournamentTeamStatus;
import com.studentleague.tournaments.entity.TournamentTeam;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registration name search. Each word of the query must occur in the first name
 * or the last name. The shirt nickname is not a field of this match.
 * Case and extra spaces are ignored. PostgreSQL {@code lower()} does not fold
 * Cyrillic under every locale, so the fold is {@code lower} plus a fixed
 * Cyrillic translate.
 */
public final class RegisteredNameQuery {

    private static final String UPPER_CYRILLIC = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
    private static final String LOWER_CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";

    private RegisteredNameQuery() {
    }

    public static String collapse(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    public static String fold(String raw) {
        return collapse(raw).toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String firstName, String lastName, String raw) {
        String query = fold(raw);
        if (query.length() < 2) {
            return false;
        }
        String first = fold(firstName);
        String last = fold(lastName);
        boolean any = false;
        for (String token : query.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            any = true;
            if (!first.contains(token) && !last.contains(token)) {
                return false;
            }
        }
        return any;
    }

    public static Specification<PlayerProfile> matching(String raw, UUID tournamentId) {
        Specification<PlayerProfile> spec = byTokens(fold(raw));
        if (tournamentId != null) {
            spec = spec.and(inTournament(tournamentId));
        }
        return spec;
    }

    public static Specification<PlayerProfile> inTournament(UUID tournamentId) {
        return (root, query, cb) -> {
            Subquery<UUID> members = query.subquery(UUID.class);
            Root<TeamMember> member = members.from(TeamMember.class);
            Root<TournamentTeam> entry = members.from(TournamentTeam.class);
            members.select(member.get("playerId")).where(
                    cb.equal(member.get("teamId"), entry.get("teamId")),
                    cb.equal(member.get("status"), TeamMemberStatus.ACTIVE),
                    cb.equal(entry.get("tournamentId"), tournamentId),
                    entry.get("status").in(TournamentTeamStatus.APPROVED, TournamentTeamStatus.PENDING)
            );
            return root.get("id").in(members);
        };
    }

    private static Specification<PlayerProfile> byTokens(String folded) {
        String[] tokens = folded.split(" ");
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Expression<String> first = foldedName(cb, root.get("firstName"));
            Expression<String> last = foldedName(cb, root.get("lastName"));
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }
                String pattern = "%" + escapeLike(token) + "%";
                predicates.add(cb.or(
                        cb.like(first, pattern, '\\'),
                        cb.like(last, pattern, '\\')
                ));
            }
            if (predicates.isEmpty()) {
                return cb.disjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> foldedName(CriteriaBuilder cb, Expression<String> name) {
        return cb.function(
                "translate",
                String.class,
                cb.lower(cb.coalesce(name, "")),
                cb.literal(UPPER_CYRILLIC),
                cb.literal(LOWER_CYRILLIC)
        );
    }

    private static String escapeLike(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
