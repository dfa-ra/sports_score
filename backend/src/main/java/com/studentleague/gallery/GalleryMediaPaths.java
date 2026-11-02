package com.studentleague.gallery;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Public /media paths stored on gallery rows, without the resized sidecar mark. */
public final class GalleryMediaPaths {

    private GalleryMediaPaths() {
    }

    public static String requestPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String path = uri;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        path = decode(path);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return stripVariant(path);
    }

    public static String photoPath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int media = url.indexOf("/media/");
        if (media < 0) {
            return null;
        }
        String path = url.substring(media);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return stripVariant(decode(path));
    }

    private static String stripVariant(String path) {
        return path.replace(".__v1600.", ".").replace(".__v128.", ".");
    }

    private static String decode(String path) {
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return path;
        }
    }
}
