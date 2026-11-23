package com.example.estatex.wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WalletDtos {
    private WalletDtos() {}

    public record EntryResponse(Long id, BigDecimal amount, BigDecimal balanceAfter, String currency, String type,
                                String description, String relatedType, Long relatedId, Instant createdAt) {
        static EntryResponse from(WalletEntry e) {
            return new EntryResponse(e.getId(), WalletService.fromCents(e.getAmountCents()),
                    e.getBalanceAfterCents() == null ? null : WalletService.fromCents(e.getBalanceAfterCents()),
                    e.getCurrency(), e.getType(), e.getDescription(), e.getRelatedType(), e.getRelatedId(), e.getCreatedAt());
        }
    }

    public record WalletResponse(String currency, BigDecimal balance, BigDecimal pendingWithdrawals,
                                 List<EntryResponse> entries, long totalEntries, int page, int size) {}

    public record TopUpRequest(BigDecimal amount, String currency, String provider) {}

    /**
     * method ECOCASH needs ecocashPhone; BANK needs bankAccountName, bankAccountNumber and bankName.
     * password re-confirms who is asking before money leaves the account.
     */
    public record WithdrawalRequest(BigDecimal amount, String currency, String method, String ecocashPhone,
                                    String bankAccountName, String bankAccountNumber, String bankName, String bankBranch,
                                    String password) {}

    public record WithdrawalDecision(String action, String reference, String reason) {}

    public record WithdrawalResponse(Long id, Long userId, String userName, String userEmail, BigDecimal amount,
                                     String currency, String method, String ecocashPhone, String bankAccountName,
                                     String bankAccountNumber, String bankName, String bankBranch, String status,
                                     Instant requestedAt, Instant decidedAt, String payoutReference, String rejectReason) {
        static WithdrawalResponse from(Withdrawal w, String userName, String userEmail) {
            return new WithdrawalResponse(w.getId(), w.getUserId(), userName, userEmail, WalletService.fromCents(w.getAmountCents()),
                    w.getCurrency(), w.getMethod(), w.getEcocashPhone(), w.getBankAccountName(), w.getBankAccountNumber(),
                    w.getBankName(), w.getBankBranch(), w.getStatus(), w.getRequestedAt(), w.getDecidedAt(),
                    w.getPayoutReference(), w.getRejectReason());
        }
    }
}
