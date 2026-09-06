import { Bot, Download } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { fetchContract, fetchContractVersions } from "../api/contracts";
import { getDownloadUrl, getPreviewUrl } from "../api/files";
import type { AiReview, Attachment, Contract, ContractVersion, Customer, User } from "../types";
import { formatDate, formatDateTime } from "../utils/format";
import { StatusBadge } from "./Badge";

export function ContractDetail({ contract, customers, users }: { contract: Contract; customers: Customer[]; users: User[] }) {
  const [currentContract, setCurrentContract] = useState(contract);
  const customer = customers.find((item) => item.id === currentContract.customerId);
  const drafter = users.find((item) => item.id === currentContract.drafterId);
  const attachments = currentContract.attachments ?? [];
  const [versions, setVersions] = useState<ContractVersion[]>([]);
  const aiReview = useMemo(() => parseAiReview(currentContract.aiReview), [currentContract.aiReview]);

  useEffect(() => {
    let alive = true;
    setCurrentContract(contract);
    fetchContract(contract.id)
      .then((item) => {
        if (alive) setCurrentContract(item);
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [contract]);

  useEffect(() => {
    if (currentContract.aiReview) {
      return;
    }

    let alive = true;
    let attempts = 0;
    const timer = window.setInterval(() => {
      attempts += 1;
      fetchContract(currentContract.id)
        .then((item) => {
          if (!alive) return;
          setCurrentContract(item);
          if (item.aiReview || attempts >= 10) {
            window.clearInterval(timer);
          }
        })
        .catch(() => {
          if (attempts >= 10) {
            window.clearInterval(timer);
          }
        });
    }, 3000);

    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [currentContract.id, currentContract.aiReview]);

  useEffect(() => {
    let alive = true;
    fetchContractVersions(currentContract.id)
      .then((items) => {
        if (alive) setVersions(items);
      })
      .catch(() => {
        if (alive) setVersions([]);
      });
    return () => {
      alive = false;
    };
  }, [currentContract.id]);

  const rows = [
    ["合同编号", currentContract.num],
    ["合同名称", currentContract.name],
    ["客户", customer?.name || currentContract.customerId],
    ["起草人", drafter?.name || currentContract.drafterId],
    ["起草时间", formatDateTime(currentContract.createdAt)],
    ["合同周期", `${formatDate(currentContract.beginTime)} 至 ${formatDate(currentContract.endTime)}`]
  ];

  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-2">
        {rows.map(([label, value]) => (
          <div className="rounded-card border border-line bg-white px-4 py-3" key={label}>
            <div className="text-note text-muted">{label}</div>
            <div className="mt-1 text-body text-text">{value}</div>
          </div>
        ))}
        <div className="rounded-card border border-line bg-white px-4 py-3">
          <div className="text-note text-muted">当前状态</div>
          <div className="mt-2"><StatusBadge status={currentContract.status} /></div>
        </div>
      </div>
      <div className="rounded-card border border-line bg-white px-4 py-3">
        <div className="text-note text-muted">合同内容</div>
        <p className="m-0 mt-2 whitespace-pre-wrap text-body text-text">{currentContract.content}</p>
      </div>
      <AiReviewPanel review={aiReview.review} raw={aiReview.raw} />
      {attachments.length > 0 ? (
        <div className="rounded-card border border-line bg-white px-4 py-3">
          <div className="text-note text-muted">合同附件</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {attachments.map((file) => (
              <a
                key={file.name}
                href={`${getDownloadUrl(file.path)}?name=${encodeURIComponent(file.name)}`}
                download={file.name}
                className="inline-flex items-center gap-2 rounded-button bg-section px-3 py-2 text-body font-medium text-primary hover:bg-hover transition-colors"
              >
                <Download className="h-4 w-4" />
                {file.name}
              </a>
            ))}
          </div>
          <DocumentPreviewList files={attachments} />
        </div>
      ) : null}
      {versions.length > 0 ? (
        <div className="rounded-card border border-line bg-white px-4 py-3">
          <div className="text-note text-muted">审批版本记录</div>
          <div className="mt-3 space-y-3">
            {versions.map((version) => {
              const approver = users.find((user) => user.id === version.approverId);
              const versionAttachments = version.attachments ?? [];
              return (
                <div className="rounded-card border border-line p-4" key={version.id}>
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="text-body font-semibold text-text">版本 {version.versionNo}</div>
                    <div className={version.approvalResult === "approved" ? "text-body font-medium text-success" : "text-body font-medium text-error"}>
                      {version.approvalResult === "approved" ? "审批通过" : "审批退回"}
                    </div>
                  </div>
                  <div className="mt-2 grid gap-2 text-body text-muted md:grid-cols-2">
                    <div>审批人：{approver?.name || version.approverId}</div>
                    <div>审批时间：{formatDateTime(version.createdAt)}</div>
                  </div>
                  <div className="mt-2 text-body text-text">审批意见：{version.approvalOpinion || "无"}</div>
                  <details className="mt-3">
                    <summary className="cursor-pointer text-body font-medium text-primary">查看版本内容</summary>
                    <p className="m-0 mt-2 whitespace-pre-wrap text-body text-text">{version.content}</p>
                    {versionAttachments.length > 0 ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {versionAttachments.map((file) => (
                          <a
                            key={`${version.id}-${file.name}`}
                            href={`${getDownloadUrl(file.path)}?name=${encodeURIComponent(file.name)}`}
                            download={file.name}
                            className="inline-flex items-center gap-2 rounded-button bg-section px-3 py-2 text-body font-medium text-primary hover:bg-hover transition-colors"
                          >
                            <Download className="h-4 w-4" />
                            {file.name}
                          </a>
                        ))}
                      </div>
                    ) : null}
                    <DocumentPreviewList files={versionAttachments} idPrefix={version.id} />
                  </details>
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function DocumentPreviewList({ files, idPrefix = "attachment" }: { files: Attachment[]; idPrefix?: string }) {
  const previewFiles = files.filter(isPreviewableAttachment);
  if (previewFiles.length === 0) {
    return null;
  }

  return (
    <div className="mt-4 space-y-4">
      {previewFiles.map((file) => {
        const previewUrl = `${getPreviewUrl(file.path)}?name=${encodeURIComponent(file.name)}`;
        return (
          <div className="overflow-hidden rounded-card border border-line bg-section" key={`${idPrefix}-preview-${file.path}-${file.name}`}>
            <div className="flex items-center justify-between gap-3 border-b border-line bg-white px-3 py-2">
              <div className="min-w-0 truncate text-body font-medium text-text">{file.name}</div>
              <a
                href={previewUrl}
                target="_blank"
                rel="noreferrer"
                className="shrink-0 text-note font-medium text-primary hover:text-primary-hover"
              >
                新窗口打开
              </a>
            </div>
            <iframe
              title={`附件预览：${file.name}`}
              src={previewUrl}
              className="h-[70vh] min-h-[520px] w-full border-0 bg-white"
            />
          </div>
        );
      })}
    </div>
  );
}

function isPreviewableAttachment(file: Attachment) {
  const type = file.type?.toLowerCase();
  const name = file.name?.toLowerCase() ?? "";
  const path = file.path?.toLowerCase() ?? "";
  return type === "pdf" || type === "docx"
    || name.endsWith(".pdf") || name.endsWith(".docx")
    || path.endsWith(".pdf") || path.endsWith(".docx");
}

function AiReviewPanel({ review, raw }: { review?: AiReview; raw?: string }) {
  const riskAlerts = review?.risk_alerts ?? [];
  const overallLevel = review?.overall_assessment?.overall_risk_level || "无";
  const missingClauses = review?.overall_assessment?.missing_clauses ?? [];

  return (
    <div className="rounded-card border border-warning/40 bg-white px-4 py-4 shadow-subtle">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
        <div className="flex items-center gap-2 text-subtitle font-semibold text-text">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-button bg-hover text-primary">
            <Bot className="h-[18px] w-[18px]" />
          </span>
          AI审查：
        </div>
        <span className={levelClass(overallLevel)}>{overallLevel === "无" ? "未生成" : `整体风险：${overallLevel}`}</span>
      </div>

      {review ? (
        <div className="mt-4 space-y-5">
          <div className="grid gap-3 md:grid-cols-2">
            {summaryRows(review).map(([label, value]) => (
              <div className="min-w-0" key={label}>
                <div className="text-note text-muted">{label}</div>
                <div className="mt-1 break-words text-body text-text">{value || "无"}</div>
              </div>
            ))}
          </div>

          <div>
            <div className="mb-2 text-body font-semibold text-text">风险提醒</div>
            {riskAlerts.length > 0 ? (
              <div className="divide-y divide-line rounded-card border border-line">
                {riskAlerts.map((risk, index) => (
                  <div className="p-3" key={`${risk.risk_id}-${index}`}>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={levelClass(risk.risk_level)}>{risk.risk_level}</span>
                      <span className="text-body font-semibold text-text">{risk.risk_category || "风险提醒"}</span>
                      <span className="text-note text-muted">{risk.clause_reference || "无"}</span>
                    </div>
                    <p className="m-0 mt-2 whitespace-pre-wrap break-words text-body text-text">{risk.risk_description || "无"}</p>
                    <p className="m-0 mt-2 whitespace-pre-wrap break-words text-body text-primary">建议：{risk.suggestion || "无"}</p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded-card border border-line bg-section px-3 py-2 text-body text-muted">未发现明确风险。</div>
            )}
          </div>

          <div>
            <div className="text-body font-semibold text-text">整体评估</div>
            <p className="m-0 mt-2 whitespace-pre-wrap break-words text-body text-text">
              {review.overall_assessment?.summary || "无"}
            </p>
            {missingClauses.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {missingClauses.map((clause) => (
                  <span className="rounded-tag bg-section px-2 py-1 text-note text-muted" key={clause}>{clause}</span>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      ) : (
        <div className="mt-3 rounded-card border border-line bg-section px-3 py-2 text-body text-muted">
          {raw ? (
            <pre className="m-0 max-h-64 overflow-auto whitespace-pre-wrap break-words text-note">{raw}</pre>
          ) : (
            "暂未生成审查结果。请确认后端已配置百炼应用后重新起草合同。"
          )}
        </div>
      )}
    </div>
  );
}

function parseAiReview(value: Contract["aiReview"]): { review?: AiReview; raw?: string } {
  if (!value) {
    return {};
  }
  if (typeof value === "object") {
    return { review: value as AiReview };
  }
  try {
    return { review: JSON.parse(value) as AiReview };
  } catch {
    return { raw: value };
  }
}

function summaryRows(review: AiReview) {
  const summary = review.contract_summary ?? {};
  return [
    ["合同标题", summary.contract_title],
    ["合同性质", summary.contract_type],
    ["甲方", summary.party_a],
    ["乙方", summary.party_b],
    ["合同标的", summary.subject_matter],
    ["合同总金额", summary.total_amount],
    ["付款条款", summary.payment_terms],
    ["履行期限", summary.performance_period],
    ["生效日期", summary.effective_date],
    ["终止日期", summary.termination_date],
    ["争议解决", summary.dispute_resolution]
  ];
}

function levelClass(level: string) {
  const normalized = (level || "").trim();
  if (normalized === "高") {
    return "inline-flex items-center rounded-tag bg-error px-2 py-1 text-note font-semibold text-white";
  }
  if (normalized === "中") {
    return "inline-flex items-center rounded-tag bg-warning px-2 py-1 text-note font-semibold text-white";
  }
  if (normalized === "低") {
    return "inline-flex items-center rounded-tag bg-success px-2 py-1 text-note font-semibold text-white";
  }
  return "inline-flex items-center rounded-tag bg-section px-2 py-1 text-note font-semibold text-muted";
}
