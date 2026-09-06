import type { Contract, ContractProcess, SignRecord, User } from "../types";
import { formatDateTime, processText } from "../utils/format";
import { statusRank } from "../utils/flow";
import { ProcessBadge } from "./Badge";

export function ProcessSteps({
  contract,
  processes,
  signRecords = [],
  users
}: {
  contract: Contract;
  processes: ContractProcess[];
  signRecords?: SignRecord[];
  users: User[];
}) {
  const rank = statusRank(contract.status);
  const steps = [
    { label: "起草", done: rank >= 1, active: rank === 1 },
    { label: "会签", done: rank >= 3, active: rank === 2 },
    { label: "定稿", done: rank >= 4, active: rank === 3 },
    { label: "审批", done: rank >= 5, active: rank === 4 },
    { label: "签订", done: rank >= 6, active: rank === 5 }
  ];

  return (
    <div>
      <div className="flex overflow-x-auto pb-2">
        {steps.map((step, index) => (
          <div className="flex min-w-[140px] flex-1 items-center" key={`${step.label}-${index}`}>
            <div className="flex flex-col items-center">
              <span
                className={[
                  "grid h-9 w-9 place-items-center rounded-full border text-body font-semibold",
                  step.done ? "border-primary bg-primary text-white" : step.active ? "border-progress bg-white text-progress" : "border-line bg-white text-weak"
                ].join(" ")}
              >
                {index + 1}
              </span>
              <span className="mt-2 text-body font-medium text-text">{step.label}</span>
            </div>
            {index < steps.length - 1 ? <span className={`mx-4 h-px flex-1 ${steps[index + 1].done ? "bg-primary" : "bg-line"}`} /> : null}
          </div>
        ))}
      </div>
      <div className="mt-6 space-y-3">
        <div className="rounded-card border border-line p-4">
          <div className="flex flex-wrap justify-between gap-3">
            <span className="text-body font-medium text-text">起草</span>
            <span className="text-note text-muted">{formatDateTime(contract.createdAt)}</span>
          </div>
          <p className="m-0 mt-1 text-body text-muted">操作人：{users.find((user) => user.id === contract.drafterId)?.name || contract.drafterId}</p>
        </div>
        {processes.map((process) => (
          <div className="rounded-card border border-line p-4" key={process.id}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span className="text-body font-medium text-text">{processText[process.type]}</span>
              <ProcessBadge state={process.state} />
            </div>
            <p className="m-0 mt-1 text-body text-muted">操作人：{users.find((user) => user.id === process.userId)?.name || process.userId}</p>
            <p className="m-0 mt-1 text-note text-muted">时间：{formatDateTime(process.time)}</p>
            {process.content ? <p className="m-0 mt-2 text-body text-text">意见：{process.content}</p> : null}
          </div>
        ))}
        <SignRecordPanel records={signRecords.filter((record) => record.contractId === contract.id)} />
      </div>
    </div>
  );
}

function SignRecordPanel({ records }: { records: SignRecord[] }) {
  if (records.length === 0) {
    return null;
  }

  return (
    <div className="rounded-card border border-line p-4">
      <div className="text-body font-medium text-text">电子签名记录</div>
      <div className="mt-3 space-y-3">
        {records.map((record) => (
          <div className="rounded-card border border-line bg-section p-3" key={record.id}>
            <div className="grid gap-2 text-body text-muted md:grid-cols-2">
              <div>签署人：{record.signerName || "未填写"}</div>
              <div>签订日期：{record.signDate || "未填写"}</div>
              <div>签订方式：{record.method || "未填写"}</div>
              <div>备注：{record.remark || "无"}</div>
            </div>
            {record.signatureDataUrl ? (
              <div className="mt-3">
                <div className="mb-2 text-note text-muted">电子签名</div>
                <div className="inline-block max-w-full rounded-input border border-line bg-white p-2">
                  <img className="block h-24 max-w-full object-contain" src={record.signatureDataUrl} alt="电子签名" />
                </div>
              </div>
            ) : (
              <div className="mt-2 text-note text-muted">未保存电子签名图片</div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
