package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageFileTest {

    // 실제 파일 선두 바이트. 뒤쪽 내용은 검증하지 않으므로 0으로 채운다.
    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_HEADER =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private static byte[] png(int totalSize) {
        byte[] bytes = new byte[totalSize];
        System.arraycopy(PNG_HEADER, 0, bytes, 0, PNG_HEADER.length);
        return bytes;
    }

    private static MockMultipartFile file(String name, byte[] content) {
        // 세 번째 인자가 Content-Type이다. 검증이 이 값을 믿지 않는다는 것을
        // 드러내려고 전부 image/png로 준다.
        return new MockMultipartFile("file", name, "image/png", content);
    }

    @Test
    void acceptsPng() {
        ImageFile image = ImageFile.validate(file("me.png", png(100)));

        assertThat(image.extension()).isEqualTo("png");
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.bytes()).hasSize(100);
    }

    @Test
    void acceptsJpegRegardlessOfExtensionSpelling() {
        assertThat(ImageFile.validate(file("me.jpg", JPEG_HEADER)).contentType())
                .isEqualTo("image/jpeg");
        // 대문자 확장자도 같은 파일이다. 소문자로 정규화해야 뒤의 switch가 걸린다.
        assertThat(ImageFile.validate(file("me.JPEG", JPEG_HEADER)).extension())
                .isEqualTo("jpeg");
    }

    @Test
    void acceptsWebp() {
        // RIFF....WEBP - 4~7바이트는 파일 크기라 검사하지 않는다.
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

        assertThat(ImageFile.validate(file("me.webp", webp)).contentType())
                .isEqualTo("image/webp");
    }

    @Test
    void rejectsFileDisguisedByExtension() {
        // 확장자만 png이고 내용은 스크립트다. Content-Type도 image/png라고 주장한다.
        // 이 한 건을 막으려고 서버 경유 업로드를 택했다.
        byte[] notAnImage = "#!/bin/sh\necho hi\n".getBytes();

        assertThatThrownBy(() -> ImageFile.validate(file("evil.png", notAnImage)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsRiffThatIsNotWebp() {
        // wav, avi도 RIFF로 시작한다. 8바이트 위치까지 봐야 webp와 구분된다.
        byte[] wav = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45};

        assertThatThrownBy(() -> ImageFile.validate(file("fake.webp", wav)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> ImageFile.validate(file("me.gif", png(100))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> ImageFile.validate(file("noextension", png(100))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsEmptyFilename() {
        // MockMultipartFile은 파일명에 null을 넣어도 빈 문자열로 바꿔 들고 있는다.
        // 실제 MultipartFile 구현은 null을 반환할 수 있어 코드에는 null 가드도 둔다.
        assertThatThrownBy(() -> ImageFile.validate(file(null, png(100))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsOversizedFile() {
        byte[] tooBig = png((int) ImageFile.MAX_BYTES + 1);

        assertThatThrownBy(() -> ImageFile.validate(file("big.png", tooBig)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_FILE_TOO_LARGE);
    }

    @Test
    void acceptsFileExactlyAtLimit() {
        // 경계값. 거부 조건이 5MB '초과'이므로 정확히 5MB는 통과해야 한다.
        ImageFile image = ImageFile.validate(file("limit.png", png((int) ImageFile.MAX_BYTES)));

        assertThat(image.bytes()).hasSize((int) ImageFile.MAX_BYTES);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> ImageFile.validate(file("empty.png", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", StorageErrorCode.STORAGE_FILE_EMPTY);
    }
}
