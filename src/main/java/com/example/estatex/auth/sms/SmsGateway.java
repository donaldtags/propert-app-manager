package com.example.estatex.auth.sms;

/** Sends a text message. Which provider is used depends on which keys are configured (see SmsConfig). */
public interface SmsGateway {
    /** @param toE164 number in international form, e.g. +263771234567 */
    void send(String toE164, String message);

    /** False for the development gateway that only writes messages to the log. */
    boolean deliversRealMessages();

    String name();
}
