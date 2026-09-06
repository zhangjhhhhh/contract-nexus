package com.example.contract.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class RegisterVerificationCodeServiceTest {

    @Test
    void acceptsUniversalTestCode() {
        MailProperties properties = new MailProperties();
        RegisterVerificationCodeService service = new RegisterVerificationCodeService(new NoopMailClient(), properties);

        assertDoesNotThrow(() -> service.verifyForRegistration("tester@example.com", "0000"));
    }

    private static class NoopMailClient implements MailClient {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public void sendHtml(String to, String subject, String html) {
        }
    }
}
