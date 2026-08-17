package com.studentleague.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpStorageService implements StorageService {

    @Override
    public String store(String folder, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        return "local://" + folder + "/" + UUID.randomUUID() + "-" + original;
    }
}
