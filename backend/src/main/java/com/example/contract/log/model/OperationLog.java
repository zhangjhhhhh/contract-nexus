package com.example.contract.log.model;

import java.time.LocalDateTime;

public class OperationLog {

    private String id;
    private String userName;
    private String content;
    private LocalDateTime time;

    public OperationLog(String userName, String content, LocalDateTime time) {
        this(null, userName, content, time);
    }

    public OperationLog(String id, String userName, String content, LocalDateTime time) {
        this.id = id;
        this.userName = userName;
        this.content = content;
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}

