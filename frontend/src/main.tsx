import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./layouts/AppLayout";
import { LoginPage } from "./pages/auth/LoginPage";
import { RegisterPage } from "./pages/auth/RegisterPage";
import { ForbiddenPage } from "./pages/ForbiddenPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { BaseContractPage } from "./pages/base/BaseContractPage";
import { CustomerPage } from "./pages/base/CustomerPage";
import { ApprovePage } from "./pages/contract/ApprovePage";
import { CountersignPage } from "./pages/contract/CountersignPage";
import { DraftPage } from "./pages/contract/DraftPage";
import { FinalizePage } from "./pages/contract/FinalizePage";
import { SignPage } from "./pages/contract/SignPage";
import { DashboardPage } from "./pages/dashboard/DashboardPage";
import { InfoQueryPage } from "./pages/query/InfoQueryPage";
import { ProcessQueryPage } from "./pages/query/ProcessQueryPage";
import { AssignPage } from "./pages/system/AssignPage";
import { LogPage } from "./pages/system/LogPage";
import { PermissionPage } from "./pages/system/PermissionPage";
import { RolePage } from "./pages/system/RolePage";
import { UserPage } from "./pages/system/UserPage";
import { useAppStore } from "./store/appStore";
import type { PermissionKey } from "./types";
import "./index.css";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const session = useAppStore((state) => state.session);
  if (!session) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequirePermission({ permission, children }: { permission: PermissionKey; children: React.ReactNode }) {
  const hasPermission = useAppStore((state) => state.hasPermission(permission));
  if (!hasPermission) return <Navigate to="/403" replace />;
  return <>{children}</>;
}

function SystemRoute({ permission, children }: { permission: PermissionKey; children: React.ReactNode }) {
  return (
    <RequirePermission permission={permission}>
      {children}
    </RequirePermission>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="contract/draft" element={<RequirePermission permission="contract:draft"><DraftPage /></RequirePermission>} />
          <Route path="contract/countersign" element={<RequirePermission permission="contract:countersign"><CountersignPage /></RequirePermission>} />
          <Route path="contract/finalize" element={<RequirePermission permission="contract:finalize"><FinalizePage /></RequirePermission>} />
          <Route path="contract/approve" element={<RequirePermission permission="contract:approve"><ApprovePage /></RequirePermission>} />
          <Route path="contract/sign" element={<RequirePermission permission="contract:sign"><SignPage /></RequirePermission>} />
          <Route path="query/info" element={<RequirePermission permission="query:info"><InfoQueryPage /></RequirePermission>} />
          <Route path="query/process" element={<RequirePermission permission="query:process"><ProcessQueryPage /></RequirePermission>} />
          <Route path="base/contract" element={<RequirePermission permission="base:contract"><BaseContractPage /></RequirePermission>} />
          <Route path="base/customer" element={<RequirePermission permission="base:customer"><CustomerPage /></RequirePermission>} />
          <Route path="system/assign" element={<SystemRoute permission="system:assign"><AssignPage /></SystemRoute>} />
          <Route path="system/user" element={<SystemRoute permission="system:user"><UserPage /></SystemRoute>} />
          <Route path="system/role" element={<SystemRoute permission="system:role"><RolePage /></SystemRoute>} />
          <Route path="system/permission" element={<SystemRoute permission="system:permission"><PermissionPage /></SystemRoute>} />
          <Route path="system/log" element={<SystemRoute permission="system:log"><LogPage /></SystemRoute>} />
          <Route path="403" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);
