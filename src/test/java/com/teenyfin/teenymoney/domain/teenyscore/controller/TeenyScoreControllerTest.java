package com.teenyfin.teenymoney.domain.teenyscore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreGradeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class TeenyScoreControllerTest {

    private TeenyScoreService teenyScoreService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        teenyScoreService = mock(TeenyScoreService.class);
        ObjectMapper objectMapper =
                new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TeenyScoreController(teenyScoreService))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getTeenyScoreReturnsCurrentScoreAndGrade() throws Exception {
        when(teenyScoreService.getTeenyScore(2L))
                .thenReturn(TeenyScoreResponseDTO.of(teenyScore()));

        var response = mockMvc.perform(
                        get("/teeny-score/children/{childId}", 2L))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"success\":true"), body);
        assertTrue(body.contains("\"code\":\"OK\""), body);
        assertTrue(body.contains("\"childId\":2"), body);
        assertTrue(body.contains("\"teenyScore\":610"), body);
        assertTrue(body.contains("\"gradeName\":\"양호\""), body);
        assertTrue(body.contains("\"bonusRate\":0.20"), body);

        verify(teenyScoreService).getTeenyScore(2L);
    }

    @Test
    void getHistoriesReturnsNewestHistoryList() throws Exception {
        when(teenyScoreService.getHistories(2L))
                .thenReturn(List.of(
                        TeenyScoreHistoryResponseDTO.of(history())));

        var response = mockMvc.perform(get(
                        "/teeny-score/children/{childId}/history", 2L))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"historyId\":1"), body);
        assertTrue(body.contains("\"amount\":10"), body);
        assertTrue(body.contains("\"scoreAfter\":610"), body);

        verify(teenyScoreService).getHistories(2L);
    }

    @Test
    void getGradesReturnsGradeList() throws Exception {
        when(teenyScoreService.getGrades())
                .thenReturn(List.of(TeenyScoreGradeResponseDTO.of(grade())));

        var response = mockMvc.perform(get("/teeny-score/grades"))
                .andReturn().getResponse();

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatus(), body);
        assertTrue(body.contains("\"data\":[{"), body);
        assertTrue(body.contains("\"gradeId\":4"), body);
        assertTrue(body.contains("\"gradeName\":\"양호\""), body);

        verify(teenyScoreService).getGrades();
    }

    private TeenyScoreVO teenyScore() {
        TeenyScoreVO score = new TeenyScoreVO();
        score.setChildId(2L);
        score.setTeenyScore(610);
        score.setGradeId(4L);
        score.setGradeName("양호");
        score.setMinScore(600);
        score.setMaxScore(799);
        score.setBonusRate(new BigDecimal("0.20"));
        score.setColor("#4CAF50");
        return score;
    }

    private TeenyScoreHistoryVO history() {
        TeenyScoreHistoryVO history = new TeenyScoreHistoryVO();
        history.setHistoryId(1L);
        history.setAmount(10);
        history.setScoreAfter(610);
        history.setDescription("적금납입성공");
        history.setCreatedAt(LocalDateTime.of(2026, 6, 25, 9, 0));
        return history;
    }

    private TeenyScoreGradeVO grade() {
        TeenyScoreGradeVO grade = new TeenyScoreGradeVO();
        grade.setGradeId(4L);
        grade.setGradeName("양호");
        grade.setMinScore(600);
        grade.setMaxScore(799);
        grade.setBonusRate(new BigDecimal("0.20"));
        grade.setColor("#4CAF50");
        return grade;
    }
}
