package com.studentleague.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Bakes JPEG EXIF orientation into pixels (the same idea as ImageMagick {@code -auto-orient})
 * so browsers and {@link ImageVariants} do not show phone photos on their side.
 * A file that is already upright is returned unchanged, so a second pass is a no-op.
 *
 * <p>One-shot for an existing media directory (does not touch the database):
 * {@code java com.studentleague.storage.ImageOrient /path/to/uploads}
 */
public final class ImageOrient {

    private static final int ORIENTATION_TAG = 0x0112;
    private static final int HEAD_BYTES = 64 * 1024;
    private static final long MAX_REWRITE_BYTES = 40L * 1024 * 1024;

    private ImageOrient() {
    }

    public record Summary(int rewritten, int unchanged, int failed) {
    }

    /**
     * @return the same array when the bytes are not a sideways JPEG
     */
    public static byte[] apply(byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }
        int orientation = readOrientation(input);
        if (orientation <= 1) {
            return input;
        }
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException ex) {
            return input;
        }
        if (src == null) {
            return input;
        }
        BufferedImage turned = rotate(src, orientation);
        byte[] encoded = writeJpeg(turned);
        return encoded == null ? input : encoded;
    }

    public static int readOrientation(Path path) {
        if (path == null) {
            return 1;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return readOrientation(in.readNBytes(HEAD_BYTES));
        } catch (IOException ex) {
            return 1;
        }
    }

    static int readOrientation(byte[] data) {
        if (data == null || data.length < 4) {
            return 1;
        }
        if ((data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int i = 2;
        while (i + 3 < data.length) {
            if ((data[i] & 0xFF) != 0xFF) {
                return 1;
            }
            while (i < data.length && (data[i] & 0xFF) == 0xFF) {
                i++;
            }
            if (i >= data.length) {
                return 1;
            }
            int marker = data[i++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                return 1;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (i + 1 >= data.length) {
                return 1;
            }
            int length = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            if (length < 2 || i + length > data.length) {
                return 1;
            }
            if (marker == 0xE1 && length >= 8 && isExif(data, i + 2)) {
                return tiffOrientation(data, i + 8, i + length);
            }
            i += length;
        }
        return 1;
    }

    /**
     * @return {@code src} itself when no turn is required
     */
    static BufferedImage rotate(BufferedImage src, int orientation) {
        if (src == null || orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = x;
                int dy = y;
                switch (orientation) {
                    case 2 -> dx = w - 1 - x;
                    case 3 -> {
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 4 -> dy = h - 1 - y;
                    case 5 -> {
                        dx = y;
                        dy = x;
                    }
                    case 6 -> {
                        dx = h - 1 - y;
                        dy = x;
                    }
                    case 7 -> {
                        dx = h - 1 - y;
                        dy = w - 1 - x;
                    }
                    case 8 -> {
                        dx = y;
                        dy = w - 1 - x;
                    }
                    default -> {
                    }
                }
                out.setRGB(dx, dy, src.getRGB(x, y));
            }
        }
        return out;
    }

    /**
     * Rewrites sideways JPEGs under {@code root} and drops resized sidecars next to them.
     * Other files, including the database, are left in place. Safe to run twice.
     */
    public static Summary rewriteTree(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IOException("Media directory is missing");
        }
        int rewritten = 0;
        int unchanged = 0;
        int failed = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(path) || !jpegName(path) || ImageVariants.isDerivedName(fileName(path))) {
                    continue;
                }
                try {
                    if (Files.size(path) > MAX_REWRITE_BYTES) {
                        unchanged++;
                        continue;
                    }
                    byte[] raw = Files.readAllBytes(path);
                    byte[] next = apply(raw);
                    if (next == raw) {
                        unchanged++;
                        continue;
                    }
                    writeAtomically(path, next);
                    dropSidecars(path);
                    rewritten++;
                } catch (RuntimeException | IOException ex) {
                    failed++;
                }
            }
        }
        return new Summary(rewritten, unchanged, failed);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            System.err.println("Usage: java com.studentleague.storage.ImageOrient <media-directory>");
            System.exit(2);
            return;
        }
        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(2);
            return;
        }
        Summary summary = rewriteTree(root);
        System.out.printf(
                "rewritten=%d unchanged=%d failed=%d%n",
                summary.rewritten(),
                summary.unchanged(),
                summary.failed()
        );
        if (summary.failed() > 0) {
            System.exit(1);
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Path tmp = path.resolveSibling(fileName(path) + ".orient-tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void dropSidecars(Path original) throws IOException {
        String name = fileName(original);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot + 1) : "img";
        for (String mark : new String[] {".__v128.", ".__v1600."}) {
            Path derived = original.resolveSibling(base + mark + ext);
            Files.deleteIfExists(derived);
            Files.deleteIfExists(derived.resolveSibling(derived.getFileName().toString() + ".skip"));
        }
    }

    private static boolean jpegName(Path path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    private static boolean isExif(byte[] data, int offset) {
        return offset + 6 <= data.length
                && data[offset] == 'E'
                && data[offset + 1] == 'x'
                && data[offset + 2] == 'i'
                && data[offset + 3] == 'f'
                && data[offset + 4] == 0
                && data[offset + 5] == 0;
    }

    private static int tiffOrientation(byte[] data, int start, int end) {
        if (end - start < 8) {
            return 1;
        }
        boolean little;
        if (data[start] == 'I' && data[start + 1] == 'I') {
            little = true;
        } else if (data[start] == 'M' && data[start + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        if (u16(data, start + 2, little) != 42) {
            return 1;
        }
        long ifd = u32(data, start + 4, little);
        if (ifd < 0 || ifd > Integer.MAX_VALUE) {
            return 1;
        }
        int pos = start + (int) ifd;
        if (pos < start || pos + 2 > end) {
            return 1;
        }
        int count = u16(data, pos, little);
        pos += 2;
        for (int n = 0; n < count; n++) {
            if (pos + 12 > end) {
                return 1;
            }
            int tag = u16(data, pos, little);
            int type = u16(data, pos + 2, little);
            long values = u32(data, pos + 4, little);
            if (tag == ORIENTATION_TAG && type == 3 && values >= 1) {
                int value = u16(data, pos + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
            pos += 12;
        }
        return 1;
    }

    private static int u16(byte[] data, int offset, boolean little) {
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static long u32(byte[] data, int offset, boolean little) {
        long a = data[offset] & 0xFFL;
        long b = data[offset + 1] & 0xFFL;
        long c = data[offset + 2] & 0xFFL;
        long d = data[offset + 3] & 0xFFL;
        return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
    }

    private static byte[] writeJpeg(BufferedImage image) {
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
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            if (output == null) {
                return null;
            }
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            return null;
        } finally {
            writer.dispose();
        }
    }
}
