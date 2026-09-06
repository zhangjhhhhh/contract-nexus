import { Loader2 } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "text" | "danger";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  loading?: boolean;
  icon?: ReactNode;
};

const variantClass: Record<ButtonVariant, string> = {
  primary: "border-primary bg-primary text-white hover:bg-secondary",
  secondary: "border-primary bg-white text-primary hover:bg-hover",
  text: "border-transparent bg-transparent text-primary hover:bg-hover",
  danger: "border-error bg-white text-error hover:bg-hover"
};

export function Button({ variant = "primary", loading = false, icon, children, className = "", disabled, ...props }: ButtonProps) {
  return (
    <button
      className={[
        "inline-flex min-h-10 items-center justify-center gap-2 rounded-button border px-4 text-body font-medium leading-none transition-colors disabled:opacity-60",
        variantClass[variant],
        variant === "text" ? "px-2" : "",
        className
      ].join(" ")}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? <Loader2 className="spin h-[18px] w-[18px]" /> : icon}
      {children}
    </button>
  );
}
