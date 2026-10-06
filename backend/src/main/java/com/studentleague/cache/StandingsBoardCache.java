package com.studentleague.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * In-process cache for tournament standings and the statistics board.
 * Entries live 60 seconds and drop as soon as a protocol write commits,
 * so the next request sees the new score instead of waiting out the TTL.
 */
@Component
public class StandingsBoardCache {

    static final long TTL_NANOS = 60_000_000_000L;

    private final ConcurrentHashMap<String, Slot> entries = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong();

    public <T> T board(UUID tournamentId, int limit, Supplier<T> loader) {
        return get(boardKey(tournamentId, limit), loader);
    }

    public <T> T standings(UUID tournamentId, Supplier<T> loader) {
        return get(standingsKey(tournamentId), loader);
    }

    public <T> T get(String key, Supplier<T> loader) {
        long now = System.nanoTime();
        Slot cached = entries.get(key);
        if (cached != null && cached.expiresAtNanos - now > 0) {
            @SuppressWarnings("unchecked")
            T value = (T) cached.value;
            return value;
        }
        long seen = epoch.get();
        T loaded = loader.get();
        if (loaded != null && epoch.get() == seen) {
            entries.put(key, new Slot(loaded, System.nanoTime() + TTL_NANOS));
        }
        return loaded;
    }

    /**
     * Drops standings and every board limit for this tournament, plus the
     * all-tournaments board, after the surrounding write commits.
     */
    public void invalidateTournament(UUID tournamentId) {
        Runnable drop = () -> dropTournament(tournamentId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    drop.run();
                }
            });
            return;
        }
        drop.run();
    }

    void dropTournament(UUID tournamentId) {
        epoch.incrementAndGet();
        if (tournamentId == null) {
            entries.clear();
            return;
        }
        String id = tournamentId.toString();
        entries.keySet().removeIf(key ->
                key.equals(standingsKey(tournamentId))
                        || key.startsWith("board:" + id + ":")
                        || key.startsWith("board:all:"));
    }

    static String boardKey(UUID tournamentId, int limit) {
        return "board:" + (tournamentId == null ? "all" : tournamentId) + ":" + limit;
    }

    static String standingsKey(UUID tournamentId) {
        return "standings:" + tournamentId;
    }

    private record Slot(Object value, long expiresAtNanos) {
    }
}
