package com.example.contract.contract.service;

import com.example.contract.contract.model.Contract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BailianContractReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(BailianContractReviewService.class);
    private static final int DEFAULT_MAX_TEXT_CHARS = 120_000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String appId;
    private final boolean enabled;
    private final Duration timeout;
    private final int maxTextChars;
    private final ContractTextExtractionService textExtractionService;

    @Autowired
    public BailianContractReviewService(
            ObjectMapper objectMapper,
            ContractTextExtractionService textExtractionService,
            @Value("${bailian.endpoint}") String endpoint,
            @Value("${bailian.api-key:}") String apiKey,
            @Value("${bailian.review-app-id:${bailian.app-id:}}") String appId,
            @Value("${ai.review.enabled:true}") boolean enabled,
            @Value("${ai.review.timeout-seconds:60}") long timeoutSeconds,
            @Value("${ai.review.max-text-chars:120000}") int maxTextChars) {
        this(objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                        .build(),
                endpoint,
                textExtractionService,
                apiKey,
                appId,
                enabled,
                Duration.ofSeconds(Math.max(5, timeoutSeconds)),
                maxTextChars);
    }

    BailianContractReviewService(ObjectMapper objectMapper, HttpClient httpClient, String endpoint,
                                 ContractTextExtractionService textExtractionService, String apiKey, String appId,
                                 boolean enabled, Duration timeout, int maxTextChars) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = trimTrailingSlash(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.enabled = enabled;
        this.timeout = timeout;
        this.maxTextChars = maxTextChars > 0 ? maxTextChars : DEFAULT_MAX_TEXT_CHARS;
        this.textExtractionService = textExtractionService;
        LOG.info("[AI审查] 百炼应用审查服务初始化：enabled={}，endpoint={}，appIdConfigured={}，apiKeyConfigured={}，timeoutSeconds={}，maxTextChars={}",
                enabled, safeUrl(this.endpoint), !this.appId.isBlank(), !this.apiKey.isBlank(),
                timeout.toSeconds(), this.maxTextChars);
    }

    public Optional<String> review(Contract contract) {
        if (!isConfigured()) {
            LOG.warn("[AI审查] 百炼应用审查配置不完整，跳过调用：enabled={}，endpointConfigured={}，appIdConfigured={}，apiKeyConfigured={}",
                    enabled, !endpoint.isBlank(), !appId.isBlank(), !apiKey.isBlank());
            return Optional.empty();
        }

        try {
            LOG.info("[AI审查] 开始审查合同：contractId={}，contractNum={}，contractName={}",
                    contract.getId(), contract.getNum(), contract.getName());
            List<ContractTextExtractionService.AttachmentText> attachmentTexts =
                    textExtractionService.extractReviewTexts(contract);
            if (attachmentTexts.isEmpty()) {
                LOG.warn("[AI审查] 没有可提交给模型的合同文字，取消调用百炼应用：contractId={}，contractNum={}",
                        contract.getId(), contract.getNum());
                return Optional.empty();
            }
            LOG.info("[AI审查] 合同文字提取完成，准备调用百炼应用：contractId={}，contractNum={}，fileCount={}，totalTextLength={}",
                    contract.getId(), contract.getNum(), attachmentTexts.size(), totalTextLength(attachmentTexts));

            String requestBody = buildRequestBody(contract, attachmentTexts);
            String completionUrl = endpoint + "/" + appId + "/completion";
            LOG.info("[AI审查] 调用百炼应用：url={}，requestBodyLength={}", safeUrl(completionUrl), requestBody.length());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(completionUrl))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.info("[AI审查] 百炼应用返回：status={}，body={}", response.statusCode(), summarize(response.body()));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String outputText = extractOutputText(root);
            Optional<String> reviewJson = normalizeReviewJson(objectMapper, outputText);
            if (reviewJson.isPresent()) {
                LOG.info("[AI审查] 百炼应用审查 JSON 解析成功：contractId={}，contractNum={}，jsonLength={}",
                        contract.getId(), contract.getNum(), reviewJson.get().length());
            } else {
                LOG.warn("[AI审查] 百炼应用返回内容不是合法审查 JSON：contractId={}，contractNum={}，output={}",
                        contract.getId(), contract.getNum(), summarize(outputText));
            }
            return reviewJson;
        } catch (IOException exception) {
            LOG.error("[AI审查] 调用百炼应用发生 IO 异常：contractId={}，contractNum={}，reason={}",
                    contract.getId(), contract.getNum(), exception.toString(), exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.error("[AI审查] 调用百炼应用被中断：contractId={}，contractNum={}，reason={}",
                    contract.getId(), contract.getNum(), exception.toString(), exception);
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOG.error("[AI审查] 调用百炼应用发生运行时异常：contractId={}，contractNum={}，reason={}",
                    contract.getId(), contract.getNum(), exception.toString(), exception);
            return Optional.empty();
        }
    }

    private boolean isConfigured() {
        return enabled && !apiKey.isBlank() && !appId.isBlank() && !endpoint.isBlank();
    }

    public ConfigurationStatus configurationStatus() {
        return new ConfigurationStatus(
                enabled,
                !endpoint.isBlank(),
                !appId.isBlank(),
                !apiKey.isBlank(),
                safeUrl(endpoint));
    }

    public List<ContractTextExtractionService.AttachmentText> extractTextsForDiagnostics(Contract contract) {
        return textExtractionService.extractReviewTexts(contract);
    }

    String configuredAppId() {
        return appId;
    }

    String buildRequestBody(Contract contract, List<ContractTextExtractionService.AttachmentText> attachmentTexts)
            throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode input = root.putObject("input");
        input.put("prompt", buildPrompt(contract, attachmentTexts));
        root.putObject("debug");
        return objectMapper.writeValueAsString(root);
    }

    private String buildPrompt(Contract contract, List<ContractTextExtractionService.AttachmentText> attachmentTexts) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                请基于下方已经从合同附件中提取出的文字内容审查合同。
                附件原文件不会上传给模型；下方【合同文件文字内容】是本次审查的核心依据。
                页面表单字段仅作辅助识别信息，若与文件文字内容冲突，以文件文字内容为准。
                请输出一个合法的 JSON 对象作为审查结果，不要输出任何解释文字或代码块标记（不要用 ```json 包裹）。
                字段名必须与下面完全一致：

                {
                  "contract_summary": {
                    "contract_title": "合同标题或名称",
                    "contract_type": "合同性质或类型",
                    "party_a": "甲方",
                    "party_b": "乙方",
                    "subject_matter": "合同标的",
                    "total_amount": "合同总金额",
                    "payment_terms": "付款条款摘要",
                    "performance_period": "履行期限",
                    "effective_date": "生效日期",
                    "termination_date": "终止日期",
                    "dispute_resolution": "争议解决方式"
                  },
                  "risk_alerts": [
                    {
                      "risk_id": "风险编号，如 R1、R2",
                      "risk_level": "高 或 中 或 低",
                      "risk_category": "风险类别",
                      "clause_reference": "对应的合同条款或位置",
                      "risk_description": "风险的具体描述",
                      "suggestion": "可执行的修改建议"
                    }
                  ],
                  "overall_assessment": {
                    "overall_risk_level": "高 或 中 或 低",
                    "missing_clauses": ["缺失的必备条款"],
                    "summary": "整体评价与结论"
                  }
                }

                要求：合同未约定或信息缺失的字段填「未约定」或「无」，不要留空；risk_alerts 列出 2~5 条主要风险点；risk_level 与 overall_risk_level 只能取「高」「中」「低」；只输出 JSON 本体。

                合同名称：%s
                合同编号：%s
                客户/相对方编号：%s
                起草人编号：%s
                合同周期：%s 至 %s

                【合同文件文字内容】
                """.formatted(
                nullToNone(contract.getName()),
                nullToNone(contract.getNum()),
                nullToNone(contract.getCustomerId()),
                nullToNone(contract.getDrafterId()),
                contract.getBeginTime() == null ? "无" : contract.getBeginTime(),
                contract.getEndTime() == null ? "无" : contract.getEndTime()));

        int remaining = maxTextChars;
        boolean truncated = false;
        for (ContractTextExtractionService.AttachmentText item : attachmentTexts) {
            String header = "\n--- 文件：" + nullToNone(item.name()) + "（" + item.type() + "）---\n";
            if (remaining <= header.length()) {
                truncated = true;
                break;
            }
            builder.append(header);
            remaining -= header.length();

            String text = item.text() == null ? "" : item.text();
            if (text.length() > remaining) {
                builder.append(text, 0, Math.max(0, remaining));
                truncated = true;
                break;
            }
            builder.append(text).append('\n');
            remaining -= text.length();
        }
        if (truncated) {
            builder.append("\n【提示】合同文件文字内容较长，已按系统上限截断。");
        }
        return builder.toString();
    }

    private String extractOutputText(JsonNode root) {
        JsonNode text = root.at("/output/text");
        if (text.isTextual()) {
            return text.asText();
        }
        JsonNode content = root.at("/output/message/content");
        if (content.isTextual()) {
            return content.asText();
        }
        JsonNode choiceContent = root.at("/output/choices/0/message/content");
        if (choiceContent.isTextual()) {
            return choiceContent.asText();
        }
        return root.toString();
    }

    static Optional<String> normalizeReviewJson(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String candidate = stripMarkdownFence(value.trim());
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidate = candidate.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            if (!node.isObject()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.writeValueAsString(node));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static String stripMarkdownFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        String withoutOpening = value.replaceFirst("^```(?:json)?\\s*", "");
        return withoutOpening.replaceFirst("\\s*```$", "").trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String nullToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private static String safeUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            StringBuilder builder = new StringBuilder();
            if (uri.getScheme() != null) {
                builder.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                builder.append(uri.getHost());
            }
            if (uri.getPort() > 0) {
                builder.append(':').append(uri.getPort());
            }
            if (uri.getRawPath() != null) {
                builder.append(uri.getRawPath());
            }
            return builder.isEmpty() ? "<url>" : builder.toString();
        } catch (IllegalArgumentException exception) {
            return value.length() <= 120 ? value : value.substring(0, 120) + "...";
        }
    }

    private static String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 800 ? sanitized : sanitized.substring(0, 800) + "...";
    }

    private static int totalTextLength(List<ContractTextExtractionService.AttachmentText> attachmentTexts) {
        return attachmentTexts.stream()
                .map(ContractTextExtractionService.AttachmentText::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .sum();
    }

    public record ConfigurationStatus(
            boolean enabled,
            boolean endpointConfigured,
            boolean reviewAppIdConfigured,
            boolean apiKeyConfigured,
            String endpoint
    ) {
    }
}
