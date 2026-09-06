import type { PermissionKey, User } from "../types";
import { request } from "./http";

export type AuthResponse = {
  user: User;
  permissions: PermissionKey[];
  redirectTo: string;
};

export function login(payload: { name: string; password: string }) {
  return request<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
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
