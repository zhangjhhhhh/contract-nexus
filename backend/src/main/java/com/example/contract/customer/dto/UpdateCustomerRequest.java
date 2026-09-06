package com.example.contract.customer.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateCustomerRequest {

    @NotBlank(message = "客户名称不能为空")
    private String name;

    @NotBlank(message = "电话不能为空")
    private String tel;

    @NotBlank(message = "地址不能为空")
    private String address;

    private String fax;
    private String email;
    private String bank;
    private String account;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTel() { return tel; }
    public void setTel(String tel) { this.tel = tel; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
