import {
  ClipboardCheck,
  ClipboardEdit,
  FileCheck2,
  FileClock,
  FileSearch,
  Files,
  Home,
  ListChecks,
  LogOut,
  ScrollText,
  ShieldCheck,
  Signature,
  SlidersHorizontal,
  UserCog,
  UsersRound
} from "lucide-react";
import dayjs from "dayjs";
import { useEffect, useMemo, useRef, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { ConfirmDialog, ToastHost } from "../components/Feedback";
import { LegalAIChatWidget } from "../components/LegalAIChatWidget";
import { useAppStore, useCurrentUser } from "../store/appStore";
import { useUiStore } from "../store/uiStore";
import type { PermissionKey } from "../types";

const menuGroups: {
  title: string;
  items: { label: string; path: string; permission: PermissionKey; icon: React.ElementType }[];
}[] = [
  {
    title: "工作台",
    items: [{ label: "工作台", path: "/dashboard", permission: "dashboard:view", icon: Home }]
  },
  {
    title: "合同管理",
    items: [
      { label: "起草合同", path: "/contract/draft", permission: "contract:draft", icon: ClipboardEdit },
      { label: "待会签合同", path: "/contract/countersign", permission: "contract:countersign", icon: FileClock },
      { label: "待定稿合同", path: "/contract/finalize", permission: "contract:finalize", icon: Files },
      { label: "待审批合同", path: "/contract/approve", permission: "contract:approve", icon: ClipboardCheck },
      { label: "待签订合同", path: "/contract/sign", permission: "contract:sign", icon: Signature }
    ]
  },
  {
    title: "查询统计",
    items: [
      { label: "合同信息查询", path: "/query/info", permission: "query:info", icon: FileSearch },
      { label: "合同流程查询", path: "/query/process", permission: "query:process", icon: ListChecks }
    ]
  },
  {
    title: "基础数据",
    items: [
      { label: "合同信息管理", path: "/base/contract", permission: "base:contract", icon: ScrollText },
      { label: "客户信息管理", path: "/base/customer", permission: "base:customer", icon: UsersRound }
    ]
  },
  {
    title: "系统管理",
    items: [
      { label: "分配合同", path: "/system/assign", permission: "system:assign", icon: SlidersHorizontal },
      { label: "用户管理", path: "/system/user", permission: "system:user", icon: UserCog },
      { label: "角色管理", path: "/system/role", permission: "system:role", icon: ShieldCheck },
      { label: "权限分配", path: "/system/permission", permission: "system:permission", icon: UsersRound },
      { label: "日志管理", path: "/system/log", permission: "system:log", icon: FileCheck2 }
    ]
  }
];

function formatNow(date: Date) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function AppLayout() {
  const [now, setNow] = useState(() => new Date());
  const [expiryAlerts, setExpiryAlerts] = useState<{ id: string; name: string; daysLeft: number }[]>([]);
  const dismissedRef = useRef<Set<string>>(new Set());
  const navigate = useNavigate();
  const location = useLocation();
  const user = useCurrentUser();
  const roles = useAppStore((state) => state.roles);
  const hasPermission = useAppStore((state) => state.hasPermission);
  const logout = useAppStore((state) => state.logout);
  const session = useAppStore((state) => state.session);
  const syncContractsFromBackend = useAppStore((state) => state.syncContractsFromBackend);
  const syncSystemFromBackend = useAppStore((state) => state.syncSystemFromBackend);
  const showToast = useUiStore((state) => state.showToast);
  const contracts = useAppStore((state) => state.contracts);
  const processes = useAppStore((state) => state.processes);

  // Clock
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  // Check for contracts about to expire (within 1 day) — only for current user
  useEffect(() => {
    if (!session) return;

    const check = () => {
      const today = dayjs().startOf("day");
      const newAlerts: { id: string; name: string; daysLeft: number }[] = [];

      // 当前用户参与的合同ID：自己起草的 + 流程中分配的
      const myContractIds = new Set<string>();
      contracts
        .filter((c) => c.drafterId === session.userId)
        .forEach((c) => myContractIds.add(c.id));
      processes
        .filter((p) => p.userId === session.userId)
        .forEach((p) => myContractIds.add(p.contractId));

      contracts.forEach((c) => {
        if (!c.endTime) return;
        if (c.status === "rejected" || c.status === "drafting") return;

        // 只提醒当前用户相关的合同
        if (!myContractIds.has(c.id)) return;

        const endDate = dayjs(c.endTime).startOf("day");
        const daysLeft = endDate.diff(today, "day");

        if (daysLeft >= 0 && daysLeft <= 1 && !dismissedRef.current.has(c.id)) {
          newAlerts.push({ id: c.id, name: c.name, daysLeft });
        }
      });

      if (newAlerts.length > 0) {
        setExpiryAlerts((prev) => {
          const existingIds = new Set(prev.map((a) => a.id));
          const merged = [...prev];
          newAlerts.forEach((a) => {
            if (!existingIds.has(a.id)) merged.push(a);
          });
          return merged;
        });
      }
    };

    check();
    const timer = window.setInterval(check, 60000);
    return () => window.clearInterval(timer);
  }, [contracts, processes, session]);

  const dismissAlert = (id: string) => {
    dismissedRef.current.add(id);
    setExpiryAlerts((prev) => prev.filter((a) => a.id !== id));
  };

  useEffect(() => {
    if (!session) return;
    syncSystemFromBackend().catch((error: Error) => {
      showToast("error", `System backend connection failed: ${error.message}`);
    });
    syncContractsFromBackend().catch((error: Error) => {
      showToast("error", `合同后端连接失败：${error.message}`);
    });
  }, [session, showToast, syncContractsFromBackend, syncSystemFromBackend]);

  const visibleGroups = useMemo(
    () =>
      menuGroups
        .map((group) => ({ ...group, items: group.items.filter((item) => hasPermission(item.permission)) }))
        .filter((group) => group.items.length > 0),
    [hasPermission]
  );

  const roleText = user?.roleIds.map((id) => roles.find((role) => role.id === id)?.name || id).join("、") || "";
  const isPlainUser = user?.roleIds.includes("new_user") && user.roleIds.length === 1;

  return (
    <div className="min-h-screen bg-white text-text">
      <header className="fixed left-0 right-0 top-0 z-20 flex h-16 items-center justify-between border-b border-line bg-white px-8">
        <div className="flex items-center gap-3">
          <div className="grid h-9 w-9 place-items-center rounded-button bg-primary text-note font-semibold text-white">四海</div>
          <div className="text-subtitle font-semibold text-text">合同管理系统</div>
        </div>
        <div className="flex items-center gap-5 text-body">
          <span className="text-muted">{formatNow(now)}</span>
          <div className="flex items-center gap-3">
            <div className="grid h-8 w-8 place-items-center rounded-full bg-section text-body font-semibold text-primary">
              {user?.name.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <div className="text-body font-medium leading-tight text-text">{user?.name}</div>
              <div className="text-helper text-muted">{roleText || "未授权"}</div>
            </div>
          </div>
          <button
            className="inline-flex items-center gap-1 text-body font-medium text-primary"
            onClick={() => {
              logout();
              navigate("/login", { replace: true });
            }}
          >
            <LogOut className="h-[18px] w-[18px]" />
            注销登录
          </button>
        </div>
      </header>

      <div className="flex pt-16">
        <aside className="fixed bottom-0 left-0 top-16 w-[260px] overflow-y-auto border-r border-line bg-section px-3 py-4 page-scroll">
          {visibleGroups.map((group) => (
            <div className="mb-5" key={group.title}>
              <div className="mb-2 px-3 text-note font-medium text-muted">{group.title}</div>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const active = location.pathname === item.path;
                  return (
                    <NavLink
                      className={[
                        "relative flex h-11 items-center gap-3 rounded-button px-3 text-body font-medium transition-colors",
                        active ? "bg-hover text-primary before:absolute before:left-0 before:top-2 before:h-7 before:w-[3px] before:bg-primary" : "text-text hover:bg-hover"
                      ].join(" ")}
                      key={item.path}
                      to={item.path}
                    >
                      <Icon className="h-5 w-5" />
                      <span>{item.label}</span>
                    </NavLink>
                  );
                })}
              </div>
            </div>
          ))}
          {isPlainUser ? (
            <div className="rounded-card border border-line bg-white p-4 text-body text-muted">
              当前账号正在等待管理员授权，暂不能访问业务菜单。
            </div>
          ) : null}
        </aside>
        <main className="ml-[260px] min-h-[calc(100vh-64px)] flex-1 px-8 py-8">
          <Outlet />
        </main>
      </div>
      <LegalAIChatWidget />
      <ToastHost />
      <ConfirmDialog />
      {expiryAlerts.length > 0 && (
        <div className="fixed bottom-6 right-6 z-50 space-y-3">
          {expiryAlerts.map((alert) => {
            const text =
              alert.daysLeft === 0 ? `合同「${alert.name}」今日到期！` : `合同「${alert.name}」明天到期！`;
            return (
              <div
                key={alert.id}
                className="flex items-center gap-3 rounded-card border border-warning/40 bg-orange-50 px-4 py-3 text-body text-text shadow-lg animate-slide-up"
              >
                <span className="grid h-8 w-8 place-items-center rounded-full bg-warning/20">
                  <FileClock className="h-4 w-4 text-warning" />
                </span>
                <div>
                  <div className="font-medium text-text">{text}</div>
                  <div className="text-note text-muted">请及时处理，避免合同过期</div>
                </div>
                <button
                  className="ml-3 text-note text-muted hover:text-text"
                  onClick={() => dismissAlert(alert.id)}
                >
                  ✕
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
