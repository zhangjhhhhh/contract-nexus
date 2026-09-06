import { useMemo, useState } from "react";
import { ContractDetail } from "../../components/ContractDetail";
import { selectClass } from "../../components/Form";
import { PageHeader, Section } from "../../components/Page";
import { ProcessSteps } from "../../components/ProcessSteps";
import { useAppStore } from "../../store/appStore";

export function ProcessQueryPage() {
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const signRecords = useAppStore((state) => state.signRecords);
  const [contractId, setContractId] = useState(contracts[0]?.id || "");
  const active = useMemo(() => contracts.find((contract) => contract.id === contractId) || contracts[0], [contractId, contracts]);
  const activeProcesses = active ? processes.filter((process) => process.contractId === active.id) : [];

  return (
    <>
      <PageHeader title="合同流程查询" breadcrumb={["查询统计", "合同流程查询"]} />
      <Section
        title="流程进度"
        description="横向步骤展示完整流转，下面列出每步操作人、时间与意见。"
        extra={
          <select className={`${selectClass} min-w-[320px]`} value={active?.id || ""} onChange={(event) => setContractId(event.target.value)}>
            {contracts.map((contract) => <option key={contract.id} value={contract.id}>{contract.num} - {contract.name}</option>)}
          </select>
        }
      >
        {active ? (
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_420px]">
            <ProcessSteps contract={active} processes={activeProcesses} signRecords={signRecords} users={users} />
            <ContractDetail contract={active} customers={customers} users={users} />
          </div>
        ) : null}
      </Section>
    </>
  );
}
