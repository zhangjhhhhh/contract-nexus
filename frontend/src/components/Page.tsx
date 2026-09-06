import type { ReactNode } from "react";

export function PageHeader({ title, breadcrumb, extra }: { title: string; breadcrumb: string[]; extra?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <div className="mb-2 text-note text-muted">{breadcrumb.join(" / ")}</div>
        <h1 className="m-0 text-title font-semibold text-text">{title}</h1>
      </div>
      {extra ? <div className="flex items-center gap-3">{extra}</div> : null}
    </div>
  );
}

export function Section({ title, description, extra, children }: { title?: string; description?: string; extra?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-card border border-line bg-white">
      {(title || description || extra) && (
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-line px-6 py-4">
          <div>
            {title ? <h2 className="m-0 text-subtitle font-semibold text-text">{title}</h2> : null}
            {description ? <p className="m-0 mt-1 text-note text-muted">{description}</p> : null}
          </div>
          {extra}
        </div>
      )}
      <div className="p-6">{children}</div>
    </section>
  );
}
