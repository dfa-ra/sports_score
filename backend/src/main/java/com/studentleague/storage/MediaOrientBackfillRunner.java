package com.studentleague.storage;

import com.studentleague.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Opt-in one-shot for files already stored under {@code /media}.
 * Dev only until Захар says да + backup.
 * <p>
 * Off unless {@code app.media.orient-backfill=true} (env {@code APP_MEDIA_ORIENT_BACKFILL}).
 * Not a Flyway migration and not part of normal startup. Do not point it at production
 * {@code /media} until there is a backup and an explicit yes.
 */
@Component
@ConditionalOnProperty(name = "app.media.orient-backfill", havingValue = "true")
public class MediaOrientBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaOrientBackfillRunner.class);

    private final Path root;

    @Autowired
    public MediaOrientBackfillRunner(AppProperties appProperties) {
        this(rootOf(appProperties));
    }

    MediaOrientBackfillRunner(Path root) {
        this.root = root;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn(
                "Media orient backfill is ON ({}). Rewriting logos and player photos under {}. Not for prod without a backup.",
                MediaOrient.DEV_ONLY,
                root
        );
        int changed = MediaOrient.rewriteTree(root);
        log.warn("Media orient backfill finished. Files rewritten in place: {}", changed);
    }

    private static Path rootOf(AppProperties appProperties) {
        AppProperties.LocalStorage local = appProperties == null ? null : appProperties.localStorage();
        String root = local == null || local.rootDir() == null || local.rootDir().isBlank()
                ? "./data/uploads"
                : local.rootDir();
        return Path.of(root).toAbsolutePath().normalize();
    }
}
