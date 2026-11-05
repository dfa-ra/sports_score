package com.studentleague.storage;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opt-in rewrite of already stored logos, avatars, and registration images whose
 * JPEG Exif orientation is not 1. Gallery files are left alone.
 *
 * <p>The default is {@code --dry-run}. {@code --apply} copies each original into a
 * sibling {@code media-backup-<timestamp>/} directory (same relative path) before
 * overwriting it. That backup is never deleted. Sidecars of a rewritten file are
 * copied there too and then removed so the next read rebuilds them from upright pixels.
 *
 * <p>На проде только после мержа, с бэкапом оригиналов; этот агент его не запускал.
 *
 * <pre>
 *   ./mvnw -q -DskipTests compile
 *   java -cp target/classes com.studentleague.storage.MediaOrientBackup --dry-run /path/to/media
 *   java -cp target/classes com.studentleague.storage.MediaOrientBackup --apply /path/to/media
 * </pre>
 */
public final class MediaOrientBackup {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> FOLDERS = Set.of("logos", "avatars", "registration");

    private MediaOrientBackup() {
    }

    public static void main(String[] args) {
        try {
            int code = run(args, System.out);
            if (code != 0) {
                System.exit(code);
            }
        } catch (IOException ex) {
            System.err.println("media orient backup failed: " + ex.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args, PrintStream out) throws IOException {
        Boolean apply = null;
        Path root = null;
        for (String arg : args) {
            if ("--apply".equals(arg)) {
                apply = Boolean.TRUE;
            } else if ("--dry-run".equals(arg)) {
                apply = Boolean.FALSE;
            } else if (arg.startsWith("-")) {
                out.println("Неизвестный аргумент: " + arg);
                out.println(usage());
                return 2;
            } else if (root != null) {
                out.println(usage());
                return 2;
            } else {
                root = Path.of(arg);
            }
        }
        if (root == null) {
            out.println(usage());
            return 2;
        }
        boolean dryRun = apply == null || !apply;
        if (!Files.isDirectory(root)) {
            out.println("Нет каталога: " + root);
            return 1;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        Path backupRoot = backupDirectory(root, stamp);
        int seen = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                if (skipName(file)) {
                    continue;
                }
                String folder = folderOf(root, file);
                if (!FOLDERS.contains(folder)) {
                    continue;
                }
                byte[] raw = Files.readAllBytes(file);
                int orientation = ImageOrientation.orientation(raw);
                if (orientation == 1) {
                    continue;
                }
                seen++;
                Path relative = root.relativize(file);
                byte[] next = bytesForExisting(raw, folder);
                if (next == raw) {
                    out.println((dryRun ? "dry-run: skip" : "skip") + " " + relative + " (orientation " + orientation + ", decode failed)");
                    continue;
                }
                if (dryRun) {
                    out.println("dry-run: would backup " + relative + " -> " + backupRoot.getFileName() + "/" + relative
                            + " (orientation " + orientation + ") then rewrite in place");
                    noteSidecars(root, file, backupRoot, true, out);
                    continue;
                }
                copyAside(file, backupRoot.resolve(relative));
                Files.write(file, next);
                out.println("backed up " + relative + " -> " + backupRoot.getFileName() + "/" + relative
                        + " (orientation " + orientation + ") and rewrote in place");
                noteSidecars(root, file, backupRoot, false, out);
            }
        }
        if (seen == 0) {
            out.println((dryRun ? "dry-run: " : "") + "no logos/avatars/registration images with Exif orientation other than 1");
        }
        out.println(dryRun
                ? "dry-run only. Backup directory was not created. Pass --apply to copy originals aside and rewrite."
                : "Backup kept at " + backupRoot + " (not deleted).");
        return 0;
    }

    /**
     * Logos are squared only when the file is already a PNG, so a JPEG URL keeps a JPEG.
     * New uploads still go through {@link ImageOrientation#storedBytes} with a matching extension.
     */
    static byte[] bytesForExisting(byte[] raw, String folder) {
        boolean square = "logos".equals(folder) && ImageOrientation.isPng(raw);
        return ImageOrientation.storedBytes(raw, square);
    }

    private static void noteSidecars(Path root, Path original, Path backupRoot, boolean dryRun, PrintStream out) throws IOException {
        Path dir = original.getParent();
        if (dir == null) {
            return;
        }
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String prefix = stem + ".__v";
        try (Stream<Path> list = Files.list(dir)) {
            for (Path side : list.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
                Path relative = root.relativize(side);
                if (dryRun) {
                    out.println("dry-run: would backup sidecar " + relative + " then remove it so it can be rebuilt");
                    continue;
                }
                copyAside(side, backupRoot.resolve(relative));
                Files.delete(side);
                out.println("backed up and removed sidecar " + relative);
            }
        }
    }

    private static void copyAside(Path source, Path dest) throws IOException {
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(dest)) {
            throw new IOException("backup already exists, refusing to overwrite " + dest);
        }
        Files.copy(source, dest);
    }

    static Path backupDirectory(Path mediaRoot, String stamp) {
        Path parent = mediaRoot.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        return parent.resolve("media-backup-" + stamp);
    }

    private static boolean skipName(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.contains(".__v") || name.endsWith(".skip") || name.endsWith(".part");
    }

    private static String folderOf(Path root, Path file) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() == 0) {
            return "";
        }
        return relative.getName(0).toString().toLowerCase(Locale.ROOT);
    }

    private static String usage() {
        return """
                Usage: MediaOrientBackup [--dry-run|--apply] <media-root>
                Default is --dry-run. --apply copies each file to a sibling media-backup-<timestamp>/ before overwrite.
                The backup is never deleted. Do not point this at production until the change is merged.
                На проде только после мержа, с бэкапом оригиналов; этот агент его не запускал.
                """;
    }
}
