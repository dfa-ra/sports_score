package com.studentleague.tournaments.format;

import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.dto.StandingRow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BuiltInFormatHandlers {

    @Bean
    TournamentFormatHandler roundRobinFormat(TableStandingsCalculator calculator) {
        return handler(TournamentFormat.ROUND_ROBIN, "Каждая команда играет с каждой. Таблица по очкам.", calculator);
    }

    @Bean
    TournamentFormatHandler cupFormat(TableStandingsCalculator calculator) {
        return handler(TournamentFormat.CUP, "Олимпийская сетка. Таблица — вспомогательная, по сыгранным матчам.", calculator);
    }

    @Bean
    TournamentFormatHandler groupsPlayoffFormat(TableStandingsCalculator calculator) {
        return handler(TournamentFormat.GROUPS_PLAYOFF, "Групповой этап, затем плей-офф. Таблица групп считается круговой.", calculator);
    }

    @Bean
    TournamentFormatHandler swissFormat(TableStandingsCalculator calculator) {
        return handler(TournamentFormat.SWISS, "Пары по текущему рейтингу. Таблица — по очкам.", calculator);
    }

    @Bean
    TournamentFormatHandler doubleEliminationFormat(TableStandingsCalculator calculator) {
        return handler(TournamentFormat.DOUBLE_ELIMINATION, "Две сетки: winners и losers. Таблица — по сыгранным матчам.", calculator);
    }

    private static TournamentFormatHandler handler(
            TournamentFormat format,
            String description,
            TableStandingsCalculator calculator
    ) {
        return new TournamentFormatHandler() {
            @Override
            public String code() {
                return format.name();
            }

            @Override
            public String title() {
                return format.title();
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public List<StandingRow> standings(StandingsContext context) {
                return calculator.compute(context);
            }
        };
    }
}
