import { useState } from "react";
import { ApprovalForm } from "../../components/ActionForms";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore, userHasPending } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract } from "../../types";

export function ApprovePage() {
  const [active, setActive] = useState<Contract | null>(null);
  const session = useAppStore((state) => state.session);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const submitProcess = useAppStore((state) => state.submitProcess);
  const showToast = useUiStore((state) => state.showToast);

  const list = contracts.filter(
    (contract) => contract.status === "approving" && userHasPending(processes, contract.id, "approve", session?.userId)
  );
  return (
    <>
      <PageHeader title="待审批合同" breadcrumb={["合同管理", "待审批合同"]} />
      <Section title="审批列表" description="拒绝后合同将退回定稿人修改，修改定稿后再次提交审批。">
        <ContractTable
          contracts={list}
          customers={customers}
          users={users}
          onView={setActive}
          actions={(contract) => <Button variant="secondary" className="min-h-8 px-3" onClick={() => setActive(contract)}>审批</Button>}
        />
      </Section>
      {active ? (
        <Modal title="合同审批" onClose={() => setActive(null)} width="max-w-5xl">
          <div className="grid gap-6">
            <ContractDetail contract={active} customers={customers} users={users} />
            <ApprovalForm
              onSubmit={async (values) => {
                try {
                  await submitProcess({ contractId: active.id, type: "approve", content: values.content, result: values.result });
                  showToast("success", values.result === "approved" ? "审批通过，合同进入签订" : "审批已退回，等待定稿人修改");
                  setActive(null);
                } catch (error) {
                  showToast("error", error instanceof Error ? error.message : "审批失败");
                }
              }}
            />
          </div>
        </Modal>
      ) : null}
    </>
  );
}
