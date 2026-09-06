package com.example.contract.log.service;

import com.example.contract.log.model.OperationLog;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("memory")
public class InMemoryLogService implements LogService {

    private final LinkedList<OperationLog> logs = new LinkedList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public synchronized void record(String userName, String content) {
        logs.addFirst(new OperationLog(String.valueOf(sequence.incrementAndGet()), userName, content, LocalDateTime.now()));
    }

    @Override
    public synchronized List<OperationLog> list() {
        return List.copyOf(logs);
    }
}

