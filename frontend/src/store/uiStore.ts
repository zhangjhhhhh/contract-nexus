import { create } from "zustand";

type Toast = {
  id: number;
  type: "success" | "error";
  message: string;
};

type ConfirmState = {
  title: string;
  message: string;
  onConfirm: () => void;
} | null;

type UiState = {
  toast: Toast | null;
  confirm: ConfirmState;
  showToast: (type: Toast["type"], message: string) => void;
  clearToast: () => void;
  openConfirm: (confirm: ConfirmState) => void;
  closeConfirm: () => void;
};

export const useUiStore = create<UiState>((set) => ({
  toast: null,
  confirm: null,
  showToast: (type, message) => {
    const id = Date.now();
    set({ toast: { id, type, message } });
    window.setTimeout(() => {
      set((state) => (state.toast?.id === id ? { toast: null } : state));
    }, 2500);
  },
  clearToast: () => set({ toast: null }),
  openConfirm: (confirm) => set({ confirm }),
  closeConfirm: () => set({ confirm: null })
}));
