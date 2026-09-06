import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: "#1E3A5F",
        secondary: "#2C4A6E",
        bg: "#FFFFFF",
        section: "#F7F8FA",
        line: "#E5E7EB",
        text: "#1F2937",
        muted: "#6B7280",
        weak: "#9CA3AF",
        success: "#10B981",
        warning: "#F59E0B",
        error: "#DC2626",
        progress: "#2563EB",
        hover: "#F0F4F9"
      },
      fontFamily: {
        sans: [
          "PingFang SC",
          "HarmonyOS Sans SC",
          "Microsoft YaHei",
          "-apple-system",
          "Helvetica Neue",
          "Arial",
          "sans-serif"
        ]
      },
      fontSize: {
        helper: ["14px", { lineHeight: "1.6" }],
        note: ["15px", { lineHeight: "1.6" }],
        body: ["17px", { lineHeight: "1.65" }],
        field: ["17px", { lineHeight: "1.5" }],
        subtitle: ["20px", { lineHeight: "1.45" }],
        title: ["28px", { lineHeight: "1.35" }],
        metric: ["36px", { lineHeight: "1.15" }]
      },
      boxShadow: {
        subtle: "0 1px 2px rgba(0,0,0,0.04)"
      },
      borderRadius: {
        button: "6px",
        card: "8px",
        input: "6px",
        tag: "4px"
      }
    }
  },
  plugins: []
} satisfies Config;
