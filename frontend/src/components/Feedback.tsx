import { CheckCircle2, XCircle } from "lucide-react";
import { useUiStore } from "../store/uiStore";
import { Button } from "./Button";

export function ToastHost() {
  const toast = useUiStore((state) => state.toast);
  if (!toast) return null;

  return (
    <div className="fixed left-1/2 top-5 z-50 -translate-x-1/2">
      <div className="flex min-w-[320px] items-center gap-3 rounded-card border border-line bg-white px-4 py-3 text-body text-text shadow-subtle">
        <span className={`h-6 w-1 rounded-tag ${toast.type === "success" ? "bg-success" : "bg-error"}`} />
        {toast.type === "success" ? <CheckCircle2 className="h-5 w-5 text-success" /> : <XCircle className="h-5 w-5 text-error" />}
        <span>{toast.message}</span>
      </div>
    </div>
  );
}

export function ConfirmDialog() {
  const confirm = useUiStore((state) => state.confirm);
  const closeConfirm = useUiStore((state) => state.closeConfirm);
  if (!confirm) return null;

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-[rgba(0,0,0,0.45)] p-6">
      <div className="w-full max-w-md rounded-card bg-white p-6 shadow-subtle">
        <h2 className="m-0 text-subtitle font-semibold text-text">{confirm.title}</h2>
        <p className="mb-6 mt-3 text-body text-muted">{confirm.message}</p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={closeConfirm}>
            取消
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              confirm.onConfirm();
              closeConfirm();
            }}
          >
            确认删除
          </Button>
        </div>
      </div>
    </div>
  );
}
