import { useMemo, useState } from "react";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { inputClass, selectClass } from "../../components/Form";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { Pagination, usePageData } from "../../components/Pagination";
import { useAppStore } from "../../store/appStore";
import type { Contract, ContractStatus } from "../../types";
import { statusText } from "../../utils/format";

export function InfoQueryPage() {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("");
  const [begin, setBegin] = useState("");
  const [end, setEnd] = useState("");
  const [page, setPage] = useState(1);
  const [active, setActive] = useState<Contract | null>(null);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);

  const filtered = useMemo(() => {
    return contracts.filter((contract) => {
      const customer = customers.find((item) => item.id === contract.customerId)?.name || "";
      const text = `${contract.num} ${contract.name} ${customer}`.toLowerCase();
      const date = contract.createdAt.slice(0, 10);
      return (
        (!keyword || text.includes(keyword.toLowerCase())) &&
        (!status || contract.status === status) &&
        (!begin || date >= begin) &&
        (!end || date <= end)
      );
    });
  }, [begin, contracts, customers, end, keyword, status]);

  const pageData = usePageData(filtered, page);

  return (
    <>
      <PageHeader title="合同信息查询" breadcrumb={["查询统计", "合同信息查询"]} />
      <Section title="查询条件" description="支持名称模糊搜索、状态筛选和起草时间区间。">
        <div className="mb-6 grid gap-4 lg:grid-cols-[minmax(220px,1fr)_180px_180px_180px]">
          <input className={inputClass} placeholder="合同名称、编号或客户" value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(1); }} />
          <select className={selectClass} value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }}>
            <option value="">全部状态</option>
            {Object.entries(statusText).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
          <input className={inputClass} type="date" value={begin} onChange={(event) => { setBegin(event.target.value); setPage(1); }} />
          <input className={inputClass} type="date" value={end} onChange={(event) => { setEnd(event.target.value); setPage(1); }} />
        </div>
        <ContractTable contracts={pageData.pageItems} customers={customers} users={users} onView={setActive} />
        <Pagination total={filtered.length} page={pageData.safePage} onChange={setPage} />
      </Section>
      {active ? (
        <Modal title="合同详情" onClose={() => setActive(null)} width="max-w-5xl">
          <ContractDetail contract={active} customers={customers} users={users} />
        </Modal>
      ) : null}
    </>
  );
}
