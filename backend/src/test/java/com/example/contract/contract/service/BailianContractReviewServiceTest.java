package com.example.contract.contract.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.common.DatabaseProperties;
import com.example.contract.common.FileStorageService;
import com.example.contract.contract.model.Contract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BailianContractReviewServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configuredAppIdCanUseDedicatedReviewAgent() {
        BailianContractReviewService service = new BailianContractReviewService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                new ContractTextExtractionService(new FileStorageService(new DatabaseProperties())),
                "key",
                "review-app",
                true,
                Duration.ofSeconds(5),
                10_000);

        assertEquals("review-app", service.configuredAppId());
    }

    @Test
    void normalizeReviewJsonAcceptsPlainJson() throws Exception {
        String raw = """
                {
                  "contract_summary": {"contract_title": "测试合同"},
                  "risk_alerts": [],
                  "overall_assessment": {"overall_risk_level": "低"}
                }
                """;

        Optional<String> result = BailianContractReviewService.normalizeReviewJson(objectMapper, raw);

        assertTrue(result.isPresent());
        JsonNode node = objectMapper.readTree(result.get());
        assertEquals("测试合同", node.at("/contract_summary/contract_title").asText());
    }

    @Test
    void normalizeReviewJsonStripsMarkdownFence() throws Exception {
        String raw = """
                ```json
                {"contract_summary":{},"risk_alerts":[],"overall_assessment":{}}
                ```
                """;

        Optional<String> result = BailianContractReviewService.normalizeReviewJson(objectMapper, raw);

        assertTrue(result.isPresent());
        JsonNode node = objectMapper.readTree(result.get());
        assertTrue(node.get("risk_alerts").isArray());
    }

    @Test
    void buildRequestBodyUsesExtractedTextWithoutSessionFileIds() throws Exception {
        Contract contract = new Contract();
        contract.setName("测试合同");
        contract.setNum("HT-001");
        contract.setCustomerId("C001");
        contract.setDrafterId("U001");

        BailianContractReviewService service = new BailianContractReviewService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                new ContractTextExtractionService(new FileStorageService(new DatabaseProperties())),
                "key",
                "app",
                true,
                Duration.ofSeconds(5),
                10_000);

        String body = service.buildRequestBody(contract, List.of(
                new ContractTextExtractionService.AttachmentText("合同.pdf", "pdf", "付款条款：30日内支付。")));

        JsonNode root = objectMapper.readTree(body);
        String prompt = root.at("/input/prompt").asText();
        assertTrue(prompt.contains("付款条款"));
        assertTrue(prompt.contains("合同文件文字内容"));
        assertFalse(body.contains("session_file_ids"));
        assertTrue(root.at("/parameters").isMissingNode());
    }
}
