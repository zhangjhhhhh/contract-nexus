package com.example.contract;

import com.example.contract.common.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContractManagementApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(ContractManagementApplication.class, args);
    }
}
