import type { ContractStatus, ProcessState } from "../types";
import { statusText } from "../utils/format";

const statusClass: Record<ContractStatus, string> = {
  drafting: "bg-hover text-primary",
  countersigning: "bg-[#FEF3C7] text-[#92400E]",
  finalizing: "bg-hover text-progress",
  approving: "bg-hover text-progress",
  signing: "bg-section text-success",
  completed: "bg-section text-success",
  rejected: "bg-section text-error"
};

const processClass: Record<ProcessState, string> = {
  pending: "bg-[#FEF3C7] text-[#92400E]",
  done: "bg-section text-success",
  rejected: "bg-section text-error"
};

export function StatusBadge({ status }: { status: ContractStatus }) {
  return <span className={`inline-flex rounded-tag px-2 py-1 text-note font-medium ${statusClass[status]}`}>{statusText[status]}</span>;
}

export function ProcessBadge({ state }: { state: ProcessState }) {
  const label = state === "pending" ? "待处理" : state === "done" ? "已完成" : "已拒绝";
  return <span className={`inline-flex rounded-tag px-2 py-1 text-note font-medium ${processClass[state]}`}>{label}</span>;
}
