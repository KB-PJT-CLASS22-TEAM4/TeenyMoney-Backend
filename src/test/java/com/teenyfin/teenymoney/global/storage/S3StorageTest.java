package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3StorageTest {

    private static final byte[] PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3Storage s3Storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);
        s3Storage = new S3Storage(s3Client, s3Presigner, "teenymoney-media", 600L);
    }

    private ImageFile pngImage() {
        return ImageFile.validate(
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "me.png", "image/png", PNG));
    }

    @Test
    void uploadPutsObjectUnderPrefixWithUuidName() {
        String key = s3Storage.upload("profile/17", pngImage());

        assertThat(key).startsWith("profile/17/").endsWith(".png");
        // 원본 파일명(me.png)이 key에 들어가면 안 된다. 경로 조작과 한글 인코딩 문제가 따라온다.
        assertThat(key).doesNotContain("me.png");

        ArgumentCaptor<PutObjectRequest> request =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("teenymoney-media");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void uploadGeneratesDifferentKeyEachTime() {
        String first = s3Storage.upload("profile/17", pngImage());
        String second = s3Storage.upload("profile/17", pngImage());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void uploadTranslatesSdkFailureIntoBusinessException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("boom").build());

        assertThatThrownBy(() -> s3Storage.upload("profile/17", pngImage()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UPLOAD_FAILED);
    }

    @Test
    void presignedUrlReturnsNullForMemberWithoutProfileImage() {
        // 프로필을 설정하지 않은 회원이 대다수다. 이 분기가 없으면 GET /members/me가 바로 터진다.
        assertThat(s3Storage.presignedUrl(null)).isNull();
        assertThat(s3Storage.presignedUrl("")).isNull();
        assertThat(s3Storage.presignedUrl("   ")).isNull();
        verifyNoInteractions(s3Presigner);
    }
}