import { Download } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { inputClass } from "../../components/Form";
import { PageHeader, Section } from "../../components/Page";
import { Pagination, usePageData } from "../../components/Pagination";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import { downloadText, formatDateTime } from "../../utils/format";

export function LogPage() {
  const [keyword, setKeyword] = useState("");
  const [begin, setBegin] = useState("");
  const [end, setEnd] = useState("");
  const [page, setPage] = useState(1);
  const logs = useAppStore((state) => state.logs);
  const showToast = useUiStore((state) => state.showToast);

  const filtered = useMemo(() => {
    return logs.filter((log) => {
      const date = log.time.slice(0, 10);
      const text = `${log.userName} ${log.content}`.toLowerCase();
      return (!keyword || text.includes(keyword.toLowerCase())) && (!begin || date >= begin) && (!end || date <= end);
    });
  }, [begin, end, keyword, logs]);

  const pageData = usePageData(filtered, page);

  return (
    <>
      <PageHeader
        title="日志管理"
        breadcrumb={["系统管理", "日志管理"]}
        extra={
          <Button
            icon={<Download className="h-[18px] w-[18px]" />}
            onClick={() => {
              const csv = ["操作人,操作内容,操作时间", ...filtered.map((log) => `${log.userName},${log.content},${log.time}`)].join("\n");
              downloadText("contract-system-logs.csv", `\uFEFF${csv}`);
              showToast("success", "日志 CSV 已导出");
            }}
          >
            导出 CSV
          </Button>
        }
      />
      <Section title="操作日志" description="支持时间区间筛选和关键字搜索。">
        <div className="mb-6 grid gap-4 lg:grid-cols-[minmax(220px,1fr)_180px_180px]">
          <input className={inputClass} placeholder="搜索操作人或内容" value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(1); }} />
          <input className={inputClass} type="date" value={begin} onChange={(event) => { setBegin(event.target.value); setPage(1); }} />
          <input className={inputClass} type="date" value={end} onChange={(event) => { setEnd(event.target.value); setPage(1); }} />
        </div>
        <div className="overflow-x-auto">
          <table>
            <thead><tr><th>操作人</th><th>操作内容</th><th>操作时间</th></tr></thead>
            <tbody>
              {pageData.pageItems.map((log) => (
                <tr key={log.id}>
                  <td className="font-medium">{log.userName}</td>
                  <td>{log.content}</td>
                  <td>{formatDateTime(log.time)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination total={filtered.length} page={pageData.safePage} onChange={setPage} />
      </Section>
    </>
  );
}
