import dayjs from "dayjs";
import { create } from "zustand";
import { persist } from "zustand/middleware";
import * as authApi from "../api/auth";
import * as contractApi from "../api/contracts";
import * as systemApi from "../api/system";
import {
  initialContracts,
  initialCustomers,
  initialLogs,
  initialProcesses,
  initialRoles,
  initialSignRecords,
  initialUsers
} from "../mock/data";
import type {
  Contract,
  ContractProcess,
  Customer,
  Log,
  PermissionKey,
  ProcessType,
  Role,
  Session,
  SignRecord,
  User
} from "../types";
import { allProcessesDone, getPendingProcess } from "../utils/flow";
import { makeId } from "../utils/format";
import { useUiStore } from "./uiStore";

type DraftPayload = Omit<Contract, "id" | "num" | "drafterId" | "createdAt" | "status">;
type AssignPayload = { contractId: string; countersignUserIds: string[]; approveUserIds: string[]; signUserIds: string[] };
type ProcessPayload = { contractId: string; type: ProcessType; content: string; result?: "approved" | "rejected" };
type SignPayload = { contractId: string; signDate: string; method: string; remark?: string; signerName?: string; signatureDataUrl?: string };

type AppState = {
  session: Session | null;
  permissions: PermissionKey[];
  users: User[];
  roles: Role[];
  customers: Customer[];
  contracts: Contract[];
  processes: ContractProcess[];
  signRecords: SignRecord[];
  logs: Log[];
  contractsLoaded: boolean;
  syncSystemFromBackend: () => Promise<void>;
  syncContractsFromBackend: () => Promise<void>;
  login: (name: string, password: string) => Promise<{ ok: boolean; message?: string; redirectTo?: string }>;
  register: (name: string, password: string, email: string, verificationCode: string) => Promise<{ ok: boolean; message?: string }>;
  logout: () => void;
  clearAll: () => void;
  hasPermission: (permission: PermissionKey) => boolean;
  addLog: (content: string) => void;
  draftContract: (payload: DraftPayload) => Promise<void>;
  assignContract: (payload: AssignPayload) => Promise<void>;
  submitProcess: (payload: ProcessPayload) => Promise<void>;
  submitFinalize: (contractId: string, content: string, attachments?: { name: string; type: string; path: string }[]) => Promise<void>;
  submitSign: (payload: SignPayload) => Promise<void>;
  upsertCustomer: (payload: Customer) => Promise<void>;
  deleteCustomer: (id: string) => Promise<{ ok: boolean; message?: string }>;
  upsertContract: (payload: Contract) => Promise<void>;
  deleteContract: (id: string) => Promise<void>;
  retractContract: (contractId: string) => Promise<void>;
  upsertUser: (payload: User) => Promise<void>;
  deleteUser: (id: string) => Promise<{ ok: boolean; message?: string }>;
  upsertRole: (payload: Role) => Promise<void>;
  deleteRole: (id: string) => Promise<{ ok: boolean; message?: string }>;
  setUserRoles: (userId: string, roleIds: string[]) => Promise<void>;
};

