package com.teenyfin.teenymoney.domain.allowance.service;


import com.teenyfin.teenymoney.domain.allowance.dto.response.AllowanceSendResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AllowanceService {
    private final FamilyAccessService familyAccessService;
    private final WalletMapper walletMapper;
    private final TransferService transferService;

    public AllowanceService(FamilyAccessService familyAccessService, WalletMapper walletMapper, TransferService transferService) {
        this.familyAccessService = familyAccessService;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
    }

    //부모가 자녀에게 1회성으로 용돈 보내기
    public AllowanceSendResponseDTO sendAllowance(MemberPrincipal principal, Long childId, Long amount, String idempotencyKey) {
        // 1단계 : 이 부모가 이 자녀를 다뤄도 되는지 확인 (가족 연동 여부 + 소유권 검증)
        familyAccessService.requireChildAccess(principal, childId);

        //2단계 부모/자녀 각각의 MEMBER 지갑을 찾는다.
        WalletVO parentWallet = walletMapper.selectMemberWalletByMemberId(principal.memberId());
        if(parentWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        WalletVO childWallet = walletMapper.selectMemberWalletByMemberId(childId);
        if(childWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 3단계: 공통 송금 인프라 호출
        TransferVO pending = transferService.createPendingTransfer(parentWallet.getId(), childWallet.getId(), amount, TransferType.ALLOWANCE, idempotencyKey);

        TransferVO result = transferService.executeTransfer(pending.getId());

        return AllowanceSendResponseDTO.of(result);
    }
 }
