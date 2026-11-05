package com.studentleague.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * JPEG Exif orientation (tag 0x0112). ImageIO ignores the tag, so phone photos
 * stay sideways unless the pixels themselves are rotated before they are stored.
 */
public final class ImageOrientation {

    private ImageOrientation() {
    }

    /**
     * @return 1 when the tag is missing, unreadable, or already upright
     */
    public static int orientation(byte[] raw) {
        try {
            return readOrientation(raw);
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    /**
     * Upright pixels with no orientation tag. Logos are then centered on a square
     * transparent canvas and written as PNG. Decode failure returns {@code raw}.
     */
    public static byte[] storedBytes(byte[] raw, boolean squareLogo) {
        if (raw == null || raw.length == 0) {
            return raw;
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException ex) {
            return raw;
        }
        if (decoded == null) {
            return raw;
        }
        BufferedImage upright = apply(decoded, orientation(raw));
        try {
            if (squareLogo) {
                return write(square(upright), "png");
            }
            if (isPng(raw)) {
                return write(upright, "png");
            }
            if (isJpeg(raw)) {
                return write(upright, "jpeg");
            }
            return write(upright, "png");
        } catch (IOException ex) {
            return raw;
        }
    }

    static boolean isJpeg(byte[] raw) {
        return raw != null
                && raw.length >= 3
                && (raw[0] & 0xFF) == 0xFF
                && (raw[1] & 0xFF) == 0xD8
                && (raw[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] raw) {
        return raw != null
                && raw.length >= 8
                && (raw[0] & 0xFF) == 0x89
                && raw[1] == 0x50
                && raw[2] == 0x4E
                && raw[3] == 0x47;
    }

    static BufferedImage apply(BufferedImage src, int orientation) {
        if (src == null || orientation < 2 || orientation > 8) {
            return src;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage dst = new BufferedImage(
                swap ? height : width,
                swap ? width : height,
                src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = dst.createGraphics();
        try {
            graphics.setTransform(transform(orientation, width, height));
            graphics.drawImage(src, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return dst;
    }

    private static AffineTransform transform(int orientation, int width, int height) {
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                transform.scale(-1, 1);
                transform.translate(-width, 0);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.scale(1, -1);
                transform.translate(0, -height);
            }
            case 5 -> {
                transform.rotate(-Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                transform.scale(-1, 1);
                transform.translate(-height, 0);
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
            }
            case 8 -> {
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
            default -> {
            }
        }
        return transform;
    }

    private static BufferedImage square(BufferedImage src) {
        int side = Math.max(src.getWidth(), src.getHeight());
        BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            int x = (side - src.getWidth()) / 2;
            int y = (side - src.getHeight()) / 2;
            graphics.drawImage(src, x, y, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static byte[] write(BufferedImage image, String format) throws IOException {
        BufferedImage toWrite = image;
        if ("jpeg".equals(format) && image.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            toWrite = rgb;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(toWrite, format, out) || out.size() == 0) {
            throw new IOException("encode failed");
        }
        return out.toByteArray();
    }

    private static int readOrientation(byte[] raw) {
        if (!isJpeg(raw)) {
            return 1;
        }
        int index = 2;
        while (index + 3 < raw.length) {
            if ((raw[index] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = raw[index + 1] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                return 1;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                index += 2;
                continue;
            }
            int length = ((raw[index + 2] & 0xFF) << 8) | (raw[index + 3] & 0xFF);
            if (length < 2 || index + 2 + length > raw.length) {
                return 1;
            }
            if (marker == 0xE1) {
                int value = readExifOrientation(raw, index + 4, length - 2);
                if (value >= 1 && value <= 8) {
                    return value;
                }
            }
            index += 2 + length;
        }
        return 1;
    }

    private static int readExifOrientation(byte[] raw, int offset, int length) {
        if (length < 8 || offset < 0 || offset + length > raw.length) {
            return 1;
        }
        if (raw[offset] != 'E' || raw[offset + 1] != 'x' || raw[offset + 2] != 'i' || raw[offset + 3] != 'f'
                || raw[offset + 4] != 0 || raw[offset + 5] != 0) {
            return 1;
        }
        return readTiffOrientation(raw, offset + 6, length - 6);
    }

    private static int readTiffOrientation(byte[] raw, int tiff, int length) {
        if (length < 8 || tiff < 0 || tiff + length > raw.length) {
            return 1;
        }
        boolean little;
        if (raw[tiff] == 'I' && raw[tiff + 1] == 'I') {
            little = true;
        } else if (raw[tiff] == 'M' && raw[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        if (u16(raw, tiff + 2, little) != 42) {
            return 1;
        }
        long ifdOffset = u32(raw, tiff + 4, little);
        if (ifdOffset < 0 || ifdOffset > Integer.MAX_VALUE || tiff + ifdOffset + 2 > tiff + length) {
            return 1;
        }
        int ifd = tiff + (int) ifdOffset;
        int count = u16(raw, ifd, little);
        for (int entryIndex = 0; entryIndex < count; entryIndex++) {
            int entry = ifd + 2 + entryIndex * 12;
            if (entry + 12 > tiff + length) {
                return 1;
            }
            if (u16(raw, entry, little) != 0x0112) {
                continue;
            }
            int type = u16(raw, entry + 2, little);
            long values = u32(raw, entry + 4, little);
            if (values < 1) {
                return 1;
            }
            if (type == 3) {
                return u16(raw, entry + 8, little);
            }
            if (type == 4) {
                long wide = u32(raw, entry + 8, little);
                return wide > Integer.MAX_VALUE ? 1 : (int) wide;
            }
            return 1;
        }
        return 1;
    }

    private static int u16(byte[] raw, int offset, boolean little) {
        if (offset < 0 || offset + 1 >= raw.length) {
            return 0;
        }
        int a = raw[offset] & 0xFF;
        int b = raw[offset + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static long u32(byte[] raw, int offset, boolean little) {
        if (offset < 0 || offset + 3 >= raw.length) {
            return 0;
        }
        long a = raw[offset] & 0xFFL;
        long b = raw[offset + 1] & 0xFFL;
        long c = raw[offset + 2] & 0xFFL;
        long d = raw[offset + 3] & 0xFFL;
        return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
    }
}
