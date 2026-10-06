package com.studentleague.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidecar copies next to the original upload. Avatars and crests fit inside 128px.
 * Gallery and hero images are no wider than 1600px. The public URL stays the original
 * name; the read path serves the sidecar when it can build one.
 */
public final class ImageVariants {

    private static final Logger log = LoggerFactory.getLogger(ImageVariants.class);

    static final int AVATAR_EDGE = 128;
    static final int GALLERY_MAX_WIDTH = 1600;
    private static final String AVATAR_MARK = ".__v128.";
    private static final String GALLERY_MARK = ".__v1600.";

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private ImageVariants() {
    }

    public static void writeForFolder(Path original, String folder) {
        Variant variant = variantForFolder(folder);
        if (variant == null || original == null) {
            return;
        }
        try {
            ensure(original, variant);
        } catch (RuntimeException ex) {
            log.warn("Could not prepare image variant for {}", original.getFileName());
        }
    }

    /**
     * @return the file to send for this request: the sidecar when it exists or
     *         was just created, otherwise the original (decode failure or unknown folder)
     */
    public static Path servedFile(Path original, String requestPath) {
        if (original == null) {
            return null;
        }
        String filename = original.getFileName() == null ? "" : original.getFileName().toString();
        if (isDerivedName(filename)) {
            return original;
        }
        Variant variant = variantForPath(requestPath);
        if (variant == null) {
            return original;
        }
        try {
            if (ensure(original, variant)) {
                return derivedPath(original, variant);
            }
        } catch (RuntimeException ex) {
            log.debug("Serving original image {}", filename);
        }
        return original;
    }

    static boolean ensure(Path original, Variant variant) {
        Path derived = derivedPath(original, variant);
        if (Files.isRegularFile(derived)) {
            return true;
        }
        Path skip = skipPath(derived);
        if (Files.isRegularFile(skip)) {
            return false;
        }
        Object lock = LOCKS.computeIfAbsent(derived.toString(), key -> new Object());
        synchronized (lock) {
            try {
                if (Files.isRegularFile(derived)) {
                    return true;
                }
                if (Files.isRegularFile(skip)) {
                    return false;
                }
                if (!Files.isRegularFile(original)) {
                    return false;
                }
                return writeOnce(original, derived, skip, variant);
            } catch (Exception ex) {
                markSkipped(skip, derived);
                return false;
            }
        }
    }

    private static boolean writeOnce(Path original, Path derived, Path skip, Variant variant) throws IOException {
        BufferedImage src;
        try {
            src = ImageIO.read(original.toFile());
        } catch (IOException ex) {
            markSkipped(skip, derived);
            return false;
        }
        if (src == null) {
            markSkipped(skip, derived);
            return false;
        }
        BufferedImage fitted = fit(src, variant.maxWidth(), variant.maxHeight());
        Path tmp = derived.resolveSibling(derived.getFileName().toString() + ".part");
        try {
            if (fitted == src) {
                Files.copy(original, tmp, StandardCopyOption.REPLACE_EXISTING);
            } else if (!writeImage(fitted, extension(original.getFileName().toString()), tmp)) {
                markSkipped(skip, derived);
                return false;
            }
            moveIntoPlace(tmp, derived);
            return true;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void moveIntoPlace(Path tmp, Path derived) throws IOException {
        try {
            Files.move(tmp, derived, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, derived, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void markSkipped(Path skip, Path derived) {
        try {
            Files.deleteIfExists(derived);
            if (!Files.exists(skip)) {
                Files.write(skip, new byte[0]);
            }
        } catch (IOException ex) {
            log.debug("Could not mark image variant as skipped");
        }
    }

    static BufferedImage fit(BufferedImage src, int maxWidth, int maxHeight) {
        int width = src.getWidth();
        int height = src.getHeight();
        double scale = 1.0;
        if (width > maxWidth && maxWidth > 0) {
            scale = Math.min(scale, maxWidth / (double) width);
        }
        if (maxHeight > 0 && maxHeight != Integer.MAX_VALUE && height > maxHeight) {
            scale = Math.min(scale, maxHeight / (double) height);
        }
        if (scale >= 1.0) {
            return src;
        }
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(
                targetWidth,
                targetHeight,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    private static boolean writeImage(BufferedImage image, String extension, Path target) throws IOException {
        String format = "jpg".equals(extension) || "jpeg".equals(extension) ? "jpeg" : extension;
        if ("jpeg".equals(format)) {
            return writeJpeg(image, target);
        }
        if (format.isBlank()) {
            return false;
        }
        return ImageIO.write(image, format, target.toFile());
    }

    private static boolean writeJpeg(BufferedImage image, Path target) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return false;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            if (output == null) {
                return false;
            }
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.85f);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
            return true;
        } finally {
            writer.dispose();
        }
    }

    static Path derivedPath(Path original, Variant variant) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String derived = dot > 0
                ? name.substring(0, dot) + variant.mark() + name.substring(dot + 1)
                : name + variant.mark() + "img";
        return original.resolveSibling(derived);
    }

    private static Path skipPath(Path derived) {
        return derived.resolveSibling(derived.getFileName().toString() + ".skip");
    }

    static boolean isDerivedName(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.contains(AVATAR_MARK) || lower.contains(GALLERY_MARK);
    }

    static Variant variantForPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        String normalized = requestPath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int slash = normalized.indexOf('/');
        String folder = slash < 0 ? "" : normalized.substring(0, slash);
        return variantForFolder(folder);
    }

    static Variant variantForFolder(String folder) {
        if (folder == null) {
            return null;
        }
        String name = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return switch (name) {
            case "avatars", "logos", "crests", "registration" -> Variant.AVATAR;
            case "gallery" -> Variant.GALLERY;
            default -> null;
        };
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    enum Variant {
        AVATAR(AVATAR_EDGE, AVATAR_EDGE, AVATAR_MARK),
        GALLERY(GALLERY_MAX_WIDTH, Integer.MAX_VALUE, GALLERY_MARK);

        private final int maxWidth;
        private final int maxHeight;
        private final String mark;

        Variant(int maxWidth, int maxHeight, String mark) {
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.mark = mark;
        }

        int maxWidth() {
            return maxWidth;
        }

        int maxHeight() {
            return maxHeight;
        }

        String mark() {
            return mark;
        }
    }
}
