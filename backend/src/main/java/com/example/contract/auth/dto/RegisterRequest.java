package com.example.contract.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "^(?:[A-Za-z][A-Za-z0-9_]{2,}|\\p{IsHan}[\\p{IsHan}A-Za-z0-9_]{1,})$", message = "用户名支持中文或英文；中文用户名至少 2 位，英文用户名至少 3 位并以字母开头")
    private String name;

    @NotBlank
    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    @NotBlank
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{4}$", message = "验证码需为 4 位数字")
    private String verificationCode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

}
