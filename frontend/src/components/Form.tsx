import type { ReactNode } from "react";

export function Field({ label, error, children, required = false }: { label: string; error?: string; children: ReactNode; required?: boolean }) {
  return (
    <label className="block">
      <span className="mb-2 block text-body font-medium text-text">
        {label}
        {required ? <span className="text-error"> *</span> : null}
      </span>
      {children}
      {error ? <span className="mt-1 block text-note text-error">{error}</span> : null}
    </label>
  );
}

export const inputClass =
  "h-12 w-full rounded-input border border-line bg-white px-3 text-field text-text transition-colors hover:border-weak focus:border-progress focus:outline-none";

export const selectClass = inputClass;

export const textareaClass =
  "min-h-[120px] w-full rounded-input border border-line bg-white px-3 py-3 text-field text-text transition-colors hover:border-weak focus:border-progress focus:outline-none";
