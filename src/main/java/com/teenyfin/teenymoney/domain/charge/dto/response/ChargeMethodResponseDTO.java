package com.teenyfin.teenymoney.domain.charge.dto.response;

import com.teenyfin.teenymoney.domain.charge.vo.ChargeMethodVO;
import lombok.Getter;

// 결제수단 목록/등록 API가 프론트에 내려주는 응답 모양.
// billingKey는 절대 여기 안 넣음 - 그건 우리 서버랑 토스만 알아야 하는 값이지, 프론트가 알 필요도
// 없고 알아서도 안 됨. type + 카드필드(카드 아니면 null) + 계좌필드(계좌 아니면 null)를 전부 선언
@Getter
public class ChargeMethodResponseDTO {
    private final Long id;
    private final String type;
    private final String cardCompany;
    private final String maskedCardNumber;
    private final String accountBankName;
    private final String accountNumber;
    private final boolean primary;
    private final String status;

    public ChargeMethodResponseDTO(ChargeMethodVO chargeMethod) {
        this.id = chargeMethod.getId();
        this.type = chargeMethod.getType();
        this.cardCompany = chargeMethod.getCardCompany();
        this.maskedCardNumber = chargeMethod.getMaskedCardNumber();
        this.accountBankName = chargeMethod.getAccountBankName();
        this.accountNumber = chargeMethod.getAccountNumber();
        this.primary = chargeMethod.isPrimary();
        this.status = chargeMethod.getStatus();
    }

    public static ChargeMethodResponseDTO of(ChargeMethodVO chargeMethod) {
        return new ChargeMethodResponseDTO(chargeMethod);
    }

}
