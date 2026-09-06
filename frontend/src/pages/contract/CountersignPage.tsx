import { useState } from "react";
import { OpinionForm } from "../../components/ActionForms";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore, userHasPending } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract } from "../../types";

export function CountersignPage() {
  const [active, setActive] = useState<Contract | null>(null);
  const session = useAppStore((state) => state.session);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const submitProcess = useAppStore((state) => state.submitProcess);
  const showToast = useUiStore((state) => state.showToast);

  const list = contracts.filter((contract) => contract.status === "countersigning" && userHasPending(processes, contract.id, "countersign", session?.userId));

  return (
    <>
      <PageHeader title="待会签合同" breadcrumb={["合同管理", "待会签合同"]} />
      <Section title="会签列表" description="提交会签意见后，合同将从当前列表移除。">
        <ContractTable
          contracts={list}
          customers={customers}
          users={users}
          onView={setActive}
          actions={(contract) => <Button variant="secondary" className="min-h-8 px-3" onClick={() => setActive(contract)}>会签</Button>}
        />
      </Section>
      {active ? (
        <Modal title="合同会签" onClose={() => setActive(null)} width="max-w-5xl">
          <div className="grid gap-6">
            <ContractDetail contract={active} customers={customers} users={users} />
            <OpinionForm
              onSubmit={async (content) => {
                try {
                  await submitProcess({ contractId: active.id, type: "countersign", content });
                  showToast("success", "会签意见已提交");
                  setActive(null);
                } catch (error) {
                  showToast("error", error instanceof Error ? error.message : "会签失败");
                }
              }}
            />
          </div>
        </Modal>
      ) : null}
    </>
  );
}
