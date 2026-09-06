import { request } from "./http";

export type LegalAIChatRole = "user" | "assistant";

export type LegalAIChatHistoryItem = {
  role: LegalAIChatRole;
  content: string;
};

export type LegalAIChatRequest = {
  message: string;
  history: LegalAIChatHistoryItem[];
  userId?: string;
  contractId?: string;
};

export type LegalAIChatResponse = {
  reply: string;
};

export async function sendLegalAIMessage(payload: LegalAIChatRequest) {
  return request<LegalAIChatResponse>("/legal-ai/chat", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
