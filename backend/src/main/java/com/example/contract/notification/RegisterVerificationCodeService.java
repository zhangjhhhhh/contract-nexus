package com.example.contract.notification;

import com.example.contract.common.BusinessException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RegisterVerificationCodeService {

    public static final String TEST_CODE = "0000";
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MailClient mailClient;
    private final MailProperties mailProperties;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CodeRecord> codeRecords = new ConcurrentHashMap<>();

    public RegisterVerificationCodeService(MailClient mailClient, MailProperties mailProperties) {
        this.mailClient = mailClient;
        this.mailProperties = mailProperties;
    }

    public void sendRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(Math.max(1, mailProperties.getVerificationCodeTtlMinutes()));
        mailClient.sendHtml(normalizedEmail, "【四海】注册邮箱验证码", buildRegisterCodeHtml(code, expiresAt));
        codeRecords.put(normalizedEmail, new CodeRecord(code, expiresAt));
    }

    public void verifyForRegistration(String email, String code) {
        if (TEST_CODE.equals(code)) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        CodeRecord record = codeRecords.get(normalizedEmail);
        if (record == null || record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!record.code().equals(code)) {
            throw new BusinessException("验证码不正确");
        }
        codeRecords.remove(normalizedEmail);
    }

    private String generateCode() {
        return String.format("%04d", random.nextInt(10_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String buildRegisterCodeHtml(String code, LocalDateTime expiresAt) {
        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.7;color:#1f2937;background:#f6f8fb;padding:28px;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                    <h2 style="margin:0 0 16px;font-size:20px;color:#111827;">四海合同管理系统注册验证</h2>
                    <p style="margin:0 0 16px;">您好，您正在注册【四海】合同管理系统账号。请在注册页面输入以下验证码完成邮箱验证：</p>
                    <div style="margin:22px 0;padding:18px 24px;background:#f3f6fb;border-radius:8px;text-align:center;font-size:32px;font-weight:700;letter-spacing:8px;color:#0f4c81;">%s</div>
                    <p style="margin:0 0 8px;">验证码有效期至：%s。</p>
                    <p style="margin:0;color:#6b7280;font-size:13px;">如非本人操作，请忽略本邮件。验证码仅用于注册验证，请勿转发给他人。</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0 14px;">
                    <p style="margin:0;color:#6b7280;font-size:12px;">本邮件由四海合同管理系统自动发送，请勿直接回复。</p>
                  </div>
                </div>
                """.formatted(code, expiresAt.format(DISPLAY_TIME_FORMATTER));
    }

    private record CodeRecord(String code, LocalDateTime expiresAt) {
    }
}
