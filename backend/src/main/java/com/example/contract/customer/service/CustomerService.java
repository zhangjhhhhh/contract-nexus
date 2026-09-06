package com.example.contract.customer.service;

import com.example.contract.customer.dto.CreateCustomerRequest;
import com.example.contract.customer.dto.UpdateCustomerRequest;
import com.example.contract.customer.model.Customer;
import java.util.List;

public interface CustomerService {

    List<Customer> list(String keyword);

    Customer detail(String id);

    Customer create(CreateCustomerRequest request);

    Customer update(String id, UpdateCustomerRequest request);

    void delete(String id);
}
