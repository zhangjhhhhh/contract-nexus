package com.example.contract.legalai.controller;

import com.example.contract.auth.security.RequirePermission;
import com.example.contract.common.ApiResponse;
import com.example.contract.legalai.dto.LegalAiChatRequest;
import com.example.contract.legalai.dto.LegalAiChatResponse;
import com.example.contract.legalai.service.LegalAiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequirePermission("query:info")
@RestController
@RequestMapping("/api/legal-ai")
public class LegalAiController {

    private final LegalAiChatService legalAiChatService;

    public LegalAiController(LegalAiChatService legalAiChatService) {
        this.legalAiChatService = legalAiChatService;
    }

    @PostMapping("/chat")
    public ApiResponse<LegalAiChatResponse> chat(@Valid @RequestBody LegalAiChatRequest request) {
        return ApiResponse.success(new LegalAiChatResponse(legalAiChatService.chat(request)));
    }
}
