package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionPeriod;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionSortOrder;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionTypeFilter;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletTransactionResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class WalletService {

    // "내 지갑 보기" 화면에 보여줄 최근 거래 개수 고정값. 매직넘버 대신 이름 붙여서 관리.
    private static final int RECENT_TRANSACTION_LIMIT = 3;

    private final WalletMapper walletMapper;
    private final Clock clock;

    public WalletService(WalletMapper walletMapper, Clock clock) {

        this.walletMapper = walletMapper;
        this.clock = clock;
    }

    // 잔액 + 최근 거래내역 3건을 한 번에 반환. 화면(내 지갑 보기)이 이 둘을 항상 같이 쓰기 때문에
    // API도 하나로 합쳐서, 프론트가 한 번의 호출로 화면을 다 그릴 수 있게 한다.
    @Transactional(readOnly = true)
    public WalletDetailResponseDTO getMyWalletDetail(Long memberId) {
        // 1단계: memberId로 "이 회원의 MEMBER 타입 지갑"을 찾는다. 없으면 여기서 예외로 끝남.
        WalletVO wallet = findMemberWalletOrThrow(memberId);

        // 2단계: 그 지갑의 id로 최신 거래내역 3건을 조회한다.
        List<WalletTransactionVO> transactions =
                walletMapper.selectRecentTransactions(wallet.getId(), RECENT_TRANSACTION_LIMIT);

        // 3단계: 지갑 정보 + 거래내역을 하나의 응답 DTO로 합쳐서 반환한다.
        return WalletDetailResponseDTO.of(wallet, transactions);
    }

    // 거래내역 목록 조회. 화면의 종류/기간/정렬 버튼 3개 상태를 받아서,
    // "내 지갑"으로 범위를 먼저 확정한 뒤 그 지갑의 거래만 조회한다.

    @Transactional(readOnly = true)
    public List<WalletTransactionResponseDTO> getMyTransactions(Long memberId, TransactionTypeFilter type, TransactionPeriod period, TransactionSortOrder sort) {

        // 1단계: memberId로 "이 회원의 지갑"을 먼저 확정한다.
        // 여기서 남의 memberId를 넣을 수가 없다 (principal.memberId()는 로그인 토큰에서만 나옴).
        // 그래서 아래 selectTransactions는 항상 "내 지갑"의 id로만 조회되고, 남의 거래내역을
        // 볼 방법이 구조적으로 없다.
        WalletVO wallet = findMemberWalletOrThrow(memberId);

        // 2단계: period(enum)에게 "오늘 날짜를 줄 테니 시작일 계산해줘"라고 요청.
        // clock을 직접 안 쓰고 LocalDate.now(clock)로 감싸는 이유는 테스트 때문이다.
        // 테스트에서는 고정된 가짜 Clock을 넣어서 "오늘"을 원하는 날짜로 고정할 수 있다.
        LocalDate startDate = period.startDateFrom(LocalDate.now(clock));

        // 3단계: enum(TransactionTypeFilter) -> Mapper가 원하는 String으로 변환.
        // ALL이면 null(=SQL에서 direction 조건 자체를 뺀다), 아니면 "CREDIT"/"DEBIT" 문자열.
        String direction = (type == TransactionTypeFilter.ALL) ? null : type.name();

        // 4단계: Mapper 호출. sort.name()은 enum 상수 이름을 그대로 문자열로 바꿔준다.
        // (TransactionSortOrder.ASC.name() == "ASC")
        List<WalletTransactionVO> transactions =
                walletMapper.selectTransactions(wallet.getId(), direction, startDate, sort.name());

        // 5단계: VO 리스트 -> DTO 리스트로 변환해서 반환. (DB 형태를 API 응답 형태로 바꾸는 단계)
        return transactions.stream()
                .map(WalletTransactionResponseDTO::of)
                .toList();

    }

    // 지갑이 없으면 에러 처리.
    private WalletVO findMemberWalletOrThrow(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return wallet;
    }
}
