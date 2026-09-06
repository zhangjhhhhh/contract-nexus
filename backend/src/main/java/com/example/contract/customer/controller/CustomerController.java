package com.example.contract.customer.controller;

import com.example.contract.common.ApiResponse;
import com.example.contract.customer.dto.CreateCustomerRequest;
import com.example.contract.customer.dto.UpdateCustomerRequest;
import com.example.contract.customer.model.Customer;
import com.example.contract.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<List<Customer>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(customerService.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<Customer> detail(@PathVariable String id) {
        return ApiResponse.success(customerService.detail(id));
    }

    @PostMapping
    public ApiResponse<Customer> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.success(customerService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable String id, @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.success(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        customerService.delete(id);
        return ApiResponse.success();
    }
}
