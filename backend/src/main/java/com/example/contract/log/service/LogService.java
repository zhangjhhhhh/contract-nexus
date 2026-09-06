package com.example.contract.log.service;

import com.example.contract.log.model.OperationLog;
import java.util.List;

public interface LogService {

    void record(String userName, String content);

    List<OperationLog> list();
}

