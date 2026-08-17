package com.studentleague.storage;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Сохраняет файлы в локальную папку на диске сервера и отдаёт публичный URL /media/...
 */
@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalDiskStorageService implements StorageService {

    private final Path rootDir;
    private final String publicBaseUrl;

    public LocalDiskStorageService(AppProperties appProperties) {
        AppProperties.LocalStorage local = appProperties.localStorage();
        String root = local == null || local.rootDir() == null || local.rootDir().isBlank()
                ? "./data/uploads"
                : local.rootDir();
        this.rootDir = Path.of(root).toAbsolutePath().normalize();
        this.publicBaseUrl = local == null || local.publicBaseUrl() == null || local.publicBaseUrl().isBlank()
                ? "/media"
                : local.publicBaseUrl().replaceAll("/$", "");
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory: " + this.rootDir, e);
        }
    }

    @Override
    public String store(String folder, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = UUID.randomUUID() + "-" + safeName;
        Path dir = rootDir.resolve(sanitize(folder));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return publicBaseUrl + "/" + sanitize(folder) + "/" + filename;
        } catch (IOException e) {
            throw ApiException.badRequest("Failed to store uploaded file");
        }
    }

    private static String sanitize(String folder) {
        return folder == null ? "misc" : folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }
}
