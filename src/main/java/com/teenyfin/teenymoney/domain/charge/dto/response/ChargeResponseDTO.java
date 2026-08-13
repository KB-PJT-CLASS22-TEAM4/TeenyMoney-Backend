package com.teenyfin.teenymoney.domain.charge.dto.response;

import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import lombok.Getter;

// 충전 API가 프론트에 내려주는 응답 모양. orderId/paymentKey는 우리 서버와 토스만
// 알아야 하는 내부용 값이라 여기 안 넣음 - ChargeMethodResponseDTO가 billingKey를
// 안 넣는 것과 같은 이유.
@Getter
public class ChargeResponseDTO {
    private final Long id;
    private final Long paymentMethodId;
    private final Long amount;
    private final String status;

    public ChargeResponseDTO(ChargeVO charge) {
        this.id = charge.getId();
        this.paymentMethodId = charge.getPaymentMethodId();
        this.amount = charge.getAmount();
        this.status = charge.getStatus();
    }

    public static ChargeResponseDTO of(ChargeVO charge) {
        return new ChargeResponseDTO(charge);
    }
}
