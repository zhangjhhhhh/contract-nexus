import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Form";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { User } from "../../types";

const schema = z.object({
  name: z.string().regex(/^(?:[A-Za-z][A-Za-z0-9_]{2,}|[\u4e00-\u9fa5][\u4e00-\u9fa5A-Za-z0-9_]{1,})$/, "用户名支持中文或英文；中文用户名至少 2 位，英文用户名至少 3 位并以字母开头"),
  password: z.string().min(6, "密码至少 6 位"),
  email: z.string().email("请输入正确的邮箱").optional().or(z.literal(""))
});

function UserForm({ initial, onSubmit }: { initial?: User; onSubmit: (user: User) => Promise<void> | void }) {
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
        window.setTimeout(async () => {
          await onSubmit({ id: initial?.id || "", roleIds: initial?.roleIds || ["new_user"], ...values });
          setLoading(false);
        }, 650);
      })}
    >
      <Field label="用户名" error={errors.name?.message} required><input className={inputClass} {...register("name")} /></Field>
      <Field label="密码" error={errors.password?.message} required><input className={inputClass} type="password" {...register("password")} /></Field>
      <Field label="邮箱" error={errors.email?.message}><input className={inputClass} type="email" {...register("email")} /></Field>
      <div className="flex justify-end"><Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">保存用户</Button></div>
    </form>
  );
}

export function UserPage() {
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState<User | null>(null);
  const [creating, setCreating] = useState(false);
  const users = useAppStore((state) => state.users);
  const roles = useAppStore((state) => state.roles);
  const upsertUser = useAppStore((state) => state.upsertUser);
  const deleteUser = useAppStore((state) => state.deleteUser);
  const showToast = useUiStore((state) => state.showToast);
  const openConfirm = useUiStore((state) => state.openConfirm);
  const list = users.filter((user) => user.name.toLowerCase().includes(keyword.toLowerCase()));

  return (
    <>
      <PageHeader
        title="用户管理"
        breadcrumb={["系统管理", "用户管理"]}
        extra={<Button icon={<Plus className="h-[18px] w-[18px]" />} onClick={() => setCreating(true)}>新增用户</Button>}
      />
      <Section title="用户列表" description="新增、编辑、删除和查询系统用户。">
        <input className={`${inputClass} mb-6 max-w-md`} placeholder="搜索用户名" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <div className="overflow-x-auto">
          <table>
            <thead><tr><th>用户名</th><th>邮箱</th><th>角色</th><th>操作</th></tr></thead>
            <tbody>
              {list.map((user) => (
                <tr key={user.id}>
                  <td className="font-medium">{user.name}</td>
                  <td>{user.email || "未配置"}</td>
                  <td>{user.roleIds.map((id) => roles.find((role) => role.id === id)?.name || id).join("、")}</td>
                  <td>
                    <div className="flex gap-2">
                      <Button variant="text" className="min-h-8 px-2" onClick={() => setEditing(user)}>编辑</Button>
                      <Button
                        variant="text"
                        className="min-h-8 px-2 text-error"
                        icon={<Trash2 className="h-[18px] w-[18px]" />}
                        onClick={() => openConfirm({
                          title: "删除用户",
                          message: `确认删除用户「${user.name}」？`,
                          onConfirm: async () => {
                            const result = await deleteUser(user.id);
                            showToast(result.ok ? "success" : "error", result.message || "用户已删除");
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
      </Section>
      {(editing || creating) ? (
        <Modal title={editing ? "编辑用户" : "新增用户"} onClose={() => { setEditing(null); setCreating(false); }} width="max-w-xl">
          <UserForm
            initial={editing || undefined}
            onSubmit={async (user) => {
              await upsertUser(user);
              showToast("success", "用户信息已保存");
              setEditing(null);
              setCreating(false);
            }}
          />
        </Modal>
      ) : null}
    </>
  );
}
