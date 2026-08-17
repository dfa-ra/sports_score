package com.studentleague.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction for S3-compatible object storage. Phase 2 uses a no-op/local stub.
 */
public interface StorageService {

    /**
     * Stores a file and returns a public URL or object key reference.
     */
    String store(String folder, MultipartFile file);
}