const freshState = () => ({
  session: null,
  permissions: [],
  users: initialUsers,
  roles: initialRoles,
  customers: initialCustomers,
  contracts: initialContracts,
  processes: initialProcesses,
  signRecords: initialSignRecords,
  logs: initialLogs
  ,
  contractsLoaded: false
});

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      ...freshState(),
      syncSystemFromBackend: async () => {
        const [users, roles, logs, customers] = await Promise.all([
          systemApi.fetchUsers(),
          systemApi.fetchRoles(),
          systemApi.fetchLogs(),
          systemApi.fetchCustomers()
        ]);
        set({ users, roles, logs, customers });
      },
      syncContractsFromBackend: async () => {
        const contracts = await contractApi.fetchContracts();
        const [processes, signRecords] = await Promise.all([
          contractApi.fetchAllContractProcesses(contracts),
          contractApi.fetchAllContractSignRecords(contracts)
        ]);
        set({ contracts, processes, signRecords, contractsLoaded: true });
      },
      login: async (name, password) => {
        try {
          const result = await authApi.login({ name, password });
          const [users, roles, logs, customers] = await Promise.all([
            systemApi.fetchUsers(),
            systemApi.fetchRoles(),
            systemApi.fetchLogs(),
            systemApi.fetchCustomers()
          ]);
          set({
            session: { userId: result.user.id, userName: result.user.name },
            permissions: result.permissions,
            users,
            roles,
            logs,
            customers
          });
          return { ok: true, redirectTo: result.redirectTo };
        } catch (error) {
          return { ok: false, message: error instanceof Error ? error.message : "用户名或密码不正确" };
        }
      },
      register: async (name, password, email, verificationCode) => {
        if (get().users.some((item) => item.name === name)) {
          return { ok: false, message: "用户名已存在" };
        }
        try {
          await authApi.register({ name, password, email, verificationCode });
          const [users, logs, customers] = await Promise.all([
            systemApi.fetchUsers(),
            systemApi.fetchLogs(),
            systemApi.fetchCustomers()
          ]);
          set({ users, logs, customers });
          return { ok: true };
        } catch (error) {
          return { ok: false, message: error instanceof Error ? error.message : "注册失败" };
        }
      },
      logout: () => {
        get().addLog("注销登录");
        set({ session: null, permissions: [], contractsLoaded: false });
      },
      clearAll: () => {
        set(freshState());
        localStorage.removeItem("contract-management-system");
      },
      hasPermission: (permission) => {
        const { session, users, roles, permissions } = get();
        if (session && permissions.includes(permission)) return true;
        const user = users.find((item) => item.id === session?.userId);
        if (!user) return false;
        return user.roleIds.some((roleId) => roles.find((role) => role.id === roleId)?.permissions.includes(permission));
      },
      addLog: (content) => {
        const { session } = get();
        const log: Log = {
          id: makeId("L", get().logs.length),
          userName: session?.userName || "system",
          content,
          time: dayjs().format("YYYY-MM-DD HH:mm")
        };
        set((state) => ({ logs: [log, ...state.logs] }));
      },
      draftContract: async (payload) => {
        const session = get().session;
        if (!session) return;
        const contract = await contractApi.draftContract({
          ...payload,
          drafterId: session.userId
        });
        set((state) => ({ contracts: [contract, ...state.contracts] }));
        get().addLog(`起草合同：${contract.name}`);
      },
      assignContract: async ({ contractId, countersignUserIds, approveUserIds, signUserIds }) => {
        const contract = await contractApi.assignContract(contractId, { countersignUserIds, approveUserIds, signUserIds });
        const contractProcesses = await contractApi.fetchContractProcesses(contractId);
        set((state) => ({
          processes: [...state.processes.filter((process) => process.contractId !== contractId), ...contractProcesses],
          contracts: state.contracts.map((item) => (item.id === contractId ? contract : item))
        }));
        get().addLog(`分配合同流程：${get().contracts.find((contract) => contract.id === contractId)?.name || contractId}`);
      },
      submitProcess: async ({ contractId, type, content, result }) => {
        const userId = get().session?.userId;
        if (!userId) return;
        const contract =
          type === "approve"
            ? await contractApi.approveContract(contractId, { userId, result: result || "approved", content })
            : await contractApi.submitCountersign(contractId, { userId, content });
        const contractProcesses = await contractApi.fetchContractProcesses(contractId);
        set((state) => ({
          processes: [...state.processes.filter((process) => process.contractId !== contractId), ...contractProcesses],
          contracts: state.contracts.map((item) => (item.id === contractId ? contract : item))
        }));
        get().addLog(`${type === "countersign" ? "提交会签" : "提交审批"}：${get().contracts.find((contract) => contract.id === contractId)?.name || contractId}`);
      },
      submitFinalize: async (contractId, content, attachments?) => {
        const userId = get().session?.userId;
        if (!userId) return;
        const contract = await contractApi.finalizeContract(contractId, { userId, content, attachments });
        const contractProcesses = await contractApi.fetchContractProcesses(contractId);
        set((state) => ({
          processes: [...state.processes.filter((process) => process.contractId !== contractId), ...contractProcesses],
          contracts: state.contracts.map((item) => (item.id === contractId ? contract : item))
        }));
        get().addLog(`定稿合同：${get().contracts.find((contract) => contract.id === contractId)?.name || contractId}`);
      },
      submitSign: async ({ contractId, signDate, method, remark, signerName, signatureDataUrl }) => {
        const userId = get().session?.userId;
        if (!userId) return;
        const contract = await contractApi.signContract(contractId, { userId, signDate, method, remark, signerName, signatureDataUrl });
        const contractProcesses = await contractApi.fetchContractProcesses(contractId);
        const record: SignRecord = { id: makeId("S", get().signRecords.length), contractId, signDate, method, remark, signerName, signatureDataUrl };
        set((state) => ({
          signRecords: [record, ...state.signRecords],
          processes: [...state.processes.filter((process) => process.contractId !== contractId), ...contractProcesses],
          contracts: state.contracts.map((item) => (item.id === contractId ? contract : item))
        }));
        get().addLog(`录入签订信息：${get().contracts.find((contract) => contract.id === contractId)?.name || contractId}`);
      },
      upsertCustomer: async (payload) => {
        const existing = get().customers.find((item) => item.id === payload.id);
        const saved = existing
          ? await systemApi.updateCustomer(payload.id, {
              name: payload.name, tel: payload.tel, address: payload.address,
              fax: payload.fax, email: payload.email, bank: payload.bank,
              account: payload.account, remark: payload.remark
            })
          : await systemApi.createCustomer({
              name: payload.name, tel: payload.tel, address: payload.address,
              fax: payload.fax, email: payload.email, bank: payload.bank,
              account: payload.account, remark: payload.remark
            });
        set((state) => ({
          customers: state.customers.some((item) => item.id === saved.id)
            ? state.customers.map((item) => (item.id === saved.id ? saved : item))
            : [saved, ...state.customers]
        }));
        get().addLog(`维护客户资料：${payload.name}`);
      },
      deleteCustomer: async (id) => {
        if (get().contracts.some((contract) => contract.customerId === id)) {
          return { ok: false, message: "该客户已有合同，不能删除" };
        }
        try {
          await systemApi.deleteCustomer(id);
          set((state) => ({ customers: state.customers.filter((item) => item.id !== id) }));
          get().addLog(`删除客户：${id}`);
          return { ok: true };
        } catch (error) {
          return { ok: false, message: error instanceof Error ? error.message : "删除失败" };
        }
      },
      upsertContract: async (payload) => {
        const existing = get().contracts.find((item) => item.id === payload.id);
        if (existing) {
          const saved = await contractApi.updateContract(payload.id, {
            name: payload.name, customerId: payload.customerId,
            beginTime: payload.beginTime, endTime: payload.endTime,
            content: payload.content, drafterId: payload.drafterId
          });
          set((state) => ({
            contracts: state.contracts.map((item) => (item.id === saved.id ? saved : item))
          }));
        } else {
          const session = get().session;
          if (!session) return;
          const contract = await contractApi.draftContract({
            ...payload,
            drafterId: session.userId
          });
          set((state) => ({ contracts: [contract, ...state.contracts] }));
        }
        get().addLog(`维护合同信息：${payload.name}`);
      },
      deleteContract: async (id) => {
        try {
          await contractApi.deleteContract(id);
          set((state) => ({
            contracts: state.contracts.filter((item) => item.id !== id),
            processes: state.processes.filter((item) => item.contractId !== id),
            signRecords: state.signRecords.filter((item) => item.contractId !== id)
          }));
          get().addLog(`删除合同：${id}`);
        } catch (error) {
          const uiStore = useUiStore.getState();
          uiStore.showToast("error", error instanceof Error ? error.message : "删除失败");
        }
      },
      retractContract: async (contractId) => {
        const session = get().session;
        if (!session) return;
        await contractApi.retractContract(contractId, session.userId);
        // 合同撤回后仍为 drafting 状态，无需更新本地状态
        // 仅记录日志
        get().addLog(`撤回起草合同：${get().contracts.find((c) => c.id === contractId)?.name || contractId}`);
      },
      upsertUser: async (payload) => {
        const existing = get().users.find((item) => item.id === payload.id);
        const saved = existing
          ? await systemApi.updateUser(payload)
          : await systemApi.createUser({ name: payload.name, password: payload.password, email: payload.email, roleIds: payload.roleIds });
        const logs = await systemApi.fetchLogs();
        set((state) => ({
          users: state.users.some((item) => item.id === saved.id)
            ? state.users.map((item) => (item.id === saved.id ? saved : item))
            : [saved, ...state.users],
          logs
        }));
      },
      deleteUser: async (id) => {
        if (get().session?.userId === id) return { ok: false, message: "不能删除当前登录用户" };
        if (get().processes.some((item) => item.userId === id && item.state === "pending")) {
          return { ok: false, message: "该用户仍有待办流程，不能删除" };
        }
        try {
          await systemApi.deleteUser(id);
          const logs = await systemApi.fetchLogs();
          set((state) => ({ users: state.users.filter((item) => item.id !== id), logs }));
          return { ok: true };
        } catch (error) {
          return { ok: false, message: error instanceof Error ? error.message : "删除失败" };
        }
      },
      upsertRole: async (payload) => {
        const existing = get().roles.find((item) => item.id === payload.id);
        const saved = existing
          ? await systemApi.updateRole(payload)
          : await systemApi.createRole({ name: payload.name, description: payload.description, permissions: payload.permissions });
        const logs = await systemApi.fetchLogs();
        set((state) => ({
          roles: state.roles.some((item) => item.id === saved.id)
            ? state.roles.map((item) => (item.id === saved.id ? saved : item))
            : [saved, ...state.roles],
          logs
        }));
      },
      deleteRole: async (id) => {
        if (["admin", "operator", "new_user"].includes(id)) return { ok: false, message: "内置角色不能删除" };
        if (get().users.some((user) => user.roleIds.includes(id))) return { ok: false, message: "该角色已分配给用户，不能删除" };
        try {
          await systemApi.deleteRole(id);
          const logs = await systemApi.fetchLogs();
          set((state) => ({ roles: state.roles.filter((item) => item.id !== id), logs }));
          return { ok: true };
        } catch (error) {
          return { ok: false, message: error instanceof Error ? error.message : "删除失败" };
        }
      },
      setUserRoles: async (userId, roleIds) => {
        const saved = await systemApi.setUserRoles(userId, roleIds);
        const logs = await systemApi.fetchLogs();
        set((state) => ({ users: state.users.map((user) => (user.id === userId ? saved : user)), logs }));
      }
    }),
    {
      name: "contract-management-system",
      partialize: (state) => ({
        session: state.session,
        permissions: state.permissions,
        users: state.users,
        roles: state.roles,
        customers: state.customers,
        contracts: state.contracts,
        processes: state.processes,
        signRecords: state.signRecords,
        logs: state.logs
      })
    }
  )
);

export function useCurrentUser() {
  return useAppStore((state) => state.users.find((user) => user.id === state.session?.userId) || null);
}

export function useCan(permission: PermissionKey) {
  return useAppStore((state) => state.hasPermission(permission));
}

export function userHasPending(processes: ContractProcess[], contractId: string, type: ProcessType, userId?: string) {
  return userId ? Boolean(getPendingProcess(processes, contractId, type, userId)) : false;
}
