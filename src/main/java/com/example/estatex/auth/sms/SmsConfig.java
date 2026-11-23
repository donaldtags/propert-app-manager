package com.example.estatex.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the SMS provider from whichever keys are set: Africa's Talking first, then Twilio.
 * With neither, messages are only logged (fine for development and tests), and SMS sign-in codes
 * are offered only if app.sms.allow-log-gateway=true, so production never promises a text that
 * can't arrive.
 */
@Configuration
public class SmsConfig {
    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    SmsGateway smsGateway(
            @Value("${app.sms.africastalking.username:}") String atUsername,
            @Value("${app.sms.africastalking.api-key:}") String atApiKey,
            @Value("${app.sms.africastalking.sender-id:}") String atSenderId,
            @Value("${app.sms.twilio.account-sid:}") String twilioSid,
            @Value("${app.sms.twilio.auth-token:}") String twilioToken,
            @Value("${app.sms.twilio.from:}") String twilioFrom
    ) {
        if (!atUsername.isBlank() && !atApiKey.isBlank()) {
            log.info("SMS provider: Africa's Talking ({})", atUsername);
            return new HttpSmsGateways.AfricasTalking(atUsername, atApiKey, atSenderId);
        }
        if (!twilioSid.isBlank() && !twilioToken.isBlank() && !twilioFrom.isBlank()) {
            log.info("SMS provider: Twilio");
            return new HttpSmsGateways.Twilio(twilioSid, twilioToken, twilioFrom);
        }
        log.warn("No SMS provider configured (AFRICASTALKING_* or TWILIO_*): text messages are only logged.");
        return new LoggingSmsGateway();
    }
}
