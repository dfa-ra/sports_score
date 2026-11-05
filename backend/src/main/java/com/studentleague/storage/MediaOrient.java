package com.studentleague.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * One-shot rewrite of logos and player photos that are already on disk.
 * Dev only until Захар says да + backup.
 * Not a Flyway migration. Does not run unless a caller opts in.
 * Leaves gallery and every other folder alone, and never changes a filename.
 */
public final class MediaOrient {

    static final String DEV_ONLY = "dev only until Захар says да + backup";

    private static final Logger log = LoggerFactory.getLogger(MediaOrient.class);

    private static final List<String> FOLDERS = List.of("avatars", "logos", "registration", "crests");

    private MediaOrient() {
    }

    /**
     * @return how many files were rewritten in place
     */
    public static int rewriteTree(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return 0;
        }
        int changed = 0;
        for (String folder : FOLDERS) {
            Path dir = root.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                for (Path file : files) {
                    if (rewriteFile(file, folder)) {
                        changed++;
                    }
                }
            } catch (IOException ex) {
                log.warn("Could not scan {} for EXIF orientation", dir);
            }
        }
        return changed;
    }

    private static boolean rewriteFile(Path file, String folder) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        if (!Files.isRegularFile(file) || ImageVariants.isDerivedName(name) || name.endsWith(".skip") || name.endsWith(".part")) {
            return false;
        }
        try {
            byte[] raw = Files.readAllBytes(file);
            ImageOrientation.Prepared prepared = ImageOrientation.prepare(raw, folder, false);
            if (!prepared.changed() || !sameExtension(name, prepared.extension())) {
                return false;
            }
            Path tmp = file.resolveSibling(name + ".orient-part");
            Files.write(tmp, prepared.bytes());
            moveIntoPlace(tmp, file);
            discardSidecars(file, name);
            return true;
        } catch (IOException ex) {
            log.warn("Could not reorient {}", name);
            return false;
        }
    }

    private static boolean sameExtension(String filename, String extension) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1 || extension == null) {
            return false;
        }
        String current = filename.substring(dot + 1);
        return current.equalsIgnoreCase(extension) || ("jpeg".equalsIgnoreCase(current) && "jpg".equalsIgnoreCase(extension));
    }

    private static void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void discardSidecars(Path file, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path dir = file.getParent();
        if (dir == null) {
            return;
        }
        try (DirectoryStream<Path> sidecars = Files.newDirectoryStream(dir, stem + ".__v*")) {
            for (Path sidecar : sidecars) {
                Files.deleteIfExists(sidecar);
            }
        }
    }
}
