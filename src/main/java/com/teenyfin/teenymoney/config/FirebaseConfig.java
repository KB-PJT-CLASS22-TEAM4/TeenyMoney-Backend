package com.teenyfin.teenymoney.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class FirebaseConfig {

    @Value("${aws.s3.bucket}")
    private String BUCKET;

    @Value("${fcm.s3.key-name}")
    private String KEY;

    @PostConstruct
    public void initFirebase() {
        try {
            S3Client s3Client = S3Client.builder()
                    .region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(KEY)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3is = s3Client.getObject(request)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(s3is))
                        .build();

                FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            log.error("Firebase 초기화 실패", e);
            throw new RuntimeException("Firebase 초기화 실패", e);
        }
    }
}
