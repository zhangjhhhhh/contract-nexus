import { Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractForm } from "../../components/ContractForm";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract } from "../../types";

export function BaseContractPage() {
  const [viewing, setViewing] = useState<Contract | null>(null);
  const [editing, setEditing] = useState<Contract | null>(null);
  const [creating, setCreating] = useState(false);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const upsertContract = useAppStore((state) => state.upsertContract);
  const deleteContract = useAppStore((state) => state.deleteContract);
  const session = useAppStore((state) => state.session);
  const showToast = useUiStore((state) => state.showToast);
  const openConfirm = useUiStore((state) => state.openConfirm);

  return (
    <>
      <PageHeader
        title="合同信息管理"
        breadcrumb={["基础数据", "合同信息管理"]}
        extra={<Button icon={<Plus className="h-[18px] w-[18px]" />} onClick={() => setCreating(true)}>新增合同</Button>}
      />
      <Section title="合同台账" description="管理员与操作员可维护合同基础信息。">
        <ContractTable
          contracts={contracts}
          customers={customers}
          users={users}
          onView={setViewing}
          onEdit={setEditing}
          onDelete={(contract) => {
            openConfirm({
              title: "删除合同",
              message: `确认删除合同「${contract.name}」？该操作不可撤销。`,
              onConfirm: async () => {
                try {
                  await deleteContract(contract.id);
                  showToast("success", "合同已删除");
                } catch (error) {
                  showToast("error", error instanceof Error ? error.message : "删除失败");
                }
              }
            });
          }}
        />
      </Section>
      {viewing ? (
        <Modal title="合同详情" onClose={() => setViewing(null)} width="max-w-5xl">
          <ContractDetail contract={viewing} customers={customers} users={users} />
        </Modal>
      ) : null}
      {editing ? (
        <Modal title="编辑合同" onClose={() => setEditing(null)} width="max-w-5xl">
          <ContractForm
            customers={customers}
            initial={editing}
            requireAttachment={false}
            submitText="保存合同"
            onSubmit={async (values) => {
              try {
                await upsertContract({ ...editing, ...values });
                showToast("success", "合同信息已保存");
                setEditing(null);
              } catch (error) {
                showToast("error", error instanceof Error ? error.message : "保存失败");
              }
            }}
          />
        </Modal>
      ) : null}
      {creating ? (
        <Modal title="新增合同" onClose={() => setCreating(false)} width="max-w-5xl">
          <ContractForm
            customers={customers}
            submitText="保存合同"
            onSubmit={async (values) => {
              try {
                await upsertContract({
                  id: "",
                  num: "",
                  ...values,
                  drafterId: session?.userId || users[0]?.id || "",
                  createdAt: new Date().toISOString().slice(0, 16).replace("T", " "),
                  status: "drafting"
                });
                showToast("success", "合同已新增");
                setCreating(false);
              } catch (error) {
                showToast("error", error instanceof Error ? error.message : "新增失败");
              }
            }}
          />
        </Modal>
      ) : null}
    </>
  );
}
