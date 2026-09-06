const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080/api";

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

export async function request<T>(path: string, options: RequestInit = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    }
  });

  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || payload.code !== 0) {
    throw new ApiError(payload.code || response.status, payload.message || "接口请求失败");
  }
  return payload.data;
}

