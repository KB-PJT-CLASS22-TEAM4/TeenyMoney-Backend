package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * S3 오브젝트 저장과 조회 URL 발급.
 *
 * 버킷은 비공개다. 저장된 오브젝트의 평문 URL로 접근하면 403이므로, 조회할 때마다
 * 만료되는 서명 URL을 발급해서 내보낸다.
 *
 * 그래서 DB에는 URL이 아니라 key를 저장한다. 서명 URL은 만료되므로 애초에 저장할 수
 * 있는 값이 아니다. 이 클래스가 key <-> 서명 URL 변환을 담당한다.
 *
 * 서명은 쿼리스트링에 실리므로 프론트에서 <img src>에 그대로 꽂을 수 있다.
 *
 * global 패키지의 @Component + 생성자 @Value는 CookieUtil, PhoneVerificationStore와
 * 같은 패턴이다. 자식 컨텍스트가 루트 컨텍스트의 environment를 merge하므로
 * 프로퍼티가 해석되고, 루트에 있는 S3Client/S3Presigner도 주입받을 수 있다.
 */
@Component
@Slf4j
public class S3Storage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration presignTtl;

    public S3Storage(S3Client s3Client,
                     S3Presigner s3Presigner,
                     @Value("${aws.s3.bucket}") String bucket,
                     @Value("${aws.s3.presign-ttl-seconds}") long presignTtlSeconds) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignTtl = Duration.ofSeconds(presignTtlSeconds);
    }

    /**
     * 이미지를 저장하고 오브젝트 key를 반환한다. 반환값을 DB에 저장한다.
     *
     * 파일명은 UUID로 고정한다. 원본 파일명을 쓰면 경로 조작(../)과 한글 파일명
     * 인코딩 문제가 따라오는데, 우리가 원본 파일명에서 필요한 것은 확장자뿐이다.
     *
     * 같은 key로 덮어쓰지 않으므로 교체해도 이전 오브젝트가 남는다. 지우려면
     * IAM에 s3:DeleteObject를 열어야 하고, 업로드는 됐는데 DB 갱신이 실패한 경우까지
     * 생각해야 한다. 스토리지 비용이 실제로 보일 때 라이프사이클 룰로 처리한다.
     *
     * @param keyPrefix 슬래시로 끝나지 않는 접두사. 예: "profile/17"
     */
    public String upload(String keyPrefix, ImageFile image) {
        String key = keyPrefix + "/" + UUID.randomUUID() + "." + image.extension();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            // 클라이언트가 보낸 헤더가 아니라 확장자에서 유도한 값이다.
                            // 주장값을 그대로 저장하면 text/html인 오브젝트가 생기고,
                            // 서명 URL로 열 때 브라우저가 그걸 실행한다.
                            .contentType(image.contentType())
                            .build(),
                    RequestBody.fromBytes(image.bytes()));
        } catch (SdkException e) {
            // 원인(자격증명, 네트워크, 권한)은 로그에만 남긴다. 응답에 넣으면 내부 정보가 샌다.
            log.error("S3 업로드 실패: bucket={}, key={}", bucket, key, e);
            throw new BusinessException(StorageErrorCode.STORAGE_UPLOAD_FAILED);
        }
        return key;
    }

    /**
     * key를 조회용 서명 URL로 바꾼다. 네트워크 호출이 아니라 서명 계산이다.
     *
     * key가 없으면 null을 반환한다. 프로필 이미지를 설정하지 않은 회원이 대다수라
     * 이 분기가 없으면 조회 API가 통째로 실패한다.
     */
    public String presignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        GetObjectRequest getObject = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(presignTtl)
                                .getObjectRequest(getObject)
                                .build())
                .url()
                .toString();
    }
}
