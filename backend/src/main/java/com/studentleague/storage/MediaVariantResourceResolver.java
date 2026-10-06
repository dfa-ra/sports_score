package com.studentleague.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serves the resized sidecar for avatar, crest, gallery and hero URLs.
 * The browser still requests the original /media/... path.
 */
public class MediaVariantResourceResolver extends PathResourceResolver {

    @Override
    @Nullable
    protected Resource resolveResourceInternal(
            @Nullable HttpServletRequest request,
            String requestPath,
            List<? extends Resource> locations,
            ResourceResolverChain chain
    ) {
        Resource resolved = super.resolveResourceInternal(request, requestPath, locations, chain);
        if (resolved == null || !resolved.isReadable()) {
            return null;
        }
        try {
            Path original = resolved.getFile().toPath();
            if (!Files.isRegularFile(original)) {
                return resolved;
            }
            Path served = ImageVariants.servedFile(original, requestPath);
            if (served == null || served.equals(original)) {
                return resolved;
            }
            if (!Files.isRegularFile(served)) {
                return resolved;
            }
            return new FileSystemResource(served);
        } catch (IOException ex) {
            return resolved;
        }
    }
}
