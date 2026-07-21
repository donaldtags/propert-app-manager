package com.example.primenestprop.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from:no-reply@primenest.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordReset(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your PrimeNest password");
        message.setText("""
                We received a request to reset your PrimeNest password.

                Reset your password: %s

                This link expires in 30 minutes. If you didn't request this, you can ignore this email.
                """.formatted(resetLink));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send password reset email to {}: {}", toEmail, ex.getMessage());
        }
    }
}
