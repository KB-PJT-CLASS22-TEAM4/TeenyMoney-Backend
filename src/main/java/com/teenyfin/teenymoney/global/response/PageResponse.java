package com.teenyfin.teenymoney.global.response;

import lombok.Getter;

import java.util.List;

/**
 * 목록 API의 data에 들어가는 형태. 거래내역, 알림함, 소비리포트 등이 쓴다.
 *
 *   {"success":true,"code":"OK","message":"성공",
 *    "data":{"content":[...],"page":1,"size":20,"totalElements":57,"totalPages":3}}
 *
 * page는 1부터 시작한다. Spring Data(0부터)를 쓰지 않고 SQL을 직접 쓰므로
 * 화면에 보이는 번호와 맞췄다. SQL 오프셋은 (page - 1) * size 로 계산할 것.
 */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    private PageResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResponse<>(content, page, size, totalElements);
    }
}
