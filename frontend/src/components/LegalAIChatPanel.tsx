import { Check, FileText, Loader2, Plus, Search, Send, X } from "lucide-react";
import type { KeyboardEvent, ReactNode } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { LegalAIChatRole } from "../api/legalAi";
import type { Contract } from "../types";
import { Button } from "./Button";
import { inputClass } from "./Form";

export type LegalAIChatMessage = {
  id: string;
  role: LegalAIChatRole;
  content: string;
};

type LegalAIChatPanelProps = {
  messages: LegalAIChatMessage[];
  value: string;
  loading: boolean;
  error?: string;
  contracts: Contract[];
  selectedContractId?: string;
  onChange: (value: string) => void;
  onClose: () => void;
  onSend: () => void;
  onNewConversation: () => void;
  onPickPrompt: (value: string) => void;
  onSelectContract: (contractId?: string) => void;
};

const sihaiName = "\u56db\u6d77";
const recommendedPrompts = [
  "\u5206\u6790\u98ce\u9669",
  "\u603b\u7ed3\u5408\u540c",
  "\u5217\u51fa\u5173\u952e\u4e49\u52a1",
  "\u63d0\u70bc\u4ed8\u6b3e\u6761\u6b3e"
];

export function LegalAIChatPanel({
  messages,
  value,
  loading,
  error,
  contracts,
  selectedContractId,
  onChange,
  onClose,
  onSend,
  onNewConversation,
  onPickPrompt,
  onSelectContract
}: LegalAIChatPanelProps) {
  const [contractPickerOpen, setContractPickerOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const canSend = value.trim().length > 0 && !loading;
  const selectedContract = contracts.find((contract) => contract.id === selectedContractId);

  const filteredContracts = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) return contracts;
    return contracts.filter((contract) => {
      const text = `${contract.num} ${contract.name}`.toLowerCase();
      return text.includes(normalized);
    });
  }, [contracts, keyword]);

  useEffect(() => {
    const element = messageListRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages.length, loading, error]);

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== "Enter" || event.shiftKey) {
      return;
    }
    event.preventDefault();
    if (canSend) {
      onSend();
    }
  };

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-[rgba(31,41,55,0.18)] md:left-auto md:top-16 md:bg-transparent">
      <aside
        aria-label={sihaiName}
        className="flex h-full w-full flex-col border-l border-line bg-white shadow-[0_12px_36px_rgba(31,41,55,0.16)] md:h-[calc(100vh-64px)] md:w-[460px]"
      >
        <header className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div>
            <h2 className="m-0 text-subtitle font-semibold text-text">{sihaiName}</h2>
            <p className="m-0 mt-1 text-note text-muted">{"\u5408\u540c\u4e0e\u6cd5\u5f8b\u95ee\u7b54\u667a\u80fd\u4f53"}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button
              className="inline-flex h-9 items-center rounded-button border border-line px-3 text-note font-medium text-muted transition hover:bg-hover hover:text-text"
              type="button"
              onClick={onNewConversation}
            >
              开启新对话
            </button>
            <button
              aria-label={"\u5173\u95ed" + sihaiName}
              className="grid h-9 w-9 place-items-center rounded-button text-muted transition hover:bg-hover hover:text-text"
              type="button"
              onClick={onClose}
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </header>

        <div className="border-b border-line bg-section px-5 py-3">
          <div className="flex flex-wrap gap-2">
            {recommendedPrompts.map((prompt) => (
              <button
                key={prompt}
                className="rounded-tag border border-line bg-white px-3 py-1.5 text-note font-medium text-primary transition hover:bg-hover"
                type="button"
                onClick={() => onPickPrompt(prompt)}
              >
                {prompt}
              </button>
            ))}
          </div>
          {selectedContract ? (
            <div className="mt-3 flex items-center justify-between gap-3 rounded-card border border-line bg-white px-3 py-2">
              <div className="min-w-0">
                <div className="truncate text-note font-semibold text-text">{selectedContract.name}</div>
                <div className="truncate text-helper text-muted">{selectedContract.num}</div>
              </div>
              <button
                className="shrink-0 text-note font-medium text-muted hover:text-text"
                type="button"
                onClick={() => onSelectContract(undefined)}
              >
                {"\u79fb\u9664"}
              </button>
            </div>
          ) : null}
        </div>

        <div ref={messageListRef} className="page-scroll flex-1 space-y-4 overflow-y-auto px-5 py-5">
          {messages.map((message) => (
            <MessageBubble key={message.id} message={message} />
          ))}
          {loading ? (
            <div className="flex justify-start">
              <div className="max-w-[84%] rounded-card border border-line bg-section px-4 py-3 text-body text-muted">
                <span className="inline-flex items-center gap-2">
                  <Loader2 className="spin h-[18px] w-[18px]" />
                  {sihaiName}{"\u6b63\u5728\u601d\u8003..."}
                </span>
              </div>
            </div>
          ) : null}
          {error ? (
            <div className="rounded-card border border-error/30 bg-white px-4 py-3 text-body text-error">
              {error}
            </div>
          ) : null}
        </div>

        <footer className="relative border-t border-line bg-white px-5 py-4">
          {contractPickerOpen ? (
            <div className="absolute bottom-[calc(100%-8px)] left-5 right-5 z-10 rounded-card border border-line bg-white p-3 shadow-[0_12px_36px_rgba(31,41,55,0.16)]">
              <div className="mb-3 flex items-center gap-2">
                <Search className="h-4 w-4 text-muted" />
                <input
                  className={`${inputClass} h-9`}
                  placeholder={"\u641c\u7d22\u5408\u540c\u540d\u79f0\u6216\u7f16\u53f7"}
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </div>
              <div className="page-scroll max-h-64 space-y-1 overflow-y-auto">
                {filteredContracts.length > 0 ? (
                  filteredContracts.map((contract) => {
                    const active = contract.id === selectedContractId;
                    return (
                      <button
                        key={contract.id}
                        className={[
                          "flex w-full items-center gap-3 rounded-button px-3 py-2 text-left transition",
                          active ? "bg-hover text-primary" : "text-text hover:bg-section"
                        ].join(" ")}
                        type="button"
                        onClick={() => {
                          onSelectContract(contract.id);
                          setContractPickerOpen(false);
                        }}
                      >
                        <FileText className="h-4 w-4 shrink-0" />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-body font-medium">{contract.name}</span>
                          <span className="block truncate text-helper text-muted">{contract.num}</span>
                        </span>
                        {active ? <Check className="h-4 w-4 shrink-0" /> : null}
                      </button>
                    );
                  })
                ) : (
                  <div className="rounded-card bg-section px-3 py-2 text-body text-muted">{"\u6682\u65e0\u53ef\u9009\u62e9\u5408\u540c"}</div>
                )}
              </div>
            </div>
          ) : null}

          <textarea
            className="min-h-[96px] w-full rounded-input border border-line px-3 py-2 text-body text-text transition focus:border-primary focus:outline-none"
            placeholder={"\u5411" + sihaiName + "\u63d0\u95ee\uff0cEnter \u53d1\u9001\uff0cShift + Enter \u6362\u884c"}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={handleKeyDown}
          />
          <div className="mt-3 flex items-center justify-between gap-3">
            <button
              aria-label={"\u9009\u62e9\u5408\u540c"}
              className="grid h-9 w-9 shrink-0 place-items-center rounded-button border border-line text-muted transition hover:bg-hover hover:text-text"
              type="button"
              onClick={() => setContractPickerOpen((open) => !open)}
            >
              <Plus className="h-5 w-5" />
            </button>
            <Button disabled={!canSend} icon={<Send className="h-[18px] w-[18px]" />} loading={loading} onClick={onSend}>
              {"\u53d1\u9001"}
            </Button>
          </div>
        </footer>
      </aside>
    </div>
  );
}

