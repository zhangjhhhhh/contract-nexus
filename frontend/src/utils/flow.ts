import type { Contract, ContractProcess, ContractStatus, ProcessType } from "../types";

export const flowSteps: { key: "draft" | ProcessType; label: string }[] = [
  { key: "draft", label: "起草" },
  { key: "countersign", label: "会签" },
  { key: "finalize", label: "定稿" },
  { key: "approve", label: "审批" },
  { key: "sign", label: "签订" }
];

export function statusRank(status: ContractStatus) {
  const ranks: Record<ContractStatus, number> = {
    drafting: 1,
    countersigning: 2,
    finalizing: 3,
    approving: 4,
    signing: 5,
    completed: 6,
    rejected: 0
  };
  return ranks[status];
}

export function isCurrentProcess(contract: Contract, type: ProcessType) {
  const expected: Record<ProcessType, ContractStatus> = {
    countersign: "countersigning",
    finalize: "finalizing",
    approve: "approving",
    sign: "signing"
  };
  return contract.status === expected[type];
}

export function getPendingProcess(processes: ContractProcess[], contractId: string, type: ProcessType, userId: string) {
  return processes.find(
    (process) =>
      process.contractId === contractId &&
      process.type === type &&
      process.userId === userId &&
      process.state === "pending"
  );
}

export function allProcessesDone(processes: ContractProcess[], contractId: string, type: ProcessType) {
  const list = processes.filter((process) => process.contractId === contractId && process.type === type);
  return list.length > 0 && list.every((process) => process.state === "done");
}
