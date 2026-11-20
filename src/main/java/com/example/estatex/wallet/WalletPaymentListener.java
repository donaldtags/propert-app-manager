package com.example.estatex.wallet;

import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.stay.StayBooking;
import com.example.estatex.stay.StayBookingRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a successful payment means for wallets.
 *
 * <p>Money rule (no double paying): online payments are collected by EstateX's gateway account,
 * never paid to the landlord or host directly. So when rent or a stay is paid online (Paynow,
 * Stripe) or from a wallet, the payee is credited in their EstateX wallet. A "manual" payment is
 * money the tenant handed over outside EstateX (cash, direct transfer) and only recorded here, so
 * it credits nobody. A wallet payment only counts if the payer's wallet was really debited.
 */
@Component
public class WalletPaymentListener {
    private static final Logger log = LoggerFactory.getLogger(WalletPaymentListener.class);

    private final WalletService wallets;
    private final StayBookingRepository stayBookings;
    private final PlatformAccountService platformAccounts;

    public WalletPaymentListener(WalletService wallets, StayBookingRepository stayBookings, PlatformAccountService platformAccounts) {
        this.wallets = wallets;
        this.stayBookings = stayBookings;
        this.platformAccounts = platformAccounts;
    }

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment payment = event.payment();
        String provider = payment.getProvider();
        boolean viaGateway = wallets.isGateway(provider);
        boolean viaWallet = WalletService.WALLET_PROVIDER.equals(provider) && wallets.paidFromWallet(payment.getId());

        if (payment.getPurposeType() == PaymentPurpose.WALLET_TOP_UP) {
            if (!viaGateway) {
                log.warn("Ignoring wallet top-up payment {} confirmed without a gateway (provider {})", payment.getId(), provider);
                return;
            }
            wallets.post(payment.getPayer().getId(), payment.getCurrency(), WalletService.toCents(payment.getAmount()),
                    WalletEntryType.TOP_UP, WalletService.EXTERNAL_GATEWAY, "Top-up via " + label(provider),
                    "Payment", payment.getId(), "topup:" + payment.getId(), null);
            return;
        }

        boolean payout = payment.getPurposeType() == PaymentPurpose.RENT || payment.getPurposeType() == PaymentPurpose.STAY_BOOKING;
        if (!payout || !(viaGateway || viaWallet)) return;
        if (payment.getPayee().getId().equals(platformAccounts.billingAccount().getId())) return;

        long gross = WalletService.toCents(payment.getAmount());
        long fee = payment.getPurposeType() == PaymentPurpose.STAY_BOOKING ? stayServiceFeeCents(payment) : 0;
        long share = gross - fee;
        String source = viaWallet ? WalletService.PLATFORM_COLLECTIONS : WalletService.EXTERNAL_GATEWAY;
        String what = payment.getPurposeType() == PaymentPurpose.RENT ? "Rent received" : "Stay booking paid";
        if (share > 0) {
            wallets.post(payment.getPayee().getId(), payment.getCurrency(), share, WalletEntryType.PAYOUT, source,
                    what + ": " + payment.getPurpose(), "Payment", payment.getId(), "payout:" + payment.getId(), null);
        }
        if (fee > 0) {
            wallets.postBetweenSystemAccounts(source, WalletService.PLATFORM_REVENUE, payment.getCurrency(), fee,
                    WalletEntryType.FEE, "EstateX service fee: " + payment.getPurpose(), "Payment", payment.getId(),
                    "fee:" + payment.getId(), null);
        }
    }

    /** EstateX's service fee on a stay is kept by the platform; the host gets the rest. */
    private long stayServiceFeeCents(Payment payment) {
        String meta = payment.getMetadata();
        if (meta == null || !meta.startsWith("stayBookingId=")) return 0;
        try {
            Long id = Long.valueOf(meta.substring("stayBookingId=".length()).trim());
            BigDecimal fee = stayBookings.findById(id).map(StayBooking::getServiceFee).orElse(BigDecimal.ZERO);
            return fee == null ? 0 : WalletService.toCents(fee);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String label(String provider) {
        return switch (provider) {
            case "paynow" -> "Paynow";
            case "stripe" -> "card";
            default -> provider;
        };
    }
}
