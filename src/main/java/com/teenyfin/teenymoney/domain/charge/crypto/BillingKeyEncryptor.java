package com.teenyfin.teenymoney.domain.charge.crypto;

import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;


// billing_key처럼 "DB엔 암호화해서 저장하지만, 나중에 우리가 다시 원래 값을 꺼내 써야 하는"
// 값을 암호화/복호화하는 전용 컴포넌트. AES라는 대칭키 암호화 방식을 씀 -

@Component
public class BillingKeyEncryptor {

    // AES/GCM/NoPadding: AES 중에서도 "GCM"이라는 모드를 씀. GCM은 암호화뿐 아니라
    // "이 데이터가 중간에 조작되지 않았는지"까지 같이 검증해주는 방식이라 요즘 표준으로 권장됨.
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    // IV(초기화 벡터): 매번 암호화할 때마다 무작위로 새로 만드는 값. 같은 평문을 두 번
    // 암호화해도 매번 다른 암호문이 나오게 해줌(그래야 안전함). GCM 표준 권장 길이가 12바이트.
    private static final int GCM_IV_LENGTH_BYTES = 12;

    // 태그: 데이터가 조작 안 됐는지 검증하는 데 쓰이는 길이(비트 단위). GCM 표준값.
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    // @Value("${charge.billing-key-encryption-key}") : application.properties에 적을
    // Base64로 인코딩된 32바이트(256비트) 키 문자열을 주입받아서, AES가 쓸 수 있는
    // SecretKey 객체로 변환해둠. JWT_SECRET이랑 똑같은 방식으로 만들면 됨
    // (openssl rand -base64 32 명령어로 생성).

    @Autowired
    public BillingKeyEncryptor(@Value("${charge.billing-key-encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(keyBytes, "AES"); }

    // 평문(빌링키) 를 암호화해서 DB에 저장할 수 있는 Base64 문자열로 리턴
    public String encrypt(String plainText) {
        try {
            //이번 암호화에만 쓸 무작위 IV를 새로 만듦.
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 복호화할 때 이 IV를 다시 알아야 해서, 암호문 앞에 IV를 붙여서 하나로 합쳐 저장함.
            // (IV는 비밀값이 아니라 "매번 달라야 하는 값"이라 같이 저장해도 안전함)
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ChargeErrorCode.BILLING_KEY_ENCRYPTION_FAILED);

        }
    }



    // 암호화된 Base64 문자열(DB에서 꺼낸 값)을 다시 원래 빌링키 평문으로 되돌림
    public String decrypt(String encryptedText) {

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // 앞 12바이트는 IV, 나머지는 진짜 암호문 - encrypt()에서 합친 순서 그대로 다시 분리.
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ChargeErrorCode.BILLING_KEY_ENCRYPTION_FAILED);
        }

    }
}
