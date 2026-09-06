import { Bot } from "lucide-react";

export function LegalAIChatButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      aria-label={"\u6253\u5f00\u56db\u6d77"}
      className="fixed bottom-6 right-6 z-40 grid h-14 w-14 place-items-center rounded-full border border-primary bg-primary text-white shadow-[0_12px_30px_rgba(30,58,95,0.25)] transition hover:bg-secondary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary md:bottom-8 md:right-8"
      title={"\u56db\u6d77"}
      type="button"
      onClick={onClick}
    >
      <Bot className="h-6 w-6" />
      <span className="sr-only">{"\u56db\u6d77"}</span>
    </button>
  );
}
