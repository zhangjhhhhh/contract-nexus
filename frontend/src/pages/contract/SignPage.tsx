import { useState } from "react";
import { ElectronicSignForm } from "../../components/ElectronicSignForm";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore, userHasPending } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract } from "../../types";

export function SignPage() {
  const [active, setActive] = useState<Contract | null>(null);
  const session = useAppStore((state) => state.session);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const submitSign = useAppStore((state) => state.submitSign);
  const showToast = useUiStore((state) => state.showToast);
  const list = contracts.filter((contract) => contract.status === "signing" && userHasPending(processes, contract.id, "sign", session?.userId));

  return (
    <>
      <PageHeader title="待签订合同" breadcrumb={["合同管理", "待签订合同"]} />
      <Section title="签订列表" description="审批通过后录入签订日期、签订方式与备注。">
        <ContractTable
          contracts={list}
          customers={customers}
          users={users}
          onView={setActive}
          actions={(contract) => <Button variant="secondary" className="min-h-8 px-3" onClick={() => setActive(contract)}>签订</Button>}
        />
      </Section>
      {active ? (
        <Modal title="签订录入" onClose={() => setActive(null)} width="max-w-5xl">
          <div className="grid gap-6">
            <ContractDetail contract={active} customers={customers} users={users} />
            <ElectronicSignForm
              storageKey={`contract-electronic-signature-draft:${active.id}`}
              onSubmit={async (values) => {
                try {
                  await submitSign({ contractId: active.id, ...values });
                  showToast("success", "签订完成，合同状态已更新");
                  setActive(null);
                } catch (error) {
                  showToast("error", error instanceof Error ? error.message : "签订失败");
                }
              }}
            />
          </div>
        </Modal>
      ) : null}
    </>
  );
}
