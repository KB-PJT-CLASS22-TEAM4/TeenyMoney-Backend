package com.teenyfin.teenymoney.domain.quest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestRejectRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestDetailResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListResponseDTO;
import com.teenyfin.teenymoney.domain.quest.service.QuestCreationService;
import com.teenyfin.teenymoney.domain.quest.service.QuestProgressService;
import com.teenyfin.teenymoney.domain.quest.service.QuestQueryService;
import com.teenyfin.teenymoney.domain.quest.service.QuestReviewService;
import com.teenyfin.teenymoney.domain.quest.vo.AfterDeadlineAction;
import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestTab;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class QuestControllerTest {

    private static final String REQUEST_KEY = "11111111-1111-1111-1111-111111111111";

    private final QuestProgressService questProgressService = mock(QuestProgressService.class);
    private final QuestCreationService creationService = mock(QuestCreationService.class);
    private final QuestQueryService queryService = mock(QuestQueryService.class);
    private final QuestReviewService reviewService = mock(QuestReviewService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new QuestController(
                                questProgressService,
                                creationService,
                                queryService,
                                reviewService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(1L, "PARENT"), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("생성은 인증된 부모와 요청 키와 본문을 서비스에 전달한다")
    void createPassesPrincipalRequestKeyAndBodyToService() throws Exception {
        given(creationService.create(any(), any(), eq(REQUEST_KEY)))
                .willReturn(List.of(101L, 102L));

        var response = mockMvc.perform(post("/quests")
                        .header("X-Creation-Request-Key", REQUEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequest())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"data\":[101,102]"), body);

        verify(creationService).create(
                eq(new MemberPrincipal(1L, "PARENT")), any(QuestCreateRequestDTO.class), eq(REQUEST_KEY));
    }

    @Test
    @DisplayName("생성 요청 키가 없으면 400이다")
    void createWithoutRequestKeyReturns400() throws Exception {
        var response = mockMvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequest())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"COMMON_MISSING_HEADER\""), body);
    }

    @Test
    @DisplayName("잘못된 생성 본문은 필드 오류와 함께 400이다")
    void invalidCreateBodyReturns400WithFieldErrors() throws Exception {
        var response = mockMvc.perform(post("/quests")
                        .header("X-Creation-Request-Key", REQUEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"COMMON_INVALID_INPUT\""), body);
        assertTrue(body.contains("\"childIds\""), body);
        assertTrue(body.contains("\"title\""), body);
    }

    @Test
    @DisplayName("목록은 탭과 선택 필터를 서비스에 전달한다")
    void listPassesTabAndOptionalFiltersToService() throws Exception {
        given(queryService.getQuests(any(), eq(QuestTab.AVAILABLE), eq(2L), eq("cursor")))
                .willReturn(new QuestListResponseDTO(List.of(), null));

        var response = mockMvc.perform(get("/quests")
                        .param("tab", "AVAILABLE")
                        .param("childId", "2")
                        .param("cursor", "cursor"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"items\":[]"), body);
        assertTrue(body.contains("\"nextCursor\":null"), body);

        verify(queryService).getQuests(
                new MemberPrincipal(1L, "PARENT"), QuestTab.AVAILABLE, 2L, "cursor");
    }

    @Test
    @DisplayName("수정은 저장한 뒤 최신 상세를 반환한다")
    void updateReturnsFreshDetailAfterSaving() throws Exception {
        QuestDetailResponseDTO detail = detail();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail);

        var response = mockMvc.perform(patch("/quests/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateRequest())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);
        assertFalse(body.contains("\"acceptedAt\""), body);

        verify(creationService).update(
                eq(new MemberPrincipal(1L, "PARENT")), eq(55L), any(QuestUpdateRequestDTO.class));
        verify(queryService).getQuest(new MemberPrincipal(1L, "PARENT"), 55L);
    }

    @Test
    @DisplayName("삭제는 data null로 응답한다")
    void deleteRespondsWithNullData() throws Exception {
        var response = mockMvc.perform(delete("/quests/55"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"data\":null"), body);

        verify(creationService).delete(new MemberPrincipal(1L, "PARENT"), 55L);
    }

    @Test
    @DisplayName("수락은 자녀와 퀘스트 ID를 서비스에 전달하고 최신 상세를 반환한다")
    void acceptPassesChildAndQuestIdToServiceAndReturnsFreshDetail() throws Exception {
        asChild();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());

        var response = mockMvc.perform(patch("/quests/55/accept"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);

        verify(questProgressService).accept(new MemberPrincipal(2L, "CHILD"), 55L);
        verify(queryService).getQuest(new MemberPrincipal(2L, "CHILD"), 55L);
    }

    @Test
    @DisplayName("수락은 본문을 받지 않는다")
    void acceptTakesNoRequestBody() throws Exception {
        asChild();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());

        var response = mockMvc.perform(patch("/quests/55/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andReturn().getResponse();

        // 본문을 보내도 무시된다. 상태는 서버가 정하지 클라이언트가 지정하지 못한다.
        assertEquals(200, response.getStatus(),
                response.getContentAsString(StandardCharsets.UTF_8));
        verify(questProgressService).accept(new MemberPrincipal(2L, "CHILD"), 55L);
    }

    @Test
    @DisplayName("거절은 사유를 서비스에 전달하고 최신 상세를 반환한다")
    void declinePassesReasonToServiceAndReturnsFreshDetail() throws Exception {
        asChild();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());
        ArgumentCaptor<QuestDeclineRequestDTO> captor =
                ArgumentCaptor.forClass(QuestDeclineRequestDTO.class);

        var response = mockMvc.perform(patch("/quests/55/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(QuestDeclineRequestDTO.builder()
                                .reasonCode(DeclineReasonCode.NOT_ENOUGH_TIME)
                                .reasonDetail("학원이 있어요")
                                .build())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);

        verify(questProgressService).decline(
                eq(new MemberPrincipal(2L, "CHILD")), eq(55L), captor.capture());
        assertEquals(DeclineReasonCode.NOT_ENOUGH_TIME, captor.getValue().getReasonCode());
        assertEquals("학원이 있어요", captor.getValue().getReasonDetail());
    }

    @Test
    @DisplayName("사유 코드가 없는 거절은 필드 오류와 함께 400이고 서비스를 부르지 않는다")
    void declineWithoutReasonCodeReturns400WithFieldError() throws Exception {
        asChild();

        var response = mockMvc.perform(patch("/quests/55/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"COMMON_INVALID_INPUT\""), body);
        // @Valid 가 붙어 있어야 어느 필드가 문제인지 응답에 담긴다.
        assertTrue(body.contains("\"reasonCode\""), body);

        verify(questProgressService, never()).decline(any(), any(), any());
    }

    @Test
    @DisplayName("상세 사유가 500자를 넘으면 400이다")
    void declineWithTooLongDetailReturns400() throws Exception {
        asChild();

        var response = mockMvc.perform(patch("/quests/55/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(QuestDeclineRequestDTO.builder()
                                .reasonCode(DeclineReasonCode.OTHER)
                                .reasonDetail("가".repeat(501))
                                .build())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"reasonDetail\""), body);

        verify(questProgressService, never()).decline(any(), any(), any());
    }

    @Test
    @DisplayName("인증 제출은 글과 사진을 서비스에 전달하고 최신 상세를 반환한다")
    void submitVerificationPassesContentAndImageToService() throws Exception {
        asChild();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());
        MockMultipartFile image = new MockMultipartFile(
                "image", "proof.png", "image/png", new byte[] {1, 2, 3});

        var response = mockMvc.perform(multipart("/quests/55/verifications")
                        .file(image)
                        .param("content", "다 했어요"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);

        verify(questProgressService).submitVerification(
                eq(new MemberPrincipal(2L, "CHILD")), eq(55L), eq("다 했어요"), eq(image));
    }

    @Test
    @DisplayName("인증 제출은 글도 사진도 없이 컨트롤러를 통과한다")
    void submitVerificationAllowsEmptyPartsAtControllerLevel() throws Exception {
        asChild();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());

        var response = mockMvc.perform(multipart("/quests/55/verifications"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));

        // 무엇이 필수인지는 퀘스트마다 다르므로 컨트롤러가 아니라 서비스가 판단한다.
        verify(questProgressService).submitVerification(
                new MemberPrincipal(2L, "CHILD"), 55L, null, null);
    }

    @Test
    @DisplayName("인증 승인은 부모와 두 ID를 서비스에 전달하고 갱신된 전체 상세를 반환한다")
    void approveVerificationReturnsFreshFullDetail() throws Exception {
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());

        var response = mockMvc.perform(
                        patch("/quests/55/verifications/9/approve"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);
        assertTrue(body.contains("\"rewardAmount\":1000"), body);
        assertTrue(body.contains("\"teenyScoreEnabled\":true"), body);
        verify(reviewService).approve(
                new MemberPrincipal(1L, "PARENT"), 55L, 9L);
        verify(queryService).getQuest(
                new MemberPrincipal(1L, "PARENT"), 55L);
    }

    @Test
    @DisplayName("인증 반려는 사유와 기한 후 선택을 서비스에 전달하고 갱신된 전체 상세를 반환한다")
    void rejectVerificationPassesReviewRequestAndReturnsFreshDetail()
            throws Exception {
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());
        ArgumentCaptor<QuestRejectRequestDTO> captor =
                ArgumentCaptor.forClass(QuestRejectRequestDTO.class);
        LocalDateTime extension = LocalDateTime.of(2026, 8, 20, 20, 0);

        var response = mockMvc.perform(
                        patch("/quests/55/verifications/9/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        QuestRejectRequestDTO.builder()
                                                .reason("다시 확인해 주세요")
                                                .afterDeadlineAction(
                                                        AfterDeadlineAction.EXTEND)
                                                .extendedDeadline(extension)
                                                .build())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);
        verify(reviewService).reject(
                eq(new MemberPrincipal(1L, "PARENT")),
                eq(55L),
                eq(9L),
                captor.capture());
        assertEquals("다시 확인해 주세요", captor.getValue().getReason());
        assertEquals(AfterDeadlineAction.EXTEND,
                captor.getValue().getAfterDeadlineAction());
        assertEquals(extension, captor.getValue().getExtendedDeadline());
    }

    @Test
    @DisplayName("반려 사유 없이도 인증을 반려할 수 있다")
    void rejectVerificationWithoutReasonIsAllowed() throws Exception {
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail());
        ArgumentCaptor<QuestRejectRequestDTO> captor =
                ArgumentCaptor.forClass(QuestRejectRequestDTO.class);

        var response = mockMvc.perform(
                        patch("/quests/55/verifications/9/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);
        verify(reviewService).reject(
                eq(new MemberPrincipal(1L, "PARENT")),
                eq(55L),
                eq(9L),
                captor.capture());
        assertNull(captor.getValue().getReason());
    }

    /** 수락·거절·인증 제출은 자녀 전용이라 setUp 의 PARENT 인증을 CHILD 로 바꾼다. */
    private void asChild() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(2L, "CHILD"), null, List.of()));
    }

    private QuestCreateRequestDTO createRequest() {
        return QuestCreateRequestDTO.builder()
                .childIds(List.of(2L, 3L))
                .title("방 청소")
                .content("책상까지 정리")
                .deadline(LocalDateTime.of(2026, 8, 11, 20, 0))
                .rewardAmount(1_000L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.PHOTO_REQUIRED)
                .build();
    }

    private QuestUpdateRequestDTO updateRequest() {
        return QuestUpdateRequestDTO.builder()
                .title("방 청소")
                .content("책상까지 정리")
                .deadline(LocalDateTime.of(2026, 8, 12, 20, 0))
                .rewardAmount(1_000L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.PHOTO_REQUIRED)
                .build();
    }

    private QuestDetailResponseDTO detail() {
        QuestVO quest = QuestVO.builder()
                .id(55L)
                .childId(2L)
                .childName("김티니")
                .title("방 청소")
                .content("책상까지 정리")
                .deadline(LocalDateTime.of(2026, 8, 12, 20, 0))
                .rewardAmount(1_000L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.PHOTO_REQUIRED)
                .status(QuestStatus.AVAILABLE)
                .remainingCount(3)
                .build();
        return QuestDetailResponseDTO.of(quest, null, null);
    }
}
