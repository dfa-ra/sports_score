package com.studentleague.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Хранение загруженных файлов (аватары, логотипы).
 */
public interface StorageService {

    /**
     * Сохраняет файл и возвращает публичный URL.
     */
    String store(String folder, MultipartFile file);
}
