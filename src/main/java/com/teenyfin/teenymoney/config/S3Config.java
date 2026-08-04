package com.teenyfin.teenymoney.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 인프라 설정 (루트 컨텍스트).
 *
 * RedisConfig와 같은 자리에 둔다. 인프라 클라이언트를 자식 컨텍스트에 두면
 * 컨텍스트가 다시 만들어질 때 커넥션이 함께 새로 생긴다.
 *
 * credentialsProvider를 지정하지 않았다. SDK 기본값이 DefaultCredentialsProvider이고,
 * 그게 환경변수 -> 프로파일 -> EC2 인스턴스 메타데이터 순으로 자격증명을 찾는다.
 * 덕분에 로컬(액세스 키)과 EC2(IAM 역할)에 코드 분기가 없고, EC2에는 키를 두지 않아도 된다.
 *   로컬 : AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경변수
 *   EC2  : 인스턴스 IAM 역할
 *
 * 자격증명 탐색은 지연 실행이다. 값이 없어도 앱은 정상 기동하고 첫 S3 호출에서야
 * 실패한다. JWT_SECRET처럼 기동 시점에 드러나지 않으므로 배포 후 업로드를 한 번 확인한다.
 *
 * 두 클라이언트 모두 close()를 갖고 있고 Spring이 소멸 메서드로 자동 추론하므로
 * destroyMethod를 명시하지 않는다.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
