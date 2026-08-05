package com.teenyfin.teenymoney.global.storage;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("ImageFile - 업로드 이미지 검증")
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

    // --- 기대와 실제를 콘솔에 남기는 헬퍼 ----------------------------------------
    //
    // 초록불만으로는 "정말 거부된 것"과 "거부되지 않고 그냥 통과한 것"을 구분할 수 없다.
    // 실제 결과를 문자열로 만들어 출력한 다음 단언하므로, 통과했을 때도 판단 근거가 남는다.

    /**
     * 거부를 기대하는 검증.
     *
     * 예외가 안 났으면 "거부되지 않고 통과함"을 실제 결과로 찍고 실패시킨다.
     * assertThatThrownBy는 이 경우 예외 없이 끝났다는 사실만 알려주고
     * 무엇이 반환됐는지는 남기지 않는다.
     */
    private static void expectRejected(String input, StorageErrorCode expected, ThrowingCallable call) {
        BusinessException thrown = catchThrowableOfType(call, BusinessException.class);

        String actual = (thrown == null)
                ? "거부되지 않고 통과함"
                : "거부됨 (" + thrown.getErrorCode().getCode() + ")";
        System.out.printf("    입력: %s%n    기대: 거부됨 (%s)%n    실제: %s%n%n",
                input, expected.getCode(), actual);

        assertThat(thrown)
                .as("거부되어야 하는데 통과했다: %s", input)
                .isNotNull();
        assertThat(thrown.getErrorCode())
                .as("거부되긴 했으나 사유가 다르다: %s", input)
                .isEqualTo(expected);
    }

    /** 통과를 기대하는 검증에서, 실제로 뽑힌 값을 남긴다. */
    private static void printAccepted(String input, String expected, ImageFile actual) {
        System.out.printf("    입력: %s%n    기대: 통과 (%s)%n    실제: 통과 (확장자=%s, contentType=%s, %d바이트)%n%n",
                input, expected, actual.extension(), actual.contentType(), actual.bytes().length);
    }

    // --- 통과해야 하는 것 -------------------------------------------------------

    @Test
    @DisplayName("정상 png 100바이트 -> 통과. 확장자·contentType·바이트가 그대로 나온다")
    void acceptsPng() {
        ImageFile image = ImageFile.validate(file("me.png", png(100)));
        printAccepted("me.png / 정상 png 100바이트",
                "확장자=png, contentType=image/png, 100바이트", image);

        assertThat(image.extension()).isEqualTo("png");
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.bytes()).hasSize(100);
    }

    @Test
    @DisplayName("jpg와 대문자 JPEG -> 둘 다 통과. 확장자가 소문자로 정규화된다")
    void acceptsJpegRegardlessOfExtensionSpelling() {
        ImageFile lower = ImageFile.validate(file("me.jpg", JPEG_HEADER));
        printAccepted("me.jpg / 정상 jpeg", "contentType=image/jpeg", lower);

        // 대문자 확장자도 같은 파일이다. 소문자로 정규화해야 뒤의 switch가 걸린다.
        ImageFile upper = ImageFile.validate(file("me.JPEG", JPEG_HEADER));
        printAccepted("me.JPEG / 대문자 확장자", "확장자=jpeg (소문자로 정규화)", upper);

        assertThat(lower.contentType()).isEqualTo("image/jpeg");
        assertThat(upper.extension()).isEqualTo("jpeg");
    }

    @Test
    @DisplayName("RIFF....WEBP 시그니처 -> 통과. contentType이 image/webp가 된다")
    void acceptsWebp() {
        // RIFF....WEBP - 4~7바이트는 파일 크기라 검사하지 않는다.
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

        ImageFile image = ImageFile.validate(file("me.webp", webp));
        printAccepted("me.webp / RIFF....WEBP", "contentType=image/webp", image);

        assertThat(image.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("정확히 5MB -> 통과. 거부 조건이 '초과'이므로 경계값은 허용")
    void acceptsFileExactlyAtLimit() {
        ImageFile image = ImageFile.validate(file("limit.png", png((int) ImageFile.MAX_BYTES)));
        printAccepted("limit.png / " + ImageFile.MAX_BYTES + "바이트 (상한과 동일)",
                "통과", image);

        assertThat(image.bytes()).hasSize((int) ImageFile.MAX_BYTES);
    }

    // --- 거부해야 하는 것 -------------------------------------------------------

    @Test
    @DisplayName("확장자·Content-Type만 png인 셸 스크립트 -> STORAGE_UNSUPPORTED_TYPE으로 거부")
    void rejectsFileDisguisedByExtension() {
        // 이 한 건을 막으려고 서버 경유 업로드를 택했다.
        byte[] notAnImage = "#!/bin/sh\necho hi\n".getBytes();

        expectRejected("evil.png / Content-Type도 image/png, 내용은 셸 스크립트",
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> ImageFile.validate(file("evil.png", notAnImage)));
    }

    @Test
    @DisplayName("RIFF로 시작하지만 WAVE인 파일 -> STORAGE_UNSUPPORTED_TYPE으로 거부")
    void rejectsRiffThatIsNotWebp() {
        // wav, avi도 RIFF로 시작한다. 8바이트 위치까지 봐야 webp와 구분된다.
        byte[] wav = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45};

        expectRejected("fake.webp / 내용은 wav (RIFF....WAVE)",
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> ImageFile.validate(file("fake.webp", wav)));
    }

    @Test
    @DisplayName("내용은 정상 png인데 확장자가 gif -> STORAGE_UNSUPPORTED_TYPE으로 거부")
    void rejectsUnsupportedExtension() {
        // 내용이 이미지면 통과가 아니라, 허용 목록에 있어야 통과다.
        expectRejected("me.gif / 내용은 정상 png",
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> ImageFile.validate(file("me.gif", png(100))));
    }

    @Test
    @DisplayName("점이 없는 파일명 -> STORAGE_UNSUPPORTED_TYPE으로 거부")
    void rejectsMissingExtension() {
        expectRejected("noextension / 확장자 없음, 내용은 정상 png",
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> ImageFile.validate(file("noextension", png(100))));
    }

    @Test
    @DisplayName("빈 파일명 -> STORAGE_UNSUPPORTED_TYPE으로 거부")
    void rejectsEmptyFilename() {
        // MockMultipartFile은 파일명에 null을 넣어도 빈 문자열로 바꿔 들고 있는다.
        // 실제 MultipartFile 구현은 null을 반환할 수 있어 코드에는 null 가드도 둔다.
        expectRejected("(빈 파일명) / 내용은 정상 png",
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE,
                () -> ImageFile.validate(file(null, png(100))));
    }

    @Test
    @DisplayName("5MB + 1바이트 -> STORAGE_FILE_TOO_LARGE로 거부")
    void rejectsOversizedFile() {
        byte[] tooBig = png((int) ImageFile.MAX_BYTES + 1);

        expectRejected("big.png / " + (ImageFile.MAX_BYTES + 1) + "바이트",
                StorageErrorCode.STORAGE_FILE_TOO_LARGE,
                () -> ImageFile.validate(file("big.png", tooBig)));
    }

    @Test
    @DisplayName("0바이트 파일 -> STORAGE_FILE_EMPTY로 거부")
    void rejectsEmptyFile() {
        expectRejected("empty.png / 0바이트",
                StorageErrorCode.STORAGE_FILE_EMPTY,
                () -> ImageFile.validate(file("empty.png", new byte[0])));
    }
}
