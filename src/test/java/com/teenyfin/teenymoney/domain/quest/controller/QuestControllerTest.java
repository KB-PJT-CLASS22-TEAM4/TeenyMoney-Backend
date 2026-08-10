package com.teenyfin.teenymoney.domain.quest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestDetailResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListResponseDTO;
import com.teenyfin.teenymoney.domain.quest.service.QuestCreationService;
import com.teenyfin.teenymoney.domain.quest.service.QuestQueryService;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestTab;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class QuestControllerTest {

    private static final String REQUEST_KEY = "11111111-1111-1111-1111-111111111111";

    private final QuestCreationService creationService = mock(QuestCreationService.class);
    private final QuestQueryService queryService = mock(QuestQueryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new QuestController(creationService, queryService))
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
    void 생성은_인증된_부모와_요청_키와_본문을_서비스에_전달한다() throws Exception {
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
    void 생성_요청_키가_없으면_400이다() throws Exception {
        var response = mockMvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createRequest())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, response.getStatus(), body);
        assertTrue(body.contains("\"code\":\"COMMON_MISSING_HEADER\""), body);
    }

    @Test
    void 잘못된_생성_본문은_필드_오류와_함께_400이다() throws Exception {
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
    void 목록은_탭과_선택_필터를_서비스에_전달한다() throws Exception {
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
    void 수정은_저장한_뒤_최신_상세를_반환한다() throws Exception {
        QuestDetailResponseDTO detail = detail();
        given(queryService.getQuest(any(), eq(55L))).willReturn(detail);

        var response = mockMvc.perform(patch("/quests/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateRequest())))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"questId\":55"), body);

        verify(creationService).update(
                eq(new MemberPrincipal(1L, "PARENT")), eq(55L), any(QuestUpdateRequestDTO.class));
        verify(queryService).getQuest(new MemberPrincipal(1L, "PARENT"), 55L);
    }

    @Test
    void 삭제는_data_null로_응답한다() throws Exception {
        var response = mockMvc.perform(delete("/quests/55"))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"data\":null"), body);

        verify(creationService).delete(new MemberPrincipal(1L, "PARENT"), 55L);
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
