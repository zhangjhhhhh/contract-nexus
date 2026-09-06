export function EmptyState({ text = "暂无数据" }: { text?: string }) {
  return (
    <div className="flex min-h-[180px] flex-col items-center justify-center rounded-card border border-dashed border-line bg-white px-6 py-8 text-center">
      <svg width="72" height="54" viewBox="0 0 72 54" fill="none" aria-hidden="true">
        <path d="M7 14.5h21.2l5 6H65v25.5a5 5 0 0 1-5 5H12a5 5 0 0 1-5-5V14.5Z" stroke="#9CA3AF" strokeWidth="2" />
        <path d="M7 14.5V9a5 5 0 0 1 5-5h16l5 6h27a5 5 0 0 1 5 5v5.5" stroke="#9CA3AF" strokeWidth="2" />
      </svg>
      <p className="m-0 mt-4 text-body text-muted">{text}</p>
    </div>
  );
}
