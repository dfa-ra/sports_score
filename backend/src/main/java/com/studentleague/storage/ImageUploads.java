package com.studentleague.storage;

import com.studentleague.common.exception.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * JPEG / JPG / PNG / WebP / GIF. SVG не берём — это разметка, не картинка для карусели.
 */
public final class ImageUploads {

    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private static final Set<String> TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "image/png",
            "image/x-png",
            "image/webp",
            "image/gif"
    );

    private ImageUploads() {
    }

    public static void requireRasterImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Файл обязателен");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Файл больше 8 МБ");
        }
        Kind kind = sniff(file);
        if (kind == null) {
            throw ApiException.badRequest("Нужны JPEG, PNG, WebP или GIF");
        }
    }

    public static String safeFilename(MultipartFile file) {
        Kind kind = sniff(file);
        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String base = original.replaceAll("[\\\\/]+", "_");
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.isBlank()) {
            base = "image";
        }
        String ext = kind == null ? extensionOf(original) : kind.extension;
        if (ext == null || ext.isBlank()) {
            ext = "bin";
        }
        return base + "." + ext;
    }

    static Kind sniff(MultipartFile file) {
        byte[] header = header(file);
        Kind fromMagic = fromMagic(header);
        if (fromMagic != null) {
            return fromMagic;
        }
        String type = normalizeType(file.getContentType());
        String ext = extensionOf(file.getOriginalFilename());
        if (TYPES.contains(type) && kindFromExtension(ext) != null) {
            return kindFromExtension(ext);
        }
        return null;
    }

    private static byte[] header(MultipartFile file) {
        try {
            byte[] all = file.getBytes();
            int n = Math.min(all.length, 16);
            byte[] head = new byte[n];
            System.arraycopy(all, 0, head, 0, n);
            return head;
        } catch (IOException e) {
            throw ApiException.badRequest("Не удалось прочитать файл");
        }
    }

    private static Kind fromMagic(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 0x50
                && b[2] == 0x4E
                && b[3] == 0x47) {
            return Kind.PNG;
        }
        if (b.length >= 6
                && b[0] == 'G'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == '8'
                && (b[4] == '7' || b[4] == '9')
                && b[5] == 'a') {
            return Kind.GIF;
        }
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') {
            return Kind.WEBP;
        }
        return null;
    }

    private static String normalizeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Kind kindFromExtension(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> Kind.JPEG;
            case "png" -> Kind.PNG;
            case "webp" -> Kind.WEBP;
            case "gif" -> Kind.GIF;
            default -> null;
        };
    }

    enum Kind {
        JPEG("jpg"),
        PNG("png"),
        WEBP("webp"),
        GIF("gif");

        private final String extension;

        Kind(String extension) {
            this.extension = extension;
        }
    }
}
