package com.teenyfin.teenymoney.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberChildResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberParentResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberProfileImageResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.MemberService;
import com.teenyfin.teenymoney.domain.member.vo.MemberChildVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@DisplayName("MemberController - 회원 조회와 프로필 이미지 변경 HTTP 계약")
class MemberControllerTest {

    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(17L, "PARENT"), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /members/me -> 토큰의 memberId로 조회하고 회원 정보를 반환한다")
    void getMeUsesAuthenticatedMemberIdAndReturnsMemberInformation() throws Exception {
        when(memberService.getMe(17L)).thenReturn(memberResponse());

        var response = mockMvc.perform(get("/members/me"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        System.out.printf("    입력: GET /members/me (토큰의 memberId=17)%n"
                        + "    기대: 200, 회원 정보 + profileImageUrl에 서명 URL%n"
                        + "    실제: %d, %s%n%n",
                response.getStatus(), body);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"memberId\":17"), body);
        assertTrue(body.contains("\"role\":\"PARENT\""), body);
        assertTrue(body.contains("\"name\":\"Test User\""), body);
        assertTrue(body.contains("\"email\":\"user@example.com\""), body);
        assertTrue(body.contains("\"phoneNumber\":\"01012345678\""), body);
        assertTrue(body.contains("\"birthDate\":\"1990-01-02\""), body);
        assertTrue(body.contains(
                "\"profileImageUrl\":\"https://s3.example.com/signed\""), body);
        verify(memberService).getMe(17L);
    }

