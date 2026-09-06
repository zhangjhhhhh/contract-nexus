package com.example.contract.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sihai.mail")
public class MailProperties {

    private String from;
    private String fromName = "四海合同管理系统";
    private int verificationCodeTtlMinutes = 10;
    private boolean taskReminderEnabled = true;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public int getVerificationCodeTtlMinutes() {
        return verificationCodeTtlMinutes;
    }

    public void setVerificationCodeTtlMinutes(int verificationCodeTtlMinutes) {
        this.verificationCodeTtlMinutes = verificationCodeTtlMinutes;
    }

    public boolean isTaskReminderEnabled() {
        return taskReminderEnabled;
    }

    public void setTaskReminderEnabled(boolean taskReminderEnabled) {
        this.taskReminderEnabled = taskReminderEnabled;
    }
}
