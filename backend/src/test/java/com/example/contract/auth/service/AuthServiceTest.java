package com.example.contract.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.auth.dto.RegisterRequest;
import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import com.example.contract.auth.repository.InMemoryUserRepository;
import com.example.contract.auth.security.TokenService;
import com.example.contract.log.service.LogService;
import com.example.contract.notification.MailClient;
import com.example.contract.notification.MailProperties;
import com.example.contract.notification.RegisterVerificationCodeService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allowsChineseAndThreeLetterEnglishUsernames() {
        RegisterRequest english = registerRequest("abc");
        RegisterRequest chinese = registerRequest("张三");

        assertTrue(validator.validate(english).isEmpty());
        assertTrue(validator.validate(chinese).isEmpty());
    }

    @Test
    void registersNewUsersAsOperatorByDefault() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.init();
        AuthService authService = new AuthService(
                userRepository,
                new NoopLogService(),
                new RegisterVerificationCodeService(new NoopMailClient(), new MailProperties()),
                new TokenService(),
                new BCryptPasswordEncoder());

        authService.register(registerRequest("李四"));

        User saved = userRepository.findUserByUsername("李四").orElseThrow();
        assertEquals(List.of("operator"), saved.getRoleIds());
    }

    private RegisterRequest registerRequest(String name) {
        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setPassword("secret1");
        request.setEmail("tester@example.com");
        request.setVerificationCode("0000");
        return request;
    }

    private static class NoopLogService implements LogService {
        @Override
        public void record(String userName, String content) {
        }

        @Override
        public List<com.example.contract.log.model.OperationLog> list() {
            return List.of();
        }
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
