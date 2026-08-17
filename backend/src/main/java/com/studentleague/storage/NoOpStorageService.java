package com.studentleague.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class NoOpStorageService implements StorageService {

    @Override
    public String store(String folder, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        return "local://" + folder + "/" + UUID.randomUUID() + "-" + original;
    }
}
