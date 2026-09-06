import type { Customer, Log, Role, User } from "../types";
import { request } from "./http";

export function fetchUsers() {
  return request<User[]>("/users");
}

export function createUser(payload: Omit<User, "id">) {
  return request<User>("/users", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateUser(user: User) {
  return request<User>(`/users/${user.id}`, {
    method: "PUT",
    body: JSON.stringify({ name: user.name, password: user.password, email: user.email, roleIds: user.roleIds })
  });
}

export function deleteUser(id: string) {
  return request<void>(`/users/${id}`, { method: "DELETE" });
}

export function fetchRoles() {
  return request<Role[]>("/roles");
}

export function createRole(payload: Omit<Role, "id">) {
  return request<Role>("/roles", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateRole(role: Role) {
  return request<Role>(`/roles/${role.id}`, {
    method: "PUT",
    body: JSON.stringify({ name: role.name, description: role.description, permissions: role.permissions })
  });
}

export function deleteRole(id: string) {
  return request<void>(`/roles/${id}`, { method: "DELETE" });
}

export function setUserRoles(userId: string, roleIds: string[]) {
  return request<User>(`/users/${userId}/roles`, {
    method: "PUT",
    body: JSON.stringify({ roleIds })
  });
}

export function fetchLogs() {
  return request<Log[]>("/logs");
}

export function fetchCustomers() {
  return request<Customer[]>("/customers");
}

export function createCustomer(payload: Omit<Customer, "id">) {
  return request<Customer>("/customers", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateCustomer(id: string, payload: Omit<Customer, "id">) {
  return request<Customer>(`/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteCustomer(id: string) {
  return request<void>(`/customers/${id}`, { method: "DELETE" });
}
