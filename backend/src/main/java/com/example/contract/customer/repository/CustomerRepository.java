package com.example.contract.customer.repository;

import com.example.contract.customer.model.Customer;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository {

    List<Customer> findAll();

    Optional<Customer> findById(String id);

    Customer save(Customer customer);

    void deleteById(String id);

    String nextId();

    boolean existsByFieldsExcludingId(String name, String tel, String excludeId);
}
