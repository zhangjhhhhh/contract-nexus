import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass, textareaClass } from "../../components/Form";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Customer } from "../../types";

const schema = z.object({
  name: z.string().min(1, "请输入客户名称"),
  tel: z.string().min(1, "请输入电话"),
  address: z.string().min(1, "请输入地址"),
  fax: z.string().optional(),
  email: z.string().optional(),
  bank: z.string().optional(),
  account: z.string().optional(),
  remark: z.string().optional()
});

function CustomerForm({ initial, onSubmit }: { initial?: Customer; onSubmit: (customer: Customer) => void }) {
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    mode: "onBlur",
    defaultValues: initial || {}
  });

  return (
    <form
      className="grid gap-6"
      onSubmit={handleSubmit((values) => {
        setLoading(true);
        window.setTimeout(() => {
          onSubmit({ id: initial?.id || "", ...values });
          setLoading(false);
        }, 650);
      })}
    >
      <div className="grid gap-6 md:grid-cols-2">
        <Field label="客户名称" error={errors.name?.message} required><input className={inputClass} {...register("name")} /></Field>
        <Field label="电话" error={errors.tel?.message} required><input className={inputClass} {...register("tel")} /></Field>
        <Field label="地址" error={errors.address?.message} required><input className={inputClass} {...register("address")} /></Field>
        <Field label="传真"><input className={inputClass} {...register("fax")} /></Field>
        <Field label="邮箱"><input className={inputClass} {...register("email")} /></Field>
        <Field label="银行名称"><input className={inputClass} {...register("bank")} /></Field>
        <Field label="银行账号"><input className={inputClass} {...register("account")} /></Field>
      </div>
      <Field label="备注"><textarea className={textareaClass} {...register("remark")} /></Field>
      <div className="flex justify-end"><Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">保存客户</Button></div>
    </form>
  );
}

export function CustomerPage() {
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<Customer | null>(null);
  const [creating, setCreating] = useState(false);
  const customers = useAppStore((state) => state.customers);
  const upsertCustomer = useAppStore((state) => state.upsertCustomer);
  const deleteCustomer = useAppStore((state) => state.deleteCustomer);
  const showToast = useUiStore((state) => state.showToast);
  const openConfirm = useUiStore((state) => state.openConfirm);
  const list = customers.filter((customer) => `${customer.name} ${customer.tel} ${customer.address}`.toLowerCase().includes(keyword.toLowerCase()));

  return (
    <>
      <PageHeader
        title="客户信息管理"
        breadcrumb={["基础数据", "客户信息管理"]}
        extra={<Button icon={<Plus className="h-[18px] w-[18px]" />} onClick={() => setCreating(true)}>新增客户</Button>}
      />
      <Section title="客户列表" description="维护起草合同时可选择的客户基础资料。">
        <input className={`${inputClass} mb-6 max-w-md`} placeholder="搜索客户名称、电话或地址" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        {list.length ? (
          <div className="overflow-x-auto">
            <table>
              <thead><tr><th>客户名称</th><th>电话</th><th>地址</th><th>邮箱</th><th>银行名称</th><th>操作</th></tr></thead>
              <tbody>
                {list.map((customer) => (
                  <tr key={customer.id}>
                    <td className="font-medium">{customer.name}</td>
                    <td>{customer.tel}</td>
                    <td>{customer.address}</td>
                    <td>{customer.email || "-"}</td>
                    <td>{customer.bank || "-"}</td>
                    <td>
                      <div className="flex gap-2">
                        <Button variant="text" className="min-h-8 px-2" onClick={() => setEditing(customer)}>编辑</Button>
                        <Button
                          variant="text"
                          className="min-h-8 px-2 text-error"
                          icon={<Trash2 className="h-[18px] w-[18px]" />}
                          onClick={() => openConfirm({
                            title: "删除客户",
                            message: `确认删除客户「${customer.name}」？`,
                            onConfirm: async () => {
                              const result = await deleteCustomer(customer.id);
                              showToast(result.ok ? "success" : "error", result.message || "客户已删除");
                            }
                          })}
                        >
                          删除
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <EmptyState />}
      </Section>
      {(editing || creating) ? (
        <Modal title={editing ? "编辑客户" : "新增客户"} onClose={() => { setEditing(null); setCreating(false); }} width="max-w-4xl">
          <CustomerForm
            initial={editing || undefined}
            onSubmit={(customer) => {
              upsertCustomer(customer);
              showToast("success", "客户信息已保存");
              setEditing(null);
              setCreating(false);
            }}
          />
        </Modal>
      ) : null}
    </>
  );
}
