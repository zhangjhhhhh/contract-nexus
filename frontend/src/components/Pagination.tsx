import { Button } from "./Button";

export function usePageData<T>(items: T[], page: number, pageSize = 10) {
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const safePage = Math.min(page, totalPages);
  return {
    totalPages,
    safePage,
    pageItems: items.slice((safePage - 1) * pageSize, safePage * pageSize)
  };
}

export function Pagination({
  total,
  page,
  pageSize = 10,
  onChange
}: {
  total: number;
  page: number;
  pageSize?: number;
  onChange: (page: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="mt-4 flex flex-wrap items-center justify-end gap-3 text-note text-muted">
      <span>共 {total} 条</span>
      <span>每页 {pageSize} 条</span>
      <Button variant="secondary" className="min-h-9 px-3 text-note" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        上一页
      </Button>
      <span className="rounded-tag border border-line px-3 py-1 text-text">{page}</span>
      <Button variant="secondary" className="min-h-9 px-3 text-note" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        下一页
      </Button>
    </div>
  );
}
