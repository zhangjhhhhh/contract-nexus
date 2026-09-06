import { useMemo, useState } from "react";
import { sendLegalAIMessage } from "../api/legalAi";
import { useAppStore } from "../store/appStore";
import type { Contract } from "../types";
import { LegalAIChatButton } from "./LegalAIChatButton";
import { LegalAIChatPanel, type LegalAIChatMessage } from "./LegalAIChatPanel";

const welcomeMessage: LegalAIChatMessage = {
  id: "welcome",
  role: "assistant",
  content: "\u60a8\u597d\uff0c\u6211\u662f\u56db\u6d77\u3002\u53ef\u4ee5\u5148\u9009\u62e9\u4e00\u4efd\u5408\u540c\uff0c\u518d\u8ba9\u6211\u5206\u6790\u98ce\u9669\u3001\u603b\u7ed3\u6761\u6b3e\u6216\u63d0\u70bc\u5173\u952e\u4e49\u52a1\u3002\u6211\u7684\u56de\u7b54\u4ec5\u4f9b\u53c2\u8003\uff0c\u4e0d\u6784\u6210\u6b63\u5f0f\u6cd5\u5f8b\u610f\u89c1\u3002"
};

export function LegalAIChatWidget() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedContractId, setSelectedContractId] = useState<string | undefined>();
  const [messages, setMessages] = useState<LegalAIChatMessage[]>([welcomeMessage]);
  const session = useAppStore((state) => state.session);
  const permissions = useAppStore((state) => state.permissions);
  const contracts = useAppStore((state) => state.contracts);
  const processes = useAppStore((state) => state.processes);

  const availableContracts = useMemo(
    () => filterContractsByPermission(contracts, processes, permissions, session?.userId),
    [contracts, permissions, processes, session?.userId]
  );

  const handleSend = async () => {
    const content = input.trim();
    if (!content || loading) {
      return;
    }

    const userMessage: LegalAIChatMessage = { id: makeMessageId("user"), role: "user", content };
    const history = messages
      .filter((message) => message.id !== welcomeMessage.id)
      .map(({ role, content: messageContent }) => ({ role, content: messageContent }));

    setMessages((items) => [...items, userMessage]);
    setInput("");
    setError("");
    setLoading(true);

    try {
      const result = await sendLegalAIMessage({
        message: content,
        history,
        userId: session?.userId,
        contractId: selectedContractId
      });
      setMessages((items) => [
        ...items,
        {
          id: makeMessageId("assistant"),
          role: "assistant",
          content: result.reply
        }
      ]);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "\u56db\u6d77\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleNewConversation = () => {
    setMessages([welcomeMessage]);
    setInput("");
    setError("");
    setSelectedContractId(undefined);
  };

  return open ? (
    <LegalAIChatPanel
      contracts={availableContracts}
      error={error}
      loading={loading}
      messages={messages}
      selectedContractId={selectedContractId}
      value={input}
      onChange={setInput}
      onClose={() => setOpen(false)}
      onNewConversation={handleNewConversation}
      onPickPrompt={setInput}
      onSelectContract={setSelectedContractId}
      onSend={handleSend}
    />
  ) : (
    <LegalAIChatButton onClick={() => setOpen(true)} />
  );
}

function filterContractsByPermission(
  contracts: Contract[],
  processes: { contractId: string; userId: string }[],
  permissions: string[],
  userId?: string
) {
  if (permissions.includes("query:info") || permissions.includes("base:contract")) {
    return contracts;
  }
  if (!userId) {
    return [];
  }
  const contractIds = new Set<string>();
  contracts.filter((contract) => contract.drafterId === userId).forEach((contract) => contractIds.add(contract.id));
  processes.filter((process) => process.userId === userId).forEach((process) => contractIds.add(process.contractId));
  return contracts.filter((contract) => contractIds.has(contract.id));
}

function makeMessageId(role: LegalAIChatMessage["role"]) {
  return `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
