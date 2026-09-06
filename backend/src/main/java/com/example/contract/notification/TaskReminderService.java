package com.example.contract.notification;

import com.example.contract.auth.model.User;
import com.example.contract.auth.repository.UserRepository;
import com.example.contract.contract.model.Contract;
import com.example.contract.contract.model.ContractProcess;
import com.example.contract.contract.model.ProcessType;
import com.example.contract.contract.repository.ContractRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TaskReminderService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskReminderService.class);

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final MailClient mailClient;
    private final MailProperties mailProperties;

    public TaskReminderService(ContractRepository contractRepository,
                               UserRepository userRepository,
                               MailClient mailClient,
                               MailProperties mailProperties) {
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.mailClient = mailClient;
        this.mailProperties = mailProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendStartupTaskReminders() {
        if (!mailProperties.isTaskReminderEnabled()) {
            LOG.info("[四海待办提醒] 已关闭启动邮件提醒");
            return;
        }
        if (!mailClient.isConfigured()) {
            LOG.warn("[四海待办提醒] 邮件服务未配置，跳过启动提醒");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Contract> contractMap = contractRepository.findAllContracts()
                .stream()
                .collect(Collectors.toMap(Contract::getId, contract -> contract, (left, right) -> left));
        Map<String, User> userMap = userRepository.findAllUsers()
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        Map<String, List<ReminderItem>> remindersByUser = contractRepository.findAllProcesses()
                .stream()
                .map(process -> toReminderItem(process, contractMap.get(process.getContractId()), now))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(item -> item.process().getUserId()));

        remindersByUser.forEach((userId, items) -> sendReminderToUser(userMap.get(userId), items));
        LOG.info("[四海待办提醒] 启动检查完成，涉及用户数={}，提醒待办数={}",
                remindersByUser.size(),
                remindersByUser.values().stream().mapToInt(List::size).sum());
    }

    private ReminderItem toReminderItem(ContractProcess process, Contract contract, LocalDateTime now) {
        if (!TaskReminderPolicy.needsReminder(contract, process, now)) {
            return null;
        }
        return new ReminderItem(contract, process, reason(contract, process, now));
    }

    private void sendReminderToUser(User user, List<ReminderItem> items) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            LOG.warn("[四海待办提醒] 用户不存在或未配置邮箱，跳过提醒：userId={}", user == null ? null : user.getId());
            return;
        }
        List<ReminderItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(item -> item.contract().getEndTime()))
                .toList();
        try {
            mailClient.sendHtml(
                    user.getEmail(),
                    "【四海】合同待办提醒：" + sortedItems.size() + "项需处理",
                    buildReminderHtml(user, sortedItems));
        } catch (RuntimeException exception) {
            LOG.error("[四海待办提醒] 邮件发送失败：userId={}，reason={}", user.getId(), exception.getMessage());
        }
    }

    private String reason(Contract contract, ContractProcess process, LocalDateTime now) {
        boolean dueSoon = TaskReminderPolicy.isContractDueWithinOneDay(contract.getEndTime(), now.toLocalDate());
        boolean overdue = TaskReminderPolicy.isPendingMoreThanOneDay(TaskReminderPolicy.pendingSince(contract, process), now);
        if (dueSoon && overdue) {
            return "合同截止日期不足1天，且待办已超过1天未处理";
        }
        if (dueSoon) {
            return "合同截止日期不足1天";
        }
        return "待办已超过1天未处理";
    }

    private String buildReminderHtml(User user, List<ReminderItem> items) {
        String rows = items.stream()
                .map(item -> """
                        <tr>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                          <td style="padding:10px;border-bottom:1px solid #e5e7eb;">%s</td>
                        </tr>
                        """.formatted(
                        escape(item.contract().getNum()),
                        escape(item.contract().getName()),
                        processTypeText(item.process().getType()),
                        item.contract().getEndTime(),
                        pendingDurationText(TaskReminderPolicy.pendingSince(item.contract(), item.process())),
                        item.reason()))
                .collect(Collectors.joining());

        return """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.7;color:#1f2937;background:#f6f8fb;padding:28px;">
                  <div style="max-width:760px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                    <h2 style="margin:0 0 16px;font-size:20px;color:#111827;">四海合同管理系统待办提醒</h2>
                    <p style="margin:0 0 18px;">尊敬的 %s，系统检测到以下合同待办需要关注，请及时登录【四海】合同管理系统处理。</p>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                      <thead>
                        <tr style="background:#f3f6fb;text-align:left;">
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">合同编号</th>
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">合同名称</th>
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">待办类型</th>
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">截止日期</th>
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">待办时长</th>
                          <th style="padding:10px;border-bottom:1px solid #d1d5db;">提醒原因</th>
                        </tr>
                      </thead>
                      <tbody>%s</tbody>
                    </table>
                    <p style="margin:18px 0 0;color:#374151;">建议优先处理临近截止日期的合同，避免影响合同流转和履约安排。</p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0 14px;">
                    <p style="margin:0;color:#6b7280;font-size:12px;">本邮件由四海合同管理系统自动发送，请勿直接回复。</p>
                  </div>
                </div>
                """.formatted(escape(user.getUsername()), rows);
    }

    private String pendingDurationText(LocalDateTime pendingSince) {
        if (pendingSince == null) {
            return "未记录";
        }
        long hours = Math.max(0, Duration.between(pendingSince, LocalDateTime.now()).toHours());
        long days = hours / 24;
        long restHours = hours % 24;
        if (days > 0) {
            return days + "天" + restHours + "小时";
        }
        return restHours + "小时";
    }

    private String processTypeText(ProcessType type) {
        return switch (type) {
            case COUNTERSIGN -> "会签";
            case FINALIZE -> "定稿";
            case APPROVE -> "审批";
            case SIGN -> "签订";
        };
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record ReminderItem(Contract contract, ContractProcess process, String reason) {
    }
}
