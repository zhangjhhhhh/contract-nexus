import { request } from "./http";

export type AttachmentInfo = {
  name: string;
  storedName: string;
  type: string;
  path: string;
  uploadTime: string;
};

const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080/api";

// Simple single-file upload
export async function uploadFile(file: File): Promise<AttachmentInfo> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/files`, {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: "上传失败" }));
    throw new Error(error.message || "上传失败");
  }

  const result = await response.json();
  if (result.code !== 0) {
    throw new Error(result.message || "上传失败");
  }
  return result.data;
}

// Chunked upload
const CHUNK_SIZE = 1 * 1024 * 1024; // 1MB per chunk

export type UploadState = "idle" | "uploading" | "paused" | "done" | "error";

export type UploadProgress = {
  state: UploadState;
  uploaded: number;
  total: number;
  percent: number;
};

export function createChunkUploader(
  file: File,
  onProgress: (p: UploadProgress) => void
) {
  const STORAGE_KEY = "chunk_uploads";

  // Resume from previous session if same file
  const getSavedFileId = (): string | null => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
      const key = `${file.name}_${file.size}`;
      return saved[key] || null;
    } catch { return null; }
  };

  const saveFileId = (fileId: string) => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
      saved[`${file.name}_${file.size}`] = fileId;
      localStorage.setItem(STORAGE_KEY, JSON.stringify(saved));
    } catch {}
  };

  const removeFileId = () => {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
      delete saved[`${file.name}_${file.size}`];
      localStorage.setItem(STORAGE_KEY, JSON.stringify(saved));
    } catch {}
  };

  const fileId = getSavedFileId() || createUploadId();
  if (getSavedFileId()) {
    // Found saved session — will resume
  } else {
    saveFileId(fileId);
  }

  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  const uploadedChunks = new Set<number>();
  let paused = false;
  let aborted = false;

  const progress = (): UploadProgress => ({
    state: paused ? "paused" : "uploading",
    uploaded: uploadedChunks.size,
    total: totalChunks,
    percent: Math.round((uploadedChunks.size / totalChunks) * 100)
  });

  const uploadChunk = async (index: number): Promise<void> => {
    const start = index * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const blob = file.slice(start, end);

    const formData = new FormData();
    formData.append("file", blob, `chunk_${index}`);
    formData.append("fileId", fileId);
    formData.append("chunkIndex", String(index));
    formData.append("totalChunks", String(totalChunks));
    formData.append("fileName", file.name);

    const response = await fetch(`${API_BASE}/files/chunk`, {
      method: "POST",
      body: formData
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: "分片上传失败" }));
      throw new Error(err.message || "分片上传失败");
    }
    const result = await response.json();
    if (result.code !== 0) {
      throw new Error(result.message || "分片上传失败");
    }

    uploadedChunks.add(index);
    onProgress(progress());
  };

  const fetchUploadedChunks = async (): Promise<number[]> => {
    const response = await fetch(`${API_BASE}/files/chunks/${fileId}`);
    if (!response.ok) return [];
    const result = await response.json();
    if (result.code !== 0) return [];
    return result.data.uploadedChunks || [];
  };

  const start = async (): Promise<AttachmentInfo> => {
    paused = false;
    aborted = false;

    // Check if any chunks were already uploaded (resume)
    const existing = await fetchUploadedChunks();
    for (const idx of existing) uploadedChunks.add(idx);
    onProgress(progress());

    for (let i = 0; i < totalChunks; i++) {
      if (aborted) throw new Error("上传已取消");
      while (paused && !aborted) {
        await new Promise(r => setTimeout(r, 200));
      }
      if (aborted) throw new Error("上传已取消");
      if (uploadedChunks.has(i)) continue;

      await uploadChunk(i);
    }

    // Merge
    const mergeResponse = await fetch(`${API_BASE}/files/merge`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fileId, fileName: file.name, totalChunks })
    });

    if (!mergeResponse.ok) {
      const err = await mergeResponse.json().catch(() => ({ message: "合并失败" }));
      throw new Error(err.message || "合并失败");
    }
    const mergeResult = await mergeResponse.json();
    if (mergeResult.code !== 0) {
      throw new Error(mergeResult.message || "合并失败");
    }

    onProgress({ state: "done", uploaded: totalChunks, total: totalChunks, percent: 100 });
    removeFileId();
    return mergeResult.data;
  };

  return {
    start,
    pause: () => { paused = true; onProgress(progress()); },
    resume: () => { paused = false; onProgress(progress()); },
    abort: () => { aborted = true; paused = false; removeFileId(); }
  };
}

export function getDownloadUrl(path: string): string {
  return toApiFileUrl(path);
}

export function getPreviewUrl(path: string): string {
  return toApiFileUrl(path.replace("/api/files/", "/api/files/preview/"));
}

function createUploadId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }

  const randomValues = typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function"
    ? crypto.getRandomValues(new Uint8Array(16))
    : Array.from({ length: 16 }, () => Math.floor(Math.random() * 256));

  randomValues[6] = (randomValues[6] & 0x0f) | 0x40;
  randomValues[8] = (randomValues[8] & 0x3f) | 0x80;

  const hex = Array.from(randomValues, (value) => value.toString(16).padStart(2, "0"));
  return [
    hex.slice(0, 4).join(""),
    hex.slice(4, 6).join(""),
    hex.slice(6, 8).join(""),
    hex.slice(8, 10).join(""),
    hex.slice(10, 16).join("")
  ].join("-");
}

function toApiFileUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  if (path.startsWith("/api/")) {
    return `${API_BASE.replace(/\/api$/, "")}${path}`;
  }
  return path;
}
