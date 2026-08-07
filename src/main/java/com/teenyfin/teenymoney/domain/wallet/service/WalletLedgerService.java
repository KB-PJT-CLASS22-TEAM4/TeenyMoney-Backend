package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletLedgerService {

    private final WalletMapper walletMapper;

    // final + 생성자로만 값을 받는 방식 = "생성자 주입(constructor injection)".
    // Spring이 이 클래스의 빈을 만들 때, WalletMapper 빈을 찾아서 자동으로 생성자에 넣어준다.
    // 필드에 @Autowired 안 붙이고 이렇게 하는 이유: 테스트에서 mock을 직접 넣기 쉽고,
    // "이 클래스는 반드시 WalletMapper가 있어야 동작한다"는 게 코드로 명확히 드러나서

    public WalletLedgerService(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    // amount 유효성 검증. credit/debit 둘 다 쓸 거라 따로 뽑아둔다.
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER_AMOUNT);
        }
    }
    // 지갑을 잠근 채로 조회하고, 없으면 예외. 이것도 credit/debit 공용.
    private WalletVO findWalletForUpdateOrThrow(Long walletId) {
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return wallet;
    }


    // @Transactional: 이 메서드 안의 DB 작업들(지갑 잠금 조회 + 잔액 갱신 + 원장 INSERT)이
    // 전부 하나의 트랜잭션으로 묶인다는 뜻. 중간에 예외가 터지면 이미 실행된 부분까지
    // 전부 롤백된다 (예: 잔액은 바뀌었는데 원장은 안 남는 반쪽짜리 상태 방지).

    @Transactional
    public void credit(Long walletId, Long amount, ReferenceType refType, Long refId, String desc) {
        // 1단계: amount가 0 이하면 여기서 바로 막는다.
        // DB의 CHECK 제약(amount는 항상 양수)까지 안 가고 서비스 레벨에서 먼저 걸러내는 이유는 "왜 실패했는지" 명확한 응답(400 + 메시지)을 주기 위해서다.
        validateAmount(amount);

        // 2단계: 지갑을 FOR UPDATE로 잠근 채로 조회. 지갑이 없으면 여기서 예외.
        WalletVO wallet = findWalletForUpdateOrThrow(walletId);

        // 3단계: "잠근 뒤 읽은 현재 잔액"을 기준으로 새 잔액을 Java에서 직접 계산한다.
        // (DB에 "balance = balance + amount" 같은 식으로 맡기지 않고 Java에서 계산하는 이유:
        //  새 잔액 값을 그대로 T_WLT_HIST_H.balance_after에도 써야 하기 때문에,
        //  계산 결과를 변수로 들고 있어야 두 군데(지갑/원장)에 같은 값을 정확히 넣을 수 있다.)
        Long newBalance = wallet.getBalance() + amount;

        // 4단계: 지갑 잔액을 새 값으로 갱신.
        walletMapper.updateBalance(walletId, newBalance);

        // 5단계: 원장에 CREDIT 한 줄 기입. refType.name()으로 enum을 문자열("CHARGE" 등)로 바꿔서 넘긴다.

        walletMapper.insertWalletHistory(walletId, "CREDIT", amount, newBalance, refType.name(), refId, desc);
    }

    @Transactional
    public void debit(Long walletId, Long amount, ReferenceType refType, Long refId, String desc) {
        // 1단계: amount 유효성 검증 (credit과 동일)
        validateAmount(amount);

        // 2단계: 지갑을 잠근 채로 조회 (credit과 동일)
        WalletVO wallet = findWalletForUpdateOrThrow(walletId);

        // 3단계: debit만의 검증 — 현재 잔액이 출금액보다 적으면 여기서 막는다.
        // 잔액도 안 바뀌고 원장도 안 남긴 채로 바로 예외를 던지므로, 이 시점 이후 아무 변경도 없다.
        if (wallet.getBalance() < amount) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // 4단계: 새 잔액 계산 — credit은 더하고, debit은 뺀다는 차이만 있음.
        Long newBalance = wallet.getBalance() - amount;

        // 5단계: 지갑 잔액 갱신
        walletMapper.updateBalance(walletId, newBalance);
        // 6단계: 원장에 DEBIT 한 줄 기입
        walletMapper.insertWalletHistory(walletId, "DEBIT", amount, newBalance, refType.name(), refId, desc);
    }


}
