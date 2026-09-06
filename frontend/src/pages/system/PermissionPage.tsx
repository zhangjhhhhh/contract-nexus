import { Save } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";

export function PermissionPage() {
  const users = useAppStore((state) => state.users);
  const roles = useAppStore((state) => state.roles);
  const setUserRoles = useAppStore((state) => state.setUserRoles);
  const showToast = useUiStore((state) => state.showToast);
  const [draft, setDraft] = useState<Record<string, string[]>>(() => Object.fromEntries(users.map((user) => [user.id, user.roleIds])));

  return (
    <>
      <PageHeader title="权限分配" breadcrumb={["系统管理", "权限分配"]} />
      <Section title="用户角色分配" description="给用户勾选一个或多个角色，菜单与路由权限会即时按角色生效。">
        <div className="grid gap-4">
          {users.map((user) => (
            <div className="grid gap-4 rounded-card border border-line p-4 lg:grid-cols-[180px_minmax(0,1fr)_120px]" key={user.id}>
              <div>
                <div className="text-body font-medium text-text">{user.name}</div>
                <div className="text-note text-muted">{user.id}</div>
              </div>
              <div className="flex flex-wrap gap-x-6 gap-y-3">
                {roles.map((role) => (
                  <label className="inline-flex items-center gap-2 text-body text-text" key={role.id}>
                    <input
                      className="h-4 w-4"
                      type="checkbox"
                      checked={(draft[user.id] || []).includes(role.id)}
                      onChange={(event) => {
                        const current = draft[user.id] || [];
                        setDraft({
                          ...draft,
                          [user.id]: event.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id)
                        });
                      }}
                    />
                    {role.name}
                  </label>
                ))}
              </div>
              <div className="flex justify-end">
                <Button
                  icon={<Save className="h-[18px] w-[18px]" />}
                  onClick={async () => {
                    if (!(draft[user.id] || []).length) {
                      showToast("error", "至少选择一个角色");
                      return;
                    }
                    await setUserRoles(user.id, draft[user.id]);
                    showToast("success", "权限分配已保存");
                  }}
                >
                  保存
                </Button>
              </div>
            </div>
          ))}
        </div>
      </Section>
    </>
  );
}
