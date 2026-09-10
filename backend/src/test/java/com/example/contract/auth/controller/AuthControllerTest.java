package com.example.contract.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.contract.auth.repository.InMemoryUserRepository;
import com.example.contract.auth.security.TokenService;
import com.example.contract.auth.service.AuthService;
import com.example.contract.log.service.LogService;
import com.example.contract.notification.MailClient;
import com.example.contract.notification.MailProperties;
import com.example.contract.notification.RegisterVerificationCodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    @Test
    void registersChineseUsernameWithoutRoleId() throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.init();
        AuthService authService = new AuthService(
                userRepository,
                new NoopLogService(),
                new RegisterVerificationCodeService(new NoopMailClient(), new MailProperties()),
                new TokenService(),
                new BCryptPasswordEncoder());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "张三",
                                  "password": "secret1",
                                  "email": "tester@example.com",
                                  "verificationCode": "0000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.roleIds[0]").value("operator"));
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
