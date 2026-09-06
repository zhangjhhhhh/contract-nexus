import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Save, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "../../components/Button";
import { Field, inputClass, textareaClass } from "../../components/Form";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { permissionGroups } from "../../mock/data";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { PermissionKey, Role } from "../../types";

const schema = z.object({
  name: z.string().min(1, "请输入角色名称"),
  description: z.string().min(1, "请输入角色说明")
});

function RoleForm({ initial, onSubmit }: { initial?: Role; onSubmit: (role: Role) => Promise<void> | void }) {
  const [selected, setSelected] = useState<PermissionKey[]>(initial?.permissions || []);
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
          await onSubmit({ id: initial?.id || "", permissions: selected, ...values });
          setLoading(false);
        }, 650);
      })}
    >
      <Field label="角色名称" error={errors.name?.message} required><input className={inputClass} {...register("name")} /></Field>
      <Field label="角色说明" error={errors.description?.message} required><textarea className={textareaClass} {...register("description")} /></Field>
      <div>
        <div className="mb-3 text-body font-medium text-text">权限勾选</div>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {permissionGroups.map((group) => (
            <div className="rounded-card border border-line p-4" key={group.group}>
              <div className="mb-3 text-body font-semibold text-text">{group.group}</div>
              <div className="grid gap-2">
                {group.items.map((item) => (
                  <label className="flex items-center gap-2 text-body text-text" key={item.key}>
                    <input
                      className="h-4 w-4"
                      type="checkbox"
                      checked={selected.includes(item.key)}
                      onChange={(event) => setSelected(event.target.checked ? [...selected, item.key] : selected.filter((key) => key !== item.key))}
                    />
                    {item.label}
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="flex justify-end"><Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">保存角色</Button></div>
    </form>
  );
}

export function RolePage() {
  const [editing, setEditing] = useState<Role | null>(null);
  const [creating, setCreating] = useState(false);
  const roles = useAppStore((state) => state.roles);
  const upsertRole = useAppStore((state) => state.upsertRole);
  const deleteRole = useAppStore((state) => state.deleteRole);
  const showToast = useUiStore((state) => state.showToast);
  const openConfirm = useUiStore((state) => state.openConfirm);

  return (
    <>
      <PageHeader
        title="角色管理"
        breadcrumb={["系统管理", "角色管理"]}
        extra={<Button icon={<Plus className="h-[18px] w-[18px]" />} onClick={() => setCreating(true)}>新增角色</Button>}
      />
      <Section title="角色列表" description="角色内按权限分组勾选功能点。">
        <div className="overflow-x-auto">
          <table>
            <thead><tr><th>角色名称</th><th>说明</th><th>权限数</th><th>操作</th></tr></thead>
            <tbody>
              {roles.map((role) => (
                <tr key={role.id}>
                  <td className="font-medium">{role.name}</td>
                  <td>{role.description}</td>
                  <td>{role.permissions.length}</td>
                  <td>
                    <div className="flex gap-2">
                      <Button variant="text" className="min-h-8 px-2" onClick={() => setEditing(role)}>编辑</Button>
                      <Button
                        variant="text"
                        className="min-h-8 px-2 text-error"
                        icon={<Trash2 className="h-[18px] w-[18px]" />}
                        onClick={() => openConfirm({
                          title: "删除角色",
                          message: `确认删除角色「${role.name}」？`,
                          onConfirm: async () => {
                            const result = await deleteRole(role.id);
                            showToast(result.ok ? "success" : "error", result.message || "角色已删除");
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
        <Modal title={editing ? "编辑角色" : "新增角色"} onClose={() => { setEditing(null); setCreating(false); }} width="max-w-6xl">
          <RoleForm
            initial={editing || undefined}
            onSubmit={async (role) => {
              await upsertRole(role);
              showToast("success", "角色信息已保存");
              setEditing(null);
              setCreating(false);
            }}
          />
        </Modal>
      ) : null}
    </>
  );
}
