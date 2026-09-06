import dayjs from "dayjs";
import type { ContractStatus, ProcessType } from "../types";

export const statusText: Record<ContractStatus, string> = {
  drafting: "起草",
  countersigning: "待会签",
  finalizing: "待定稿",
  approving: "待审批",
  signing: "待签订",
  completed: "签订完成",
  rejected: "已拒绝"
};

export const processText: Record<ProcessType, string> = {
  countersign: "会签",
  finalize: "定稿",
  approve: "审批",
  sign: "签订"
};

export function formatDate(value?: string) {
  if (!value) return "-";
  return dayjs(value).isValid() ? dayjs(value).format("YYYY-MM-DD") : value.slice(0, 10);
}

export function formatDateTime(value?: string) {
  if (!value) return "-";
  return dayjs(value).isValid() ? dayjs(value).format("YYYY-MM-DD HH:mm") : value;
}

export function makeId(prefix: string, count: number) {
  return `${prefix}${String(count + 1).padStart(3, "0")}`;
}

export function downloadText(filename: string, content: string, mime = "text/csv;charset=utf-8") {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
