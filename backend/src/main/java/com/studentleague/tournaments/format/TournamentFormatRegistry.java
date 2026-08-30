package com.studentleague.tournaments.format;

import com.studentleague.tournaments.domain.TournamentFormat;
import com.studentleague.tournaments.dto.TournamentFormatResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TournamentFormatRegistry {

    private final Map<String, TournamentFormatHandler> handlers;
    private final TableStandingsCalculator calculator;

    public TournamentFormatRegistry(List<TournamentFormatHandler> handlers, TableStandingsCalculator calculator) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(TournamentFormatHandler::code, Function.identity()));
        this.calculator = calculator;
    }

    public TournamentFormatHandler handler(String code) {
        String normalized = TournamentFormat.normalize(code);
        TournamentFormatHandler handler = handlers.get(normalized);
        if (handler != null) {
            return handler;
        }
        return handlers.getOrDefault(TournamentFormat.ROUND_ROBIN.name(), new FallbackHandler(normalized, calculator));
    }

    public List<TournamentFormatResponse> list() {
        return Arrays.stream(TournamentFormat.values())
                .map(format -> {
                    TournamentFormatHandler handler = handlers.get(format.name());
                    return new TournamentFormatResponse(
                            format.name(),
                            handler == null ? format.title() : handler.title(),
                            handler == null ? "" : handler.description()
                    );
                })
                .toList();
    }

    private record FallbackHandler(String code, TableStandingsCalculator calculator) implements TournamentFormatHandler {
        @Override
        public String title() {
            return code;
        }

        @Override
        public String description() {
            return "Формат без отдельного обработчика — таблица считается как круговая. Добавьте TournamentFormatHandler.";
        }

        @Override
        public List<com.studentleague.tournaments.dto.StandingRow> standings(StandingsContext context) {
            return calculator.compute(context);
        }
    }
}
