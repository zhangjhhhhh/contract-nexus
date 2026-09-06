package com.example.contract.notification;

public interface MailClient {

    boolean isConfigured();

    void sendHtml(String to, String subject, String html);
}
