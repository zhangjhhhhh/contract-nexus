package com.example.contract.legalai.service;

import com.example.contract.auth.repository.UserRepository;
import com.example.contract.common.BusinessException;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.repository.ContractRepository;
import com.example.contract.contract.service.ContractTextExtractionService;
import com.example.contract.contract.service.ContractTextExtractionService.AttachmentText;
import com.example.contract.legalai.dto.LegalAiChatRequest;
import com.example.contract.legalai.dto.LegalAiMessage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LegalAiChatService {

    private static final Logger LOG = LoggerFactory.getLogger(LegalAiChatService.class);
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final String FIXED_LEGAL_CHAT_APP_ID = "84a6acc6f6134090b9b21e488c3792f1";
    private static final Set<String> ALL_CONTRACT_PERMISSIONS = Set.of("query:info", "base:contract");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String appId;
    private final Duration timeout;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ContractTextExtractionService textExtractionService;

    @Autowired
    public LegalAiChatService(
            ObjectMapper objectMapper,
            ContractRepository contractRepository,
            UserRepository userRepository,
            ContractTextExtractionService textExtractionService,
            @Value("${bailian.endpoint}") String endpoint,
            @Value("${bailian.api-key:}") String apiKey,
            @Value("${ai.legal-chat.timeout-seconds:45}") long timeoutSeconds) {
        this(objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                        .build(),
                endpoint,
                apiKey,
                FIXED_LEGAL_CHAT_APP_ID,
                Duration.ofSeconds(Math.max(5, timeoutSeconds)),
                contractRepository,
                userRepository,
                textExtractionService);
    }

    LegalAiChatService(ObjectMapper objectMapper, HttpClient httpClient, String endpoint,
                       String apiKey, String appId, Duration timeout) {
        this(objectMapper, httpClient, endpoint, apiKey, appId, timeout, null, null, null);
    }

    LegalAiChatService(ObjectMapper objectMapper, HttpClient httpClient, String endpoint,
                       String apiKey, String appId, Duration timeout,
                       ContractRepository contractRepository,
                       UserRepository userRepository,
                       ContractTextExtractionService textExtractionService) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = trimTrailingSlash(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.timeout = timeout;
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.textExtractionService = textExtractionService;
        LOG.info("[四海] 服务初始化：endpoint={}，appIdConfigured={}，apiKeyConfigured={}，timeoutSeconds={}",
                safeUrl(this.endpoint), !this.appId.isBlank(), !this.apiKey.isBlank(), timeout.toSeconds());
    }

    public String chat(LegalAiChatRequest request) {
        if (!isConfigured()) {
            LOG.warn("[四海] 百炼应用配置不完整：endpointConfigured={}，appIdConfigured={}，apiKeyConfigured={}",
                    !endpoint.isBlank(), !appId.isBlank(), !apiKey.isBlank());
            throw new BusinessException(503, "四海暂未配置，请联系管理员。");
        }

        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        try {
            ContractContext contractContext = resolveContractContext(request);
            String requestBody = buildRequestBody(message, request.getHistory(), contractContext);
            String completionUrl = endpoint + "/" + appId + "/completion";
            LOG.info("[四海] 调用百炼应用：url={}，historySize={}，hasContractContext={}，requestBodyLength={}",
                    safeUrl(completionUrl), safeHistorySize(request.getHistory()), contractContext.hasText(), requestBody.length());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(completionUrl))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOG.info("[四海] 百炼应用返回：status={}，body={}", response.statusCode(), summarize(response.body()));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(502, "AI 服务暂时不可用，请稍后再试。");
            }

            String reply = extractOutputText(objectMapper.readTree(response.body()));
            if (reply.isBlank()) {
                throw new BusinessException(502, "AI 服务未返回有效回答，请稍后再试。");
            }
            return reply;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            LOG.error("[四海] 调用百炼应用 IO 异常：reason={}", exception.toString(), exception);
            throw new BusinessException(502, "AI 服务连接失败，请稍后再试。");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.error("[四海] 调用百炼应用被中断：reason={}", exception.toString(), exception);
            throw new BusinessException(502, "AI 服务请求被中断，请稍后再试。");
        } catch (RuntimeException exception) {
            LOG.error("[四海] 调用百炼应用运行时异常：reason={}", exception.toString(), exception);
            throw new BusinessException(502, "AI 服务异常，请稍后再试。");
        }
    }

    String buildRequestBody(String message, List<LegalAiMessage> history) throws IOException {
        return buildRequestBody(message, history, ContractContext.empty());
    }

    String buildRequestBody(String message, List<LegalAiMessage> history, ContractContext contractContext) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode input = root.putObject("input");
        input.put("prompt", buildPrompt(message, history, contractContext));
        root.putObject("debug");
        return objectMapper.writeValueAsString(root);
    }

    String buildPrompt(String message, List<LegalAiMessage> history) {
        return buildPrompt(message, history, ContractContext.empty());
    }

    String buildPrompt(String message, List<LegalAiMessage> history, ContractContext contractContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是“四海”，合同管理系统内的法律智能体。请用中文回答用户关于合同、合规和常见法律知识的问题。
                回答应清晰、谨慎、可执行；如果用户选择了合同，【合同上下文】是本轮对话的前置事实，必须完整阅读并优先依据这些内容分析。
                涉及合同文本时，不要编造未出现的事实，也不要忽略附件中的条款。
                不要承诺结果，不要替代律师意见。
                每次回答末尾必须包含免责声明：AI 回答仅供参考，不构成正式法律意见。具体案件请咨询专业律师。

                """);

        if (contractContext.hasText()) {
            builder.append("【合同上下文】\n")
                    .append("合同编号：").append(contractContext.contractNum()).append('\n')
                    .append("合同名称：").append(contractContext.contractName()).append('\n')
                    .append(contractContext.text())
                    .append("\n\n");
        }

        List<LegalAiMessage> safeHistory = history == null ? List.of() : history.stream()
                .filter(item -> item != null && item.getContent() != null && !item.getContent().isBlank())
                .filter(item -> "user".equals(item.getRole()) || "assistant".equals(item.getRole()))
                .toList();
        int start = Math.max(0, safeHistory.size() - MAX_HISTORY_MESSAGES);
        if (start < safeHistory.size()) {
            builder.append("【历史对话】\n");
            for (LegalAiMessage item : safeHistory.subList(start, safeHistory.size())) {
                builder.append("user".equals(item.getRole()) ? "用户：" : "四海：")
                        .append(limit(item.getContent().trim(), 2000))
                        .append('\n');
            }
            builder.append('\n');
        }

        builder.append("【用户问题】\n").append(limit(message.trim(), 4000));
        return builder.toString();
    }

    String extractOutputText(JsonNode root) {
        JsonNode text = root.at("/output/text");
        if (text.isTextual()) {
            return text.asText().trim();
        }
        JsonNode content = root.at("/output/message/content");
        if (content.isTextual()) {
            return content.asText().trim();
        }
        JsonNode choiceContent = root.at("/output/choices/0/message/content");
        if (choiceContent.isTextual()) {
            return choiceContent.asText().trim();
        }
        return "";
    }

    String configuredAppId() {
        return appId;
    }

    private ContractContext resolveContractContext(LegalAiChatRequest request) {
        String contractId = request.getContractId();
        if (contractId == null || contractId.isBlank()) {
            return ContractContext.empty();
        }
        if (contractRepository == null || userRepository == null || textExtractionService == null) {
            return ContractContext.empty();
        }

        String userId = request.getUserId() == null ? "" : request.getUserId().trim();
        if (userId.isBlank()) {
            throw new BusinessException("选择合同后必须提供用户ID");
        }

        Contract contract = contractRepository.findContractById(contractId)
                .orElseThrow(() -> new BusinessException(404, "合同不存在"));
        requireContractAccess(userId, contract);

        String text = buildContractContextText(contract, textExtractionService.extractReviewTexts(contract));
        if (text.isBlank()) {
            return ContractContext.empty();
        }
        return new ContractContext(contract.getNum(), contract.getName(), text);
    }

    String buildContractContextText(Contract contract, List<AttachmentText> attachmentTexts) {
        List<String> sections = new ArrayList<>();
        String content = contract.getContent() == null ? "" : contract.getContent().trim();
        if (!content.isBlank()) {
            sections.add("【合同正文】\n" + content);
        }
        for (AttachmentText item : attachmentTexts == null ? List.<AttachmentText>of() : attachmentTexts) {
            if (item == null || item.text() == null || item.text().isBlank()) {
                continue;
            }
            String name = item.name() == null || item.name().isBlank() ? "未命名附件" : item.name();
            sections.add("【附件：" + name + "】\n" + item.text().trim());
        }
        return String.join("\n\n", sections);
    }

    private void requireContractAccess(String userId, Contract contract) {
        List<String> permissions = userRepository.findPermissionsByUserId(userId);
        boolean canReadAll = permissions.stream().anyMatch(ALL_CONTRACT_PERMISSIONS::contains);
        if (canReadAll) {
            return;
        }
        if (userId.equals(contract.getDrafterId())) {
            return;
        }
        boolean involved = contractRepository.findProcessesByContractId(contract.getId())
                .stream()
                .map(ContractProcess::getUserId)
                .anyMatch(userId::equals);
        if (!involved) {
            throw new BusinessException(403, "无权读取该合同");
        }
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !appId.isBlank() && !endpoint.isBlank();
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static int safeHistorySize(List<LegalAiMessage> history) {
        return history == null ? 0 : history.size();
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

    record ContractContext(String contractNum, String contractName, String text) {
        static ContractContext empty() {
            return new ContractContext("", "", "");
        }

        boolean hasText() {
            return text != null && !text.isBlank();
        }
    }
}
