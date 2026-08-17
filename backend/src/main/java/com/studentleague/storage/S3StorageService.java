package com.studentleague.storage;

import com.studentleague.common.exception.ApiException;
import com.studentleague.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.s3.enabled", havingValue = "true")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3StorageService(AppProperties appProperties) {
        AppProperties.S3 s3 = appProperties.s3();
        this.bucket = s3.bucket();
        this.publicBaseUrl = (s3.publicBaseUrl() == null || s3.publicBaseUrl().isBlank())
                ? s3.endpoint() + "/" + s3.bucket()
                : s3.publicBaseUrl();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public String store(String folder, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String key = folder + "/" + UUID.randomUUID() + "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
            return publicBaseUrl.endsWith("/") ? publicBaseUrl + key : publicBaseUrl + "/" + key;
        } catch (IOException ex) {
            throw ApiException.badRequest("Failed to read uploaded file");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store object in S3-compatible storage", ex);
        }
    }
}
