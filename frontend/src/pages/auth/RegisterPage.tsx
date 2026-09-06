import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck, UserPlus } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { sendRegisterCode } from "../../api/auth";
import { Button } from "../../components/Button";
import { Field, inputClass } from "../../components/Form";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import { AuthShell } from "./AuthShell";

const schema = z.object({
  name: z.string().regex(/^(?:[A-Za-z][A-Za-z0-9_]{2,}|[\u4e00-\u9fa5][\u4e00-\u9fa5A-Za-z0-9_]{1,})$/, "用户名支持中文或英文；中文用户名至少 2 位，英文用户名至少 3 位并以字母开头"),
  password: z.string().min(6, "密码至少 6 位"),
  confirm: z.string().min(6, "请再次输入密码"),
  email: z.string().email("请输入正确的邮箱"),
  verificationCode: z.string().regex(/^\d{4}$/, "请输入 4 位数字验证码")
}).refine((data) => data.password === data.confirm, {
  message: "两次密码不一致",
  path: ["confirm"]
});

type RegisterForm = z.infer<typeof schema>;

export function RegisterPage() {
  const registerUser = useAppStore((state) => state.register);
  const showToast = useUiStore((state) => state.showToast);
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [codeLoading, setCodeLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const { register, handleSubmit, getValues, formState: { errors } } = useForm<RegisterForm>({
    resolver: zodResolver(schema),
    mode: "onBlur"
  });

  const handleSendCode = async () => {
    const email = getValues("email");
    const parsed = z.string().email().safeParse(email);
    if (!parsed.success) {
      showToast("error", "请先输入正确的邮箱");
      return;
    }

    setCodeLoading(true);
    try {
      await sendRegisterCode({ email });
      showToast("success", "验证码已发送，请查收邮箱");
      setCountdown(60);
      const timer = window.setInterval(() => {
        setCountdown((value) => {
          if (value <= 1) {
            window.clearInterval(timer);
            return 0;
          }
          return value - 1;
        });
      }, 1000);
    } catch (error) {
      showToast("error", error instanceof Error ? error.message : "验证码发送失败");
    } finally {
      setCodeLoading(false);
    }
  };

  const onSubmit = (data: RegisterForm) => {
    setLoading(true);
    window.setTimeout(async () => {
      const result = await registerUser(data.name, data.password, data.email, data.verificationCode);
      setLoading(false);
      if (!result.ok) {
        showToast("error", result.message || "注册失败");
        return;
      }
      showToast("success", "注册成功，请登录系统");
      navigate("/login", { replace: true });
    }, 700);
  };

  return (
    <AuthShell title="注册账号" description="注册成功后默认获得合同操作员角色，后续可由管理员统一调整。">
      <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
        <Field label="用户名" error={errors.name?.message} required>
          <input className={inputClass} autoComplete="username" {...register("name")} />
        </Field>
        <Field label="密码" error={errors.password?.message} required>
          <input className={inputClass} type="password" autoComplete="new-password" {...register("password")} />
        </Field>
        <Field label="确认密码" error={errors.confirm?.message} required>
          <input className={inputClass} type="password" autoComplete="new-password" {...register("confirm")} />
        </Field>
        <Field label="邮箱" error={errors.email?.message} required>
          <input className={inputClass} type="email" autoComplete="email" {...register("email")} />
        </Field>
        <Field label="邮箱验证码" error={errors.verificationCode?.message} required>
          <div className="flex flex-col gap-3 sm:flex-row">
            <input className={`${inputClass} sm:min-w-0`} inputMode="numeric" maxLength={4} {...register("verificationCode")} />
            <Button
              className="h-12 shrink-0 whitespace-nowrap"
              disabled={countdown > 0}
              icon={<MailCheck className="h-[18px] w-[18px]" />}
              loading={codeLoading}
              type="button"
              variant="secondary"
              onClick={handleSendCode}
            >
              {countdown > 0 ? `${countdown}s` : "发送验证码"}
            </Button>
          </div>
        </Field>
        <Button className="h-12 w-full" loading={loading} icon={<UserPlus className="h-[18px] w-[18px]" />} type="submit">
          注册
        </Button>
      </form>
      <div className="mt-5 flex items-center justify-between text-note text-muted">
        <span>已有账号</span>
        <Link className="font-medium text-primary" to="/login">返回登录</Link>
      </div>
    </AuthShell>
  );
}
