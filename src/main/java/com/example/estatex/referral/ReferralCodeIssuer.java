package com.example.estatex.referral;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a referral code in its own transaction, so two simultaneous first requests (a page
 * loading twice) can't fail the caller: the loser's insert fails alone, and the caller reads back
 * the winner's code.
 */
@Component
class ReferralCodeIssuer {
    private final ReferralCodeRepository codes;

    ReferralCodeIssuer(ReferralCodeRepository codes) {
        this.codes = codes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReferralCode create(Long userId, String code) {
        ReferralCode c = new ReferralCode();
        c.setUserId(userId);
        c.setCode(code);
        return codes.saveAndFlush(c);
    }
}
