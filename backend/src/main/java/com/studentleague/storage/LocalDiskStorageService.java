package com.studentleague.storage;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
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
        String folderName = sanitize(folder);
        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Не удалось сохранить файл");
        }
        byte[] body = orient(folderName, raw);
        String filename = UUID.randomUUID() + "-" + storedName(ImageUploads.safeFilename(file), raw, body);
        Path dir = rootDir.resolve(folderName);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.write(target, body);
            ImageVariants.writeForFolder(target, folderName);
            return publicBaseUrl + "/" + folderName + "/" + filename;
        } catch (IOException e) {
            throw ApiException.badRequest("Не удалось сохранить файл");
        }
    }

    /**
     * Logos are squared onto a transparent PNG. Avatars and registration photos
     * are only turned upright. Gallery bytes stay as uploaded.
     */
    private static byte[] orient(String folder, byte[] raw) {
        if ("logos".equals(folder)) {
            return ImageOrientation.storedBytes(raw, true);
        }
        if ("avatars".equals(folder) || "registration".equals(folder)) {
            return ImageOrientation.storedBytes(raw, false);
        }
        return raw;
    }

    private static String storedName(String filename, byte[] raw, byte[] body) {
        if (body == raw) {
            return filename;
        }
        if (ImageOrientation.isPng(body)) {
            return withExtension(filename, "png");
        }
        if (ImageOrientation.isJpeg(body)) {
            return withExtension(filename, "jpg");
        }
        return filename;
    }

    private static String withExtension(String filename, String extension) {
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        return stem + "." + extension;
    }

    private static String sanitize(String folder) {
        return folder == null ? "misc" : folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }
}
