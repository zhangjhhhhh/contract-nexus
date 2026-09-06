import { FileCheck2, FileClock, PenLine, Signature, Undo2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { ContractForm } from "../../components/ContractForm";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { StatusPieChart } from "../../components/StatusPieChart";
import { TimelineChart } from "../../components/TimelineChart";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract } from "../../types";

export function DashboardPage() {
  const [retracting, setRetracting] = useState<Contract | null>(null);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const session = useAppStore((state) => state.session);
  const hasDashboard = useAppStore((state) => state.hasPermission("dashboard:view"));
  const upsertContract = useAppStore((state) => state.upsertContract);
  const deleteContract = useAppStore((state) => state.deleteContract);
  const showToast = useUiStore((state) => state.showToast);
  const openConfirm = useUiStore((state) => state.openConfirm);

  if (!hasDashboard) {
    return (
      <>
        <PageHeader title="等待授权" breadcrumb={["工作台"]} />
        <Section title="账号待授权" description="当前账号已注册成功，请等待管理员分配角色后再使用业务功能。">
          <div className="rounded-card border border-line bg-section p-6 text-body text-muted">
            系统不会向未授权账号展示合同、客户、流程或日志数据。
          </div>
        </Section>
      </>
    );
  }

  // 当前用户相关的合同：自己起草的 + 流程中分配给自己的
  const myContractIds = useMemo(() => {
    const ids = new Set<string>();
    // 自己起草的
    contracts.filter((c) => c.drafterId === session?.userId).forEach((c) => ids.add(c.id));
    // 流程中分配给自己的
    processes
      .filter((p) => p.userId === session?.userId)
      .forEach((p) => ids.add(p.contractId));
    return ids;
  }, [contracts, processes, session?.userId]);

  const myContracts = useMemo(
    () => contracts.filter((c) => myContractIds.has(c.id)),
    [contracts, myContractIds],
  );

  const cards = [
    {
      label: "待分配",
      value: contracts.filter(
        (item) => item.status === "drafting" && item.drafterId === session?.userId,
      ).length,
      icon: Undo2,
    },
    {
      label: "待会签",
      value: processes.filter(
        (p) => p.userId === session?.userId && p.type === "countersign" && p.state === "pending",
      ).length,
      icon: FileClock,
    },
    {
      label: "待审批",
      value: processes.filter(
        (p) => p.userId === session?.userId && p.type === "approve" && p.state === "pending",
      ).length,
      icon: FileCheck2,
    },
    {
      label: "待签订",
      value: processes.filter(
        (p) => p.userId === session?.userId && p.type === "sign" && p.state === "pending",
      ).length,
      icon: Signature,
    },
    {
      label: "我起草的合同总数",
      value: contracts.filter((item) => item.drafterId === session?.userId).length,
      icon: PenLine,
    },
  ];

  const myTodo = useMemo(() => {
    const ids = new Set(
      processes
        .filter((process) => process.userId === session?.userId && process.state === "pending")
        .map((process) => process.contractId)
    );
    return contracts.filter(
      (contract) =>
        ids.has(contract.id) ||
        (contract.status === "finalizing" && contract.drafterId === session?.userId) ||
        (contract.status === "drafting" && contract.drafterId === session?.userId),
    );
  }, [contracts, processes, session?.userId]);

  return (
    <>
      <PageHeader title="工作台" breadcrumb={["工作台"]} />
      <div className="grid gap-4 md:grid-cols-5">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <div className="rounded-card border border-line bg-white p-6 shadow-subtle" key={card.label}>
              <div className="flex items-start justify-between gap-4">
                <div className="border-l-[3px] border-primary pl-4">
                  <div className="text-metric font-semibold text-text">{card.value}</div>
                  <div className="mt-1 text-note text-muted">{card.label}</div>
                </div>
                <Icon className="h-6 w-6 text-primary" />
              </div>
            </div>
          );
        })}
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_420px]">
        <Section title="我的待办列表" description="显示当前账号需要处理的合同。">
          <ContractTable
            contracts={myTodo.slice(0, 8)}
            customers={customers}
            users={users}
            actions={(contract) =>
              contract.status === "drafting" ? (
                <div className="flex items-center gap-2">
                  <Button variant="secondary" className="min-h-8 px-3" onClick={() => setRetracting(contract)} icon={<Undo2 className="h-[18px] w-[18px]" />}>
                    撤回
                  </Button>
                  <Button
                    variant="text"
                    className="min-h-8 px-2 text-error"
                    onClick={() => {
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
                  >
                    删除
                  </Button>
                </div>
              ) : null
            }
          />
        </Section>
        <Section title="合同状态占比" description="我名下合同（起草或参与）的流程状态分布。">
          <StatusPieChart contracts={myContracts} />
        </Section>
      </div>

      <div className="mt-6">
        <Section title="合同时间规划" description="我名下合同的开始与结束时间分布。">
          <TimelineChart contracts={myContracts} />
        </Section>
      </div>
      {retracting ? (
        <Modal title="撤回合同-重新编辑" onClose={() => setRetracting(null)} width="max-w-5xl">
          <ContractForm
            customers={customers}
            initial={retracting}
            requireAttachment={false}
            submitText="重新提交"
            onSubmit={async (values) => {
              try {
                await upsertContract({ ...retracting, ...values });
                showToast("success", "合同已更新，等待管理员分配");
                setRetracting(null);
              } catch (error) {
                showToast("error", error instanceof Error ? error.message : "更新失败");
              }
            }}
          />
        </Modal>
      ) : null}
    </>
  );
}
