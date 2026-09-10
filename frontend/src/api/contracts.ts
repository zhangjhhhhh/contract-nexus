import type { Contract, ContractProcess, ContractVersion, ProcessType, SignRecord } from "../types";
import { request } from "./http";

type PageResponse<T> = {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
};

export type DraftContractRequest = Omit<Contract, "id" | "num" | "createdAt" | "status">;
export type AssignContractRequest = {
  countersignUserIds: string[];
  approveUserIds: string[];
  signUserIds: string[];
};
export type ProcessSubmitRequest = {
  userId: string;
  type: ProcessType;
  content: string;
  result?: "approved" | "rejected";
};
export type FinalizeContractRequest = {
  userId: string;
  content: string;
  attachments?: { name: string; type: string; path: string }[];
};
export type SignContractRequest = {
  userId: string;
  signDate: string;
  method: string;
  remark?: string;
  signerName?: string;
  signatureDataUrl?: string;
};

export async function fetchContracts() {
  const data = await request<PageResponse<Contract>>("/contracts?page=1&pageSize=1000");
  return data.list;
}

export async function fetchContract(contractId: string) {
  return request<Contract>(`/contracts/${contractId}`);
}

export async function retryAiReview(contractId: string) {
  return request<Contract>(`/contracts/${contractId}/ai-review/retry`, { method: "POST" });
}

export async function fetchContractProcesses(contractId: string) {
  return request<ContractProcess[]>(`/contracts/${contractId}/processes`);
}

export async function fetchContractSignRecords(contractId: string) {
  return request<SignRecord[]>(`/contracts/${contractId}/sign-records`);
}

export async function fetchContractVersions(contractId: string) {
  return request<ContractVersion[]>(`/contracts/${contractId}/versions`);
}

export async function fetchAllContractProcesses(contracts: Contract[]) {
  const groups = await Promise.all(contracts.map((contract) => fetchContractProcesses(contract.id)));
  return groups.flat();
}

export async function fetchAllContractSignRecords(contracts: Contract[]) {
  const groups = await Promise.all(contracts.map((contract) => fetchContractSignRecords(contract.id)));
  return groups.flat();
}

export async function draftContract(payload: DraftContractRequest) {
  return request<Contract>("/contracts", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function assignContract(contractId: string, payload: AssignContractRequest) {
  return request<Contract>(`/contracts/${contractId}/assign`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function submitCountersign(contractId: string, payload: { userId: string; content: string }) {
  return request<Contract>(`/contracts/${contractId}/countersign`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function finalizeContract(contractId: string, payload: FinalizeContractRequest) {
  return request<Contract>(`/contracts/${contractId}/finalize`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function approveContract(contractId: string, payload: { userId: string; result: "approved" | "rejected"; content: string }) {
  return request<Contract>(`/contracts/${contractId}/approve`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function signContract(contractId: string, payload: SignContractRequest) {
  return request<Contract>(`/contracts/${contractId}/sign`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function updateContract(contractId: string, payload: DraftContractRequest) {
  return request<Contract>(`/contracts/${contractId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function deleteContract(contractId: string) {
  return request<void>(`/contracts/${contractId}`, { method: "DELETE" });
}

export async function retractContract(contractId: string, userId: string) {
  return request<Contract>(`/contracts/${contractId}/retract`, {
    method: "POST",
    body: JSON.stringify({ userId })
  });
}

