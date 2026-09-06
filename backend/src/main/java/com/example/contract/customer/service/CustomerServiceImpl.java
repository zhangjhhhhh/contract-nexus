package com.example.contract.customer.service;

import com.example.contract.common.BusinessException;
import com.example.contract.contract.repository.ContractRepository;
import com.example.contract.customer.dto.CreateCustomerRequest;
import com.example.contract.customer.dto.UpdateCustomerRequest;
import com.example.contract.customer.model.Customer;
import com.example.contract.customer.repository.CustomerRepository;
import com.example.contract.log.service.LogService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final LogService logService;

    public CustomerServiceImpl(CustomerRepository customerRepository,
                               ContractRepository contractRepository,
                               LogService logService) {
        this.customerRepository = customerRepository;
        this.contractRepository = contractRepository;
        this.logService = logService;
    }

    @Override
    public List<Customer> list(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return customerRepository.findAll()
                .stream()
                .filter(customer -> normalized.isEmpty()
                        || contains(customer.getName(), normalized)
                        || contains(customer.getTel(), normalized)
                        || contains(customer.getAddress(), normalized))
                .toList();
    }

    @Override
    public Customer detail(String id) {
        return findCustomer(id);
    }

    @Override
    public Customer create(CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setId(customerRepository.nextId());
        customer.setName(request.getName());
        customer.setTel(request.getTel());
        customer.setAddress(request.getAddress());
        customer.setFax(request.getFax());
        customer.setEmail(request.getEmail());
        customer.setBank(request.getBank());
        customer.setAccount(request.getAccount());
        customer.setRemark(request.getRemark());

        Customer saved = customerRepository.save(customer);
        logService.record("admin", "新增客户：" + saved.getName());
        return saved;
    }

    @Override
    public Customer update(String id, UpdateCustomerRequest request) {
        Customer customer = findCustomer(id);

        customer.setName(request.getName());
        customer.setTel(request.getTel());
        customer.setAddress(request.getAddress());
        customer.setFax(request.getFax());
        customer.setEmail(request.getEmail());
        customer.setBank(request.getBank());
        customer.setAccount(request.getAccount());
        customer.setRemark(request.getRemark());

        Customer saved = customerRepository.save(customer);
        logService.record("admin", "修改客户：" + saved.getName());
        return saved;
    }

    @Override
    public void delete(String id) {
        findCustomer(id);
        boolean referenced = contractRepository.findAllContracts()
                .stream()
                .anyMatch(contract -> contract.getCustomerId().equals(id));
        if (referenced) {
            throw new BusinessException("该客户已有合同，不能删除");
        }
        customerRepository.deleteById(id);
        logService.record("admin", "删除客户：" + id);
    }

    private Customer findCustomer(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "客户不存在"));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
