package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {

    // "내 지갑 보기" 화면에 보여줄 최근 거래 개수 고정값. 매직넘버 대신 이름 붙여서 관리.
    private static final int RECENT_TRANSACTION_LIMIT = 3;

    private final WalletMapper walletMapper;

    public WalletService(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
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

    // 지갑이 없으면 에러 처리.
    private WalletVO findMemberWalletOrThrow(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return wallet;
    }
}
