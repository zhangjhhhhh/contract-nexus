package com.example.contract.legalai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contract.auth.model.Role;
import com.example.contract.auth.model.User;
import com.example.contract.auth.repository.UserRepository;
import com.example.contract.common.BusinessException;
import com.example.contract.common.DatabaseProperties;
import com.example.contract.common.FileStorageService;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ContractVersion;
import com.example.contract.contract.model.SignRecord;
import com.example.contract.contract.repository.ContractRepository;
import com.example.contract.contract.service.ContractTextExtractionService;
import com.example.contract.contract.service.ContractTextExtractionService.AttachmentText;
import com.example.contract.legalai.dto.LegalAiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalAiChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configuredAppIdCanUseFixedLegalChatAgent() {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "84a6acc6f6134090b9b21e488c3792f1",
                Duration.ofSeconds(5));

        assertEquals("84a6acc6f6134090b9b21e488c3792f1", service.configuredAppId());
    }

    @Test
    void buildRequestBodyUsesPromptAndHistoryWithoutSecrets() throws Exception {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "secret-key",
                "app-id",
                Duration.ofSeconds(5));
        LegalAiMessage history = new LegalAiMessage();
        history.setRole("user");
        history.setContent("劳动合同试用期有什么限制？");

        String body = service.buildRequestBody("公司拖欠工资怎么办？", List.of(history));

        JsonNode root = objectMapper.readTree(body);
        String prompt = root.at("/input/prompt").asText();
        assertTrue(prompt.contains("公司拖欠工资怎么办"));
        assertTrue(prompt.contains("劳动合同试用期"));
        assertTrue(prompt.contains("四海"));
        assertFalse(body.contains("secret-key"));
        assertFalse(body.contains("app-id"));
    }

    @Test
    void buildPromptCanIncludeContractContext() {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "app",
                Duration.ofSeconds(5));

        String prompt = service.buildPrompt(
                "分析风险",
                List.of(),
                new LegalAiChatService.ContractContext("HT-001", "测试合同", "付款条款：30日内支付。"));

        assertTrue(prompt.contains("合同上下文"));
        assertTrue(prompt.contains("HT-001"));
        assertTrue(prompt.contains("付款条款：30日内支付。"));
    }

    @Test
    void buildPromptUsesExtractedAttachmentTextForLegalChatContext() {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "legal-chat-app",
                Duration.ofSeconds(5));

        String prompt = service.buildPrompt(
                "总结合同",
                List.of(),
                new LegalAiChatService.ContractContext("HT-002", "附件合同", "【附件：合同.pdf】\n付款条款：验收后10日内支付。"));

        assertTrue(prompt.contains("【合同上下文】"));
        assertTrue(prompt.contains("附件合同"));
        assertTrue(prompt.contains("付款条款：验收后10日内支付。"));
    }

    @Test
    void buildPromptKeepsFullContractContext() {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "legal-chat-app",
                Duration.ofSeconds(5));
        String fullContext = "首段条款\n" + "A".repeat(85_000) + "末尾风险提示";

        String prompt = service.buildPrompt(
                "请审阅全文",
                List.of(),
                new LegalAiChatService.ContractContext("HT-003", "长文本合同", fullContext));

        assertTrue(prompt.contains("首段条款"));
        assertTrue(prompt.contains("末尾风险提示"));
    }

    @Test
    void buildContractContextTextIncludesBodyAndAttachments() {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "legal-chat-app",
                Duration.ofSeconds(5));
        Contract contract = new Contract();
        contract.setContent("合同正文条款：交付后30日内验收。");

        String context = service.buildContractContextText(contract, List.of(
                new AttachmentText("合同附件.docx", "docx", "附件条款：逾期付款每日万分之五。")));

        assertTrue(context.contains("【合同正文】"));
        assertTrue(context.contains("合同正文条款：交付后30日内验收。"));
        assertTrue(context.contains("【附件：合同附件.docx】"));
        assertTrue(context.contains("附件条款：逾期付款每日万分之五。"));
    }

    @Test
    void rejectsContractContextWhenUserHasNoPermissionOrInvolvement() {
        Contract contract = new Contract();
        contract.setId("K001");
        contract.setNum("HT-001");
        contract.setName("测试合同");
        contract.setDrafterId("U002");
        contract.setContent("合同正文");

        FakeContractRepository contractRepository = new FakeContractRepository(contract, List.of());
        FakeUserRepository userRepository = new FakeUserRepository(List.of("dashboard:view"));
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "app",
                Duration.ofSeconds(5),
                contractRepository,
                userRepository,
                new ContractTextExtractionService(new FileStorageService(new DatabaseProperties())));

        com.example.contract.legalai.dto.LegalAiChatRequest request = new com.example.contract.legalai.dto.LegalAiChatRequest();
        request.setMessage("分析风险");
        request.setUserId("U001");
        request.setContractId("K001");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.chat(request));

        assertEquals(403, exception.getCode());
    }

    @Test
    void extractOutputTextAcceptsDashscopeTextShape() throws Exception {
        LegalAiChatService service = new LegalAiChatService(
                objectMapper,
                HttpClient.newHttpClient(),
                "https://dashscope.aliyuncs.com/api/v1/apps",
                "key",
                "app",
                Duration.ofSeconds(5));
        JsonNode root = objectMapper.readTree("{\"output\":{\"text\":\"回答内容\"}}");

        assertEquals("回答内容", service.extractOutputText(root));
    }

    private static class FakeContractRepository implements ContractRepository {
        private final Contract contract;
        private final List<ContractProcess> processes;

        FakeContractRepository(Contract contract, List<ContractProcess> processes) {
            this.contract = contract;
            this.processes = processes;
        }

        @Override
        public List<Contract> findAllContracts() { return List.of(contract); }

        @Override
        public Optional<Contract> findContractById(String id) {
            return contract.getId().equals(id) ? Optional.of(contract) : Optional.empty();
        }

        @Override
        public Contract saveContract(Contract contract) { return contract; }

        @Override
        public void deleteProcessesByContractId(String contractId) { }

        @Override
        public List<ContractProcess> findProcessesByContractId(String contractId) { return processes; }

        @Override
        public List<ContractProcess> findAllProcesses() { return processes; }

        @Override
        public ContractProcess saveProcess(ContractProcess process) { return process; }

        @Override
        public List<ContractProcess> saveProcesses(List<ContractProcess> processes) { return processes; }

        @Override
        public void returnToFinalize(String contractId, String drafterId, List<String> approveUserIds) { }

        @Override
        public ContractVersion saveContractVersion(Contract contract, String approverId, String approvalResult, String approvalOpinion) {
            return new ContractVersion();
        }

        @Override
        public List<ContractVersion> findVersionsByContractId(String contractId) { return List.of(); }

        @Override
        public SignRecord saveSignRecord(SignRecord signRecord) { return signRecord; }

        @Override
        public List<SignRecord> findSignRecordsByContractId(String contractId) { return List.of(); }

        @Override
        public void deleteContractById(String id) { }

        @Override
        public String nextContractId() { return "K999"; }

        @Override
        public String nextContractNum() { return "HT-999"; }

        @Override
        public String nextProcessId() { return "P999"; }

        @Override
        public String nextSignRecordId() { return "S999"; }
    }

    private static class FakeUserRepository implements UserRepository {
        private final List<String> permissions;

        FakeUserRepository(List<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public List<User> findAllUsers() { return new ArrayList<>(); }

        @Override
        public Optional<User> findUserById(String id) { return Optional.empty(); }

        @Override
        public Optional<User> findUserByUsername(String username) { return Optional.empty(); }

        @Override
        public User saveUser(User user) { return user; }

        @Override
        public void deleteUser(String id) { }

        @Override
        public List<Role> findAllRoles() { return List.of(); }

        @Override
        public Optional<Role> findRoleById(String id) { return Optional.empty(); }

        @Override
        public Role saveRole(Role role) { return role; }

        @Override
        public void deleteRole(String id) { }

        @Override
        public List<String> findRoleIdsByUserId(String userId) { return List.of(); }

        @Override
        public List<String> findPermissionsByUserId(String userId) { return permissions; }

        @Override
        public String nextUserId() { return "U999"; }

        @Override
        public String nextRoleId() { return "R999"; }
    }
}
