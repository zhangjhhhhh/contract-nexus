import { ArrowLeftRight, Search, Save, X } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { inputClass } from "../../components/Form";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Contract, User } from "../../types";

type AssignableUser = Pick<User, "id" | "name" | "email">;

function normalizeSearchText(value?: string) {
  return (value ?? "").trim().toLowerCase();
}

function userMatchesKeyword(user: AssignableUser, terms: string[]) {
  if (terms.length === 0) {
    return true;
  }
  const fields = [
    normalizeSearchText(user.name),
    normalizeSearchText(user.id),
    normalizeSearchText(user.email)
  ];
  const compactEmail = normalizeSearchText(user.email).replace(/\s+/g, "");
  return terms.every((term) => {
    const compactTerm = term.replace(/\s+/g, "");
    return fields.some((field) => field.includes(term)) || compactEmail.includes(compactTerm);
  });
}

function UserSelectBox({
  title,
  selected,
  onChange,
  users
}: {
  title: string;
  selected: string[];
  onChange: (value: string[]) => void;
  users: AssignableUser[];
}) {
  const [keyword, setKeyword] = useState("");
  const searchTerms = useMemo(
    () => normalizeSearchText(keyword).split(/\s+/).filter(Boolean),
    [keyword]
  );
  const filteredUsers = useMemo(() => {
    return users.filter((user) => userMatchesKeyword(user, searchTerms));
  }, [searchTerms, users]);

  return (
    <div className="rounded-card border border-line bg-white">
      <div className="border-b border-line bg-section px-4 py-3">
        <div className="flex items-center justify-between gap-3">
          <div className="text-body font-medium text-text">{title}</div>
          <div className="flex items-center gap-2">
            <span className="shrink-0 text-note text-muted">{selected.length}/{users.length}</span>
            <button
              className="inline-flex h-8 items-center gap-1 rounded-button px-2 text-note font-medium text-muted transition hover:bg-hover hover:text-text disabled:cursor-not-allowed disabled:opacity-50"
              disabled={selected.length === 0}
              type="button"
              onClick={() => onChange([])}
            >
              <X className="h-4 w-4" />
              清空
            </button>
          </div>
        </div>
        <label className="mt-3 flex items-center gap-2">
          <Search className="h-4 w-4 shrink-0 text-muted" />
          <input
            className={`${inputClass} h-9`}
            placeholder="搜索姓名、账号或邮箱"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </label>
      </div>
      <div className="grid max-h-[420px] min-h-[420px] content-start gap-2 overflow-y-auto p-4 page-scroll">
        {filteredUsers.length > 0 ? (
          filteredUsers.map((user) => (
            <label className="flex min-h-11 items-center gap-3 rounded-button px-2 text-body hover:bg-hover" key={user.id}>
              <input
                className="h-4 w-4 shrink-0"
                type="checkbox"
                checked={selected.includes(user.id)}
                onChange={(event) => {
                  onChange(event.target.checked ? [...selected, user.id] : selected.filter((id) => id !== user.id));
                }}
              />
              <span className="min-w-0">
                <span className="block truncate text-body font-medium text-text">{user.name}</span>
                <span className="block truncate text-helper text-muted">{user.id}{user.email ? ` · ${user.email}` : ""}</span>
              </span>
            </label>
          ))
        ) : (
          <div className="rounded-card bg-section px-3 py-3 text-body text-muted">没有匹配的人员</div>
        )}
      </div>
    </div>
  );
}

export function AssignPage() {
  const [active, setActive] = useState<Contract | null>(null);
  const [countersign, setCountersign] = useState<string[]>([]);
  const [approve, setApprove] = useState<string[]>([]);
  const [sign, setSign] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const roles = useAppStore((state) => state.roles);
  const assignContract = useAppStore((state) => state.assignContract);
  const showToast = useUiStore((state) => state.showToast);

  const operators = useMemo(() => {
    return users
      .filter((user) =>
        user.roleIds.some((roleId) => roles.find((role) => role.id === roleId)?.permissions.some((permission) => permission.startsWith("contract:")))
      )
      .sort((left, right) => left.name.localeCompare(right.name, "zh-Hans-CN", { sensitivity: "base", numeric: true }));
  }, [roles, users]);
  const list = contracts.filter((contract) => contract.status === "drafting");

  const open = (contract: Contract) => {
    setActive(contract);
    setCountersign([]);
    setApprove([]);
    setSign([]);
  };

  return (
    <>
      <PageHeader title="分配合同" breadcrumb={["系统管理", "分配合同"]} />
      <Section title="待分配合同" description="状态为起草的合同需要管理员指定流转人员。">
        <ContractTable
          contracts={list}
          customers={customers}
          users={users}
          onView={open}
          actions={(contract) => <Button variant="secondary" className="min-h-8 px-3" onClick={() => open(contract)}>分配</Button>}
        />
      </Section>
      {active ? (
        <Modal title="分配合同流转人员" onClose={() => setActive(null)} width="max-w-6xl">
          <div className="grid gap-6">
            <ContractDetail contract={active} customers={customers} users={users} />
            <div className="grid gap-4 lg:grid-cols-[1fr_56px_1fr_56px_1fr]">
              <UserSelectBox title="会签人" selected={countersign} onChange={setCountersign} users={operators} />
              <div className="hidden place-items-center lg:grid"><ArrowLeftRight className="h-6 w-6 text-muted" /></div>
              <UserSelectBox title="审批人" selected={approve} onChange={setApprove} users={operators} />
              <div className="hidden place-items-center lg:grid"><ArrowLeftRight className="h-6 w-6 text-muted" /></div>
              <UserSelectBox title="签订人" selected={sign} onChange={setSign} users={operators} />
            </div>
            <div className="flex justify-end">
              <Button
                loading={loading}
                icon={<Save className="h-[18px] w-[18px]" />}
                onClick={() => {
                  if (!countersign.length || !approve.length || !sign.length) {
                    showToast("error", "会签人、审批人、签订人均需至少选择一人");
                    return;
                  }
                  setLoading(true);
                  window.setTimeout(async () => {
                    try {
                      await assignContract({ contractId: active.id, countersignUserIds: countersign, approveUserIds: approve, signUserIds: sign });
                      showToast("success", "合同流转人员已分配");
                      setActive(null);
                    } catch (error) {
                      showToast("error", error instanceof Error ? error.message : "分配失败");
                    } finally {
                      setLoading(false);
                    }
                  }, 700);
                }}
              >
                保存分配
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </>
  );
}
