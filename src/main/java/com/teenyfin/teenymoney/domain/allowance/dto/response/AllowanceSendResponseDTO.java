package com.teenyfin.teenymoney.domain.allowance.dto.response;


import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import lombok.Getter;

@Getter
public class AllowanceSendResponseDTO {

    private final Long transferId;
    private final String status;
    private final Long amount;

    public AllowanceSendResponseDTO(TransferVO transfer) {
        this.transferId = transfer.getId();
        this.status = transfer.getStatus();
        this.amount = transfer.getAmount();
    }

    public static AllowanceSendResponseDTO of(TransferVO transfer) {
        return new AllowanceSendResponseDTO(transfer);
    }
}
