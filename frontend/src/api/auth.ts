import type { PermissionKey, User } from "../types";
import { request, setToken } from "./http";

export type AuthResponse = {
  user: User;
  permissions: PermissionKey[];
  redirectTo: string;
  token: string;
};

export async function login(payload: { name: string; password: string }) {
  const result = await request<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  setToken(result.token);
  return result;
}

export function logout() {
  return request<void>("/auth/logout", { method: "POST" });
}

export function sendRegisterCode(payload: { email: string }) {
  return request<void>("/auth/register/code", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function register(payload: { name: string; password: string; email: string; verificationCode: string }) {
  return request<User>("/auth/register", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
