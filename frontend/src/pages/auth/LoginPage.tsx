import { zodResolver } from "@hookform/resolvers/zod";
import { LogIn } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Form";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import { AuthShell } from "./AuthShell";

const schema = z.object({
  name: z.string().regex(/^(?:[A-Za-z][A-Za-z0-9_]{2,}|[\u4e00-\u9fa5][\u4e00-\u9fa5A-Za-z0-9_]{1,})$/, "用户名支持中文或英文；中文用户名至少 2 位，英文用户名至少 3 位并以字母开头"),
  password: z.string().min(6, "密码至少 6 位")
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const login = useAppStore((state) => state.login);
  const showToast = useUiStore((state) => state.showToast);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<LoginForm>({ resolver: zodResolver(schema), mode: "onBlur" });

  const onSubmit = (data: LoginForm) => {
    setLoading(true);
    window.setTimeout(async () => {
      const result = await login(data.name, data.password);
      setLoading(false);
      if (!result.ok) {
        showToast("error", result.message || "登录失败");
        return;
      }
      showToast("success", "登录成功");
      navigate(result.redirectTo || "/dashboard", { replace: true });
    }, 650);
  };

  return (
    <AuthShell title="登录系统" description="请使用企业内部账号登录，系统会按角色展示菜单。">
      <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
        <Field label="用户名" error={errors.name?.message} required>
          <input className={inputClass} autoComplete="username" {...register("name")} />
        </Field>
        <Field label="密码" error={errors.password?.message} required>
          <input className={inputClass} type="password" autoComplete="current-password" {...register("password")} />
        </Field>
        <Button className="h-12 w-full" loading={loading} icon={<LogIn className="h-[18px] w-[18px]" />} type="submit">
          登录
        </Button>
      </form>
      <div className="mt-5 flex items-center justify-between text-note text-muted">
        <span>新用户需要先注册</span>
        <Link className="font-medium text-primary" to="/register">去注册</Link>
      </div>
    </AuthShell>
  );
}
