import type { ReactNode } from "react";
import { ToastHost } from "../../components/Feedback";
import authHeroUrl from "../../assets/auth-hero.png";

export function AuthShell({ children, title, description }: { children: ReactNode; title: string; description: string }) {
  return (
    <main className="grid min-h-screen grid-cols-[minmax(0,1fr)_480px] bg-white max-lg:grid-cols-1">
      <section
        className="relative isolate flex min-h-screen flex-col justify-between overflow-hidden bg-primary px-12 py-12 text-white max-lg:hidden"
        style={{ backgroundImage: `url(${authHeroUrl})`, backgroundSize: "cover", backgroundPosition: "center" }}
      >
        <div className="absolute inset-0 -z-10 bg-primary/72" />
        <div className="absolute inset-0 -z-10 bg-[linear-gradient(135deg,rgba(30,58,95,0.9),rgba(44,74,110,0.58)_52%,rgba(30,58,95,0.78))]" />
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-button border border-white bg-primary text-note font-semibold text-white">四海</div>
          <div className="text-subtitle font-semibold">合同管理系统</div>
        </div>
        <div className="max-w-2xl">
          <h1 className="m-0 text-[40px] font-semibold leading-[1.25] tracking-normal">合同全生命周期管理</h1>
          <p className="m-0 mt-5 text-subtitle text-white">
            覆盖起草、分配、会签、定稿、审批、签订流程，面向企业法务与管理团队的内部协同系统。
          </p>
        </div>
        <p className="m-0 text-note text-white">测试账号：admin_test / admin123，operator / operator123</p>
      </section>
      <section className="flex min-h-screen items-center justify-center bg-white px-8 py-12">
        <div className="w-full max-w-[360px]">
          <h2 className="m-0 text-title font-semibold text-text">{title}</h2>
          <p className="m-0 mb-8 mt-2 text-body text-muted">{description}</p>
          {children}
        </div>
      </section>
      <ToastHost />
    </main>
  );
}
