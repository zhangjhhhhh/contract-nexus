const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080/api";

export const TOKEN_KEY = "contract-management-token";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export async function request<T>(path: string, options: RequestInit = {}) {
  const token = getToken();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });

  const payload = (await response.json()) as ApiResponse<T>;

  if (response.status === 401) {
    clearToken();
    localStorage.removeItem("contract-management-system");
    window.location.href = "/login";
    throw new ApiError(401, payload.message || "未登录或登录已过期");
  }

  if (!response.ok || payload.code !== 0) {
    throw new ApiError(payload.code || response.status, payload.message || "接口请求失败");
  }

  return payload.data;
}