    @Test
    @DisplayName("PATCH /members/me/profile-image -> 토큰의 memberId로 처리하고 서명 URL을 반환한다")
    void updateProfileImageUsesAuthenticatedMemberIdAndReturnsSignedUrl() throws Exception {
        when(memberService.updateProfileImage(eq(17L), any(MultipartFile.class)))
                .thenReturn(new MemberProfileImageResponseDTO("https://s3.example.com/signed"));

        var file = new MockMultipartFile(
                "file", "me.png", "image/png", new byte[] {1, 2, 3});

        // multipart(url, ...)의 두 번째 인자는 URI 변수다. 파일은 .file()로 붙여야 한다.
        // 여기에 파일을 넘기면 파트가 없는 요청이 되어 400 MissingServletRequestPartException이 난다.
        var result = mockMvc.perform(multipart("/members/me/profile-image")
                        .file(file)
                        .with(request -> {
                            // multipart()는 POST로 만든다. PATCH로 바꿔야 매핑에 걸린다.
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andReturn();
        var response = result.getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        System.out.printf("    입력: PATCH /members/me/profile-image (file=me.png, 토큰의 memberId=17)%n"
                        + "    기대: 200, profileImageUrl에 서명 URL. memberId는 요청이 아니라 토큰에서%n"
                        + "    실제: %d, %s%n%n",
                response.getStatus(), body);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains(
                "\"profileImageUrl\":\"https://s3.example.com/signed\""), body);
        // 요청 본문이 아니라 토큰의 memberId로 처리해야 남의 프로필을 못 바꾼다.
        verify(memberService).updateProfileImage(eq(17L), any(MultipartFile.class));
    }

    @Test
    @DisplayName("GET /members/me/children -> 토큰의 principal로 위임하고 자녀 배열을 반환한다")
    void getChildrenDelegatesWithAuthenticatedPrincipalAndReturnsArray() throws Exception {
        when(memberService.getChildren(any(MemberPrincipal.class)))
                .thenReturn(List.of(childResponse()));

        var response = mockMvc.perform(get("/members/me/children"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        System.out.printf("    입력: GET /members/me/children (토큰의 memberId=17, PARENT)%n"
                        + "    기대: 200, 자녀 배열 + 서명 URL%n"
                        + "    실제: %d, %s%n%n", response.getStatus(), body);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"childId\":2"), body);
        assertTrue(body.contains("\"name\":\"김첫째\""), body);
        assertTrue(body.contains("\"email\":\"child1@test.com\""), body);
        assertTrue(body.contains("\"teenyScore\":610"), body);
        assertTrue(body.contains("\"balance\":96500"), body);
        assertTrue(body.contains(
                "\"profileImageUrl\":\"https://s3.example.com/signed\""), body);
        // parentId를 요청으로 받으면 남의 자녀를 조회할 수 있다. 토큰에서만 나와야 한다.
        verify(memberService).getChildren(any(MemberPrincipal.class));
    }

    @Test
    @DisplayName("GET /members/me/parent -> 토큰의 principal로 위임하고 부모 정보를 반환한다")
    void getParentDelegatesWithAuthenticatedPrincipalAndReturnsParent() throws Exception {
        when(memberService.getParent(any(MemberPrincipal.class)))
                .thenReturn(parentResponse());

        var response = mockMvc.perform(get("/members/me/parent"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        System.out.printf("    입력: GET /members/me/parent (토큰의 principal로 위임)%n"
                        + "    기대: 200, 부모 정보 + 서명 URL, balance 없음%n"
                        + "    실제: %d, %s%n%n", response.getStatus(), body);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"parentId\":1"), body);
        assertTrue(body.contains("\"name\":\"김부모\""), body);
        assertTrue(body.contains(
                "\"profileImageUrl\":\"https://s3.example.com/signed\""), body);
        // 부모 잔액은 자녀 응답에 절대 실리면 안 된다. VO에 필드가 없으니 통합 리팩터를
        // 시도해 balance를 되살리면 여기서 걸린다.
        assertFalse(body.contains("balance"), body);
        // childId를 요청으로 받으면 남의 부모를 조회할 수 있다. 토큰에서만 나와야 한다.
        verify(memberService).getParent(any(MemberPrincipal.class));
    }

    @Test
    @DisplayName("GET /members/me/parent (미연동) -> 200에 data가 null")
    void getParentReturnsNullDataWhenChildHasNoParent() throws Exception {
        when(memberService.getParent(any(MemberPrincipal.class))).thenReturn(null);

        var response = mockMvc.perform(get("/members/me/parent"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        System.out.printf("    입력: 아직 연동하지 않은 자녀%n"
                        + "    기대: 200, success=true, data=null (404 아님)%n"
                        + "    실제: %d, %s%n%n", response.getStatus(), body);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"success\":true"), body);
        // FE가 이 필드로 연동 유도 화면을 분기한다. 전역 Jackson 설정에
        // @JsonInclude(NON_NULL)이 들어가면 data 자체가 사라지고 여기서 걸린다.
        assertTrue(body.contains("\"data\":null"), body);
    }

    private MemberParentResponseDTO parentResponse() {
        MemberParentVO parent = new MemberParentVO();
        parent.setParentId(1L);
        parent.setName("김부모");
        parent.setProfileImageKey("profile/1/a.png");
        // 서비스가 서명한 URL을 넘긴다. key가 그대로 나가면 안 된다.
        return MemberParentResponseDTO.of(parent, "https://s3.example.com/signed");
    }

    private MemberChildResponseDTO childResponse() {
        MemberChildVO child = new MemberChildVO();
        child.setChildId(2L);
        child.setName("김첫째");
        child.setEmail("child1@test.com");
        child.setProfileImageKey("profile/2/a.png");
        child.setTeenyScore(610);
        child.setBalance(96500L);
        // 서비스가 서명한 URL을 넘긴다. key가 그대로 나가면 안 된다.
        return MemberChildResponseDTO.of(child, "https://s3.example.com/signed");
    }

    private MemberMeResponseDTO memberResponse() {
        MemberVO member = new MemberVO();
        member.setId(17L);
        member.setRole("PARENT");
        member.setName("Test User");
        member.setEmail("user@example.com");
        member.setPhoneNumber("01012345678");
        member.setBirthDate(LocalDate.of(1990, 1, 2));
        member.setProfileImageKey("profile/17/9f2c.png");
        // 서비스가 서명한 URL을 넘긴다. key가 그대로 나가면 안 된다.
        return MemberMeResponseDTO.of(member, "https://s3.example.com/signed");
    }
}
