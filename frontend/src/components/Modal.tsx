import { X } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "./Button";

export function Modal({
  title,
  children,
  footer,
  onClose,
  width = "max-w-3xl"
}: {
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
  width?: string;
}) {
  return (
    <div className="fixed inset-0 z-40 grid place-items-center bg-[rgba(0,0,0,0.45)] p-6">
      <div className={`max-h-[calc(100vh-48px)] w-full overflow-hidden rounded-card bg-white shadow-subtle ${width}`}>
        <div className="flex items-center justify-between border-b border-line px-6 py-4">
          <h2 className="m-0 text-subtitle font-semibold text-text">{title}</h2>
          <Button variant="text" className="min-h-8 px-2" onClick={onClose} aria-label="关闭">
            <X className="h-5 w-5" />
          </Button>
        </div>
        <div className="max-h-[calc(100vh-190px)] overflow-auto p-6 page-scroll">{children}</div>
        {footer ? <div className="flex justify-end gap-3 border-t border-line px-6 py-4">{footer}</div> : null}
      </div>
    </div>
  );
}
