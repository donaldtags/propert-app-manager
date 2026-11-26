package com.example.estatex.auth;

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

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from:no-reply@estatex.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /** Plain-text account/security email; failures are logged, never thrown at the user. */
    public void send(String toEmail, String subject, String text) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send \"{}\" email: {}", subject, ex.getMessage());
        }
    }

    /**
     * An email with one attachment (scheduled reports). Unlike {@link #send}, a failure is thrown,
     * so the caller can record that the delivery didn't happen.
     */
    public void sendWithAttachment(String toEmail, String subject, String text, String fileName, byte[] content, String contentType) {
        try {
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text);
            helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(content), contentType);
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Could not build the email: " + e.getMessage(), e);
        }
    }

    public void sendPasswordReset(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your EstateX password");
        message.setText("""
                We received a request to reset your EstateX password.

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
