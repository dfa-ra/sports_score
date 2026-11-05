package com.studentleague.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Bakes EXIF orientation into pixels and, for team logos, a square transparent canvas.
 * Phone JPEGs often store a sideways picture plus an Orientation tag. ImageIO ignores that tag,
 * so the stored file has to be rotated before it is written under {@code /media}.
 */
public final class ImageOrientation {

    public record Prepared(byte[] bytes, String extension, boolean changed) {
    }

    private ImageOrientation() {
    }

    /**
     * @param squareLogos when true, non-square logos and crests are padded to a square PNG canvas.
     *                    Backfill keeps the original filename, so it passes false and only rotates.
     */
    public static Prepared prepare(byte[] original, String folder, boolean squareLogos) {
        if (original == null || original.length == 0) {
            return new Prepared(original == null ? new byte[0] : original, "bin", false);
        }
        if (!touchFolder(folder)) {
            return new Prepared(original, "bin", false);
        }
        Kind kind = kindOf(original);
        if (kind != Kind.JPEG && kind != Kind.PNG) {
            return new Prepared(original, kind == null ? "bin" : kind.extension, false);
        }
        int orientation = kind == Kind.JPEG ? jpegOrientation(original) : 1;
        boolean square = squareLogos && logoFolder(folder);
        BufferedImage decoded = decode(original);
        if (decoded == null) {
            return new Prepared(original, kind.extension, false);
        }
        BufferedImage turned = upright(decoded, orientation);
        BufferedImage framed = square ? squareCanvas(turned) : turned;
        if (turned == decoded && framed == turned) {
            return new Prepared(original, kind.extension, false);
        }
        boolean alpha = framed.getColorModel().hasAlpha();
        String extension = kind == Kind.JPEG && alpha ? "png" : kind.extension;
        byte[] encoded = encode(framed, extension);
        if (encoded == null || encoded.length == 0) {
            return new Prepared(original, kind.extension, false);
        }
        return new Prepared(encoded, extension, true);
    }

    public static String withExtension(String filename, String extension) {
        if (filename == null || filename.isBlank()) {
            return "image." + extension;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        int dot = filename.lastIndexOf('.');
        String base = dot > slash ? filename.substring(0, dot) : filename;
        return base + "." + extension;
    }

    /**
     * JPEG Orientation tag (1–8). Anything else, including PNG and a missing tag, is 1 (upright).
     */
    public static int jpegOrientation(byte[] data) {
        try {
            return readJpegOrientation(data);
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    static int readJpegOrientation(byte[] data) {
        if (data == null || data.length < 4) {
            return 1;
        }
        if ((data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int i = 2;
        while (i + 4 < data.length) {
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
            if (i + 1 >= data.length) {
                return 1;
            }
            int length = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            if (length < 2 || i + length > data.length) {
                return 1;
            }
            if (marker == 0xE1) {
                int orientation = exifOrientation(data, i + 2, length - 2);
                if (orientation >= 1 && orientation <= 8) {
                    return orientation;
                }
            }
            i += length;
        }
        return 1;
    }

    /**
     * Maps stored pixels into visual order. Orientation 1 returns the same instance.
     * 6 is 90° clockwise (the usual phone landscape tag). 8 is 90° counter-clockwise.
     */
    public static BufferedImage upright(BufferedImage src, int orientation) {
        if (src == null || orientation < 2 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int dw = swap ? h : w;
        int dh = swap ? w : h;
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage dst = new BufferedImage(dw, dh, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] srcPx = src.getRGB(0, 0, w, h, null, 0, w);
        int[] dstPx = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> {
                        dx = w - 1 - x;
                        dy = y;
                    }
                    case 3 -> {
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 4 -> {
                        dx = x;
                        dy = h - 1 - y;
                    }
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
                        dx = x;
                        dy = y;
                    }
                }
                dstPx[dy * dw + dx] = srcPx[y * w + x];
            }
        }
        return dst;
    }

    static BufferedImage squareCanvas(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w == h) {
            return src;
        }
        int edge = Math.max(w, h);
        BufferedImage dst = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = dst.createGraphics();
        try {
            graphics.drawImage(src, (edge - w) / 2, (edge - h) / 2, null);
        } finally {
            graphics.dispose();
        }
        return dst;
    }

    static boolean logoFolder(String folder) {
        String name = folderName(folder);
        return "logos".equals(name) || "crests".equals(name);
    }

    /** Logos and player photos. Gallery and other uploads stay byte-for-byte. */
    static boolean touchFolder(String folder) {
        String name = folderName(folder);
        return "avatars".equals(name) || "logos".equals(name) || "registration".equals(name) || "crests".equals(name);
    }

    private static String folderName(String folder) {
        if (folder == null) {
            return "";
        }
        return folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static BufferedImage decode(byte[] original) {
        try {
            return ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException ex) {
            return null;
        }
    }

    private static byte[] encode(BufferedImage image, String extension) {
        try {
            if ("jpg".equals(extension) || "jpeg".equals(extension)) {
                return writeJpeg(image);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, extension, out)) {
                return null;
            }
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB || image.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(java.awt.Color.WHITE);
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
                param.setCompressionQuality(0.85f);
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static int exifOrientation(byte[] data, int offset, int length) {
        if (length < 16 || offset < 0 || offset + length > data.length) {
            return 1;
        }
        if (data[offset] != 'E' || data[offset + 1] != 'x' || data[offset + 2] != 'i' || data[offset + 3] != 'f'
                || data[offset + 4] != 0 || data[offset + 5] != 0) {
            return 1;
        }
        int tiff = offset + 6;
        int end = offset + length;
        if (tiff + 8 > end) {
            return 1;
        }
        boolean little = data[tiff] == 'I' && data[tiff + 1] == 'I';
        boolean big = data[tiff] == 'M' && data[tiff + 1] == 'M';
        if (!little && !big) {
            return 1;
        }
        if (u16(data, tiff + 2, little) != 42) {
            return 1;
        }
        long ifdOffset = u32(data, tiff + 4, little);
        if (ifdOffset < 0 || tiff + ifdOffset + 2 > end) {
            return 1;
        }
        int ifd = (int) (tiff + ifdOffset);
        int count = u16(data, ifd, little);
        int entry = ifd + 2;
        for (int n = 0; n < count; n++) {
            if (entry + 12 > end) {
                return 1;
            }
            int tag = u16(data, entry, little);
            int type = u16(data, entry + 2, little);
            if (tag == 0x0112 && type == 3) {
                int value = u16(data, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
            entry += 12;
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

    private static Kind kindOf(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return Kind.PNG;
        }
        return null;
    }

    private enum Kind {
        JPEG("jpg"),
        PNG("png");

        private final String extension;

        Kind(String extension) {
            this.extension = extension;
        }
    }
}
