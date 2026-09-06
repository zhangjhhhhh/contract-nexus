package com.example.contract.notification;

import com.example.contract.common.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SmtpMailClient implements MailClient {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final String mailUsername;
    private final String mailPassword;

    public SmtpMailClient(JavaMailSender mailSender,
                          MailProperties mailProperties,
                          @Value("${spring.mail.username:}") String mailUsername,
                          @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(mailUsername) && StringUtils.hasText(mailPassword);
    }

    @Override
    public void sendHtml(String to, String subject, String html) {
        if (!isConfigured()) {
            throw new BusinessException("邮件服务未配置，请设置 MAIL_PASSWORD");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(fromAddress());
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new BusinessException("邮件发送失败，请检查邮箱 SMTP 配置");
        }
    }

    private InternetAddress fromAddress() throws UnsupportedEncodingException {
        String from = StringUtils.hasText(mailProperties.getFrom()) ? mailProperties.getFrom() : mailUsername;
        return new InternetAddress(from, mailProperties.getFromName(), StandardCharsets.UTF_8.name());
    }
}
