package com.example.contract.customer.repository;

import com.example.contract.customer.model.Customer;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    @PostConstruct
    public void init() {
        saveSeedCustomer("C001", "华南智造有限公司", "0755-88990011", "深圳市南山区科技园科苑路 88 号");
        saveSeedCustomer("C002", "星河供应链集团", "020-66881230", "广州市天河区珠江新城华夏路 16 号");
        saveSeedCustomer("C003", "北辰数据科技股份有限公司", "010-56667788", "北京市海淀区中关村东路 9 号");
        saveSeedCustomer("C004", "江南能源服务有限公司", "025-77889900", "南京市建邺区江东中路 128 号");
        saveSeedCustomer("C005", "海岳建筑工程有限公司", "0571-88332211", "杭州市西湖区文三路 199 号");
        sequence.set(5);
    }

    @Override
    public synchronized List<Customer> findAll() {
        return customers.values()
                .stream()
                .sorted(Comparator.comparing(Customer::getId).reversed())
                .map(this::copyCustomer)
                .toList();
    }

    @Override
    public synchronized Optional<Customer> findById(String id) {
        return Optional.ofNullable(customers.get(id)).map(this::copyCustomer);
    }

    @Override
    public synchronized Customer save(Customer customer) {
        customers.put(customer.getId(), copyCustomer(customer));
        return copyCustomer(customer);
    }

    @Override
    public synchronized void deleteById(String id) {
        customers.remove(id);
    }

    @Override
    public String nextId() {
        return "C" + String.format("%03d", sequence.incrementAndGet());
    }

    @Override
    public synchronized boolean existsByFieldsExcludingId(String name, String tel, String excludeId) {
        return customers.values()
                .stream()
                .anyMatch(customer -> same(customer.getName(), name)
                        && same(customer.getTel(), tel)
                        && !same(customer.getId(), excludeId));
    }

    private void saveSeedCustomer(String id, String name, String tel, String address) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName(name);
        customer.setTel(tel);
        customer.setAddress(address);
        customer.setFax("");
        customer.setEmail("");
        customer.setBank("");
        customer.setAccount("");
        customer.setRemark("");
        customers.put(id, customer);
    }

    private Customer copyCustomer(Customer source) {
        Customer target = new Customer();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setTel(source.getTel());
        target.setAddress(source.getAddress());
        target.setFax(source.getFax());
        target.setEmail(source.getEmail());
        target.setBank(source.getBank());
        target.setAccount(source.getAccount());
        target.setRemark(source.getRemark());
        return target;
    }

    private static boolean same(String left, String right) {
        return String.valueOf(left).equals(String.valueOf(right));
    }
}