function MessageBubble({ message }: { message: LegalAIChatMessage }) {
  const isUser = message.role === "user";

  return (
    <div className={isUser ? "flex justify-end" : "flex justify-start"}>
      <div
        className={[
          "max-w-[84%] whitespace-pre-wrap break-words rounded-card px-4 py-3 text-body",
          isUser ? "bg-primary text-white" : "border border-line bg-section text-text"
        ].join(" ")}
      >
        {isUser ? message.content : <MarkdownMessage content={message.content} />}
      </div>
    </div>
  );
}

type MarkdownBlock =
  | { type: "heading"; level: 1 | 2 | 3; text: string }
  | { type: "paragraph"; lines: string[] }
  | { type: "ul" | "ol"; items: string[] }
  | { type: "quote"; lines: string[] }
  | { type: "code"; language: string; text: string };

function MarkdownMessage({ content }: { content: string }) {
  const blocks = useMemo(() => parseMarkdown(content), [content]);

  return (
    <div className="space-y-2 whitespace-normal break-words leading-relaxed">
      {blocks.map((block, index) => renderMarkdownBlock(block, index))}
    </div>
  );
}

function parseMarkdown(content: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  let paragraph: string[] = [];
  let list: { type: "ul" | "ol"; items: string[] } | null = null;
  let quote: string[] = [];
  let code: { language: string; lines: string[] } | null = null;

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: "paragraph", lines: paragraph });
      paragraph = [];
    }
  };
  const flushList = () => {
    if (list) {
      blocks.push({ type: list.type, items: list.items });
      list = null;
    }
  };
  const flushQuote = () => {
    if (quote.length > 0) {
      blocks.push({ type: "quote", lines: quote });
      quote = [];
    }
  };
  const flushTextBlocks = () => {
    flushParagraph();
    flushList();
    flushQuote();
  };

  for (const rawLine of lines) {
    const line = rawLine.replace(/\s+$/g, "");
    const trimmed = line.trim();

    if (code) {
      if (trimmed.startsWith("```")) {
        blocks.push({ type: "code", language: code.language, text: code.lines.join("\n") });
        code = null;
      } else {
        code.lines.push(line);
      }
      continue;
    }

    const fenceMatch = trimmed.match(/^```([\w-]*)/);
    if (fenceMatch) {
      flushTextBlocks();
      code = { language: fenceMatch[1] ?? "", lines: [] };
      continue;
    }

    if (!trimmed) {
      flushTextBlocks();
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,3})\s+(.+)$/);
    if (headingMatch) {
      flushTextBlocks();
      blocks.push({
        type: "heading",
        level: headingMatch[1].length as 1 | 2 | 3,
        text: headingMatch[2].trim()
      });
      continue;
    }

    const unorderedMatch = line.match(/^\s*[-*]\s+(.+)$/);
    if (unorderedMatch) {
      flushParagraph();
      flushQuote();
      if (!list || list.type !== "ul") {
        flushList();
        list = { type: "ul", items: [] };
      }
      list.items.push(unorderedMatch[1].trim());
      continue;
    }

    const orderedMatch = line.match(/^\s*\d+\.\s+(.+)$/);
    if (orderedMatch) {
      flushParagraph();
      flushQuote();
      if (!list || list.type !== "ol") {
        flushList();
        list = { type: "ol", items: [] };
      }
      list.items.push(orderedMatch[1].trim());
      continue;
    }

    const quoteMatch = line.match(/^\s*>\s?(.*)$/);
    if (quoteMatch) {
      flushParagraph();
      flushList();
      quote.push(quoteMatch[1]);
      continue;
    }

    flushList();
    flushQuote();
    paragraph.push(line);
  }

  if (code) {
    blocks.push({ type: "code", language: code.language, text: code.lines.join("\n") });
  }
  flushTextBlocks();
  return blocks.length > 0 ? blocks : [{ type: "paragraph", lines: [content] }];
}

function renderMarkdownBlock(block: MarkdownBlock, index: number) {
  if (block.type === "heading") {
    const className = [
      "m-0 font-semibold text-text",
      block.level === 1 ? "text-[18px]" : block.level === 2 ? "text-[17px]" : "text-body"
    ].join(" ");
    const children = renderInlineMarkdown(block.text, `h-${index}`);
    if (block.level === 1) return <h3 className={className} key={index}>{children}</h3>;
    if (block.level === 2) return <h4 className={className} key={index}>{children}</h4>;
    return <h5 className={className} key={index}>{children}</h5>;
  }

  if (block.type === "ul" || block.type === "ol") {
    const Tag = block.type;
    return (
      <Tag className="my-0 space-y-1 pl-5 marker:text-muted" key={index}>
        {block.items.map((item, itemIndex) => (
          <li key={itemIndex}>{renderInlineMarkdown(item, `${index}-${itemIndex}`)}</li>
        ))}
      </Tag>
    );
  }

  if (block.type === "quote") {
    return (
      <blockquote className="m-0 border-l-2 border-line pl-3 text-muted" key={index}>
        {block.lines.map((line, lineIndex) => (
          <p className="m-0" key={lineIndex}>{renderInlineMarkdown(line, `${index}-${lineIndex}`)}</p>
        ))}
      </blockquote>
    );
  }

  if (block.type === "code") {
    return (
      <pre className="m-0 max-w-full overflow-x-auto rounded-input bg-white px-3 py-2 text-note text-text" key={index}>
        <code>{block.text}</code>
      </pre>
    );
  }

  if (block.type === "paragraph") {
    return (
      <p className="m-0" key={index}>
        {block.lines.map((line, lineIndex) => (
          <span key={lineIndex}>
            {lineIndex > 0 ? <br /> : null}
            {renderInlineMarkdown(line, `${index}-${lineIndex}`)}
          </span>
        ))}
      </p>
    );
  }

  return null;
}

function renderInlineMarkdown(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const tokenPattern = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|\[[^\]]+\]\(([^)\s]+)\))/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = tokenPattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }

    const token = match[0];
    const key = `${keyPrefix}-${match.index}`;
    if (token.startsWith("`")) {
      nodes.push(<code className="rounded bg-white px-1 py-0.5 text-note text-primary" key={key}>{token.slice(1, -1)}</code>);
    } else if (token.startsWith("**")) {
      nodes.push(<strong className="font-semibold" key={key}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("*")) {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>);
    } else {
      const linkMatch = token.match(/^\[([^\]]+)\]\(([^)\s]+)\)$/);
      const href = linkMatch?.[2] ?? "";
      const label = linkMatch?.[1] ?? token;
      nodes.push(isSafeLink(href)
        ? (
          <a className="font-medium text-primary underline underline-offset-2" href={href} key={key} rel="noreferrer" target="_blank">
            {label}
          </a>
        )
        : label);
    }
    lastIndex = match.index + token.length;
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}

function isSafeLink(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return value.startsWith("/") && !value.startsWith("//");
  }
}
