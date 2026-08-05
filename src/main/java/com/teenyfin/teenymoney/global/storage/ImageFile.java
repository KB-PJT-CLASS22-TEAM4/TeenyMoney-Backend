package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * 검증을 통과한 업로드 이미지.
 *
 * validate()로만 만들 수 있고, 인스턴스가 존재한다는 것 자체가 "확장자와 실제
 * 바이트가 일치하는 5MB 이하 이미지"라는 뜻이다. 서비스 계층이 다시 검사할 필요가 없다.
 *
 * Content-Type 헤더는 보지 않는다. 클라이언트가 보내는 주장값이라 위조가 자유롭다.
 * 서버 경유 업로드를 택한 이유가 실제 바이트를 볼 수 있다는 것이므로, 여기서
 * 그걸 쓰지 않으면 그 선택의 근거가 사라진다.
 *
 * 바이트 전체를 메모리에 들고 있는다. 상한이 5MB로 묶여 있어 안전하고,
 * InputStream을 넘기면 magic byte를 읽은 뒤 되감아야 해서 오히려 복잡해진다.
 */
public final class ImageFile {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final byte[] bytes;
    private final String extension;
    private final String contentType;

    private ImageFile(byte[] bytes, String extension, String contentType) {
        this.bytes = bytes;
        this.extension = extension;
        this.contentType = contentType;
    }

    public static ImageFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StorageErrorCode.STORAGE_FILE_EMPTY);
        }
        // 용량은 바이트를 읽기 전에 본다. 5MB 넘는 파일을 메모리에 올리지 않기 위해서다.
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(StorageErrorCode.STORAGE_FILE_TOO_LARGE);
        }

        String extension = extensionOf(file.getOriginalFilename());
        String contentType = contentTypeOf(extension);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // 임시 파일을 읽지 못한 경우다. 사용자가 파일을 바꿔서 해결할 문제가 아니다.
            throw new BusinessException(StorageErrorCode.STORAGE_UPLOAD_FAILED);
        }

        if (!magicMatches(extension, bytes)) {
            throw new BusinessException(StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        }
        return new ImageFile(bytes, extension, contentType);
    }

    public byte[] bytes() {
        return bytes;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 파일명에서 확장자만 소문자로 뽑는다.
     *
     * 원본 파일명은 저장에 쓰지 않는다(S3 key는 UUID로 만든다). 여기서 필요한 것은
     * 확장자 하나뿐이라 경로 조작이나 한글 인코딩을 걱정할 일이 없다.
     *
     * MultipartFile.getOriginalFilename()은 null을 반환할 수 있다(인터페이스 계약).
     */
    private static String extensionOf(String filename) {
        if (filename == null) {
            throw new BusinessException(StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        }
        int dot = filename.lastIndexOf('.');
        // 점이 없거나("me"), 점으로 끝나면("me.") 확장자가 없는 것이다.
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException(StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** S3에 저장할 때 오브젝트 메타데이터로 붙인다. 클라이언트 주장값이 아니라 확장자에서 유도한다. */
    private static String contentTypeOf(String extension) {
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            default:
                throw new BusinessException(StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        }
    }

    private static boolean magicMatches(String extension, byte[] bytes) {
        switch (extension) {
            case "jpg":
            case "jpeg":
                return startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "png":
                return startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "webp":
                // RIFF로 시작하는 포맷은 wav, avi 등 여럿이다. 4~7바이트는 파일 크기라
                // 건너뛰고 8바이트 위치의 "WEBP"까지 봐야 실제로 구분된다.
                return startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                        && startsWithAt(bytes, 8, 0x57, 0x45, 0x42, 0x50);
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        return startsWithAt(bytes, 0, expected);
    }

    /** byte는 부호가 있어 0x89 같은 값이 음수로 나온다. 0xFF로 마스킹해야 비교가 맞는다. */
    private static boolean startsWithAt(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
