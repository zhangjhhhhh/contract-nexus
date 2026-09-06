import { zodResolver } from "@hookform/resolvers/zod";
import { FileUp, Pause, Play, Save, X } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { createChunkUploader, type UploadProgress } from "../api/files";
import type { Attachment, Contract, Customer } from "../types";
import { Button } from "./Button";
import { Field, inputClass, selectClass, textareaClass } from "./Form";

const schema = z.object({
  name: z.string().min(1, "请输入合同名称"),
  customerId: z.string().min(1, "请选择客户"),
  beginTime: z.string().min(1, "请选择开始时间"),
  endTime: z.string().min(1, "请选择结束时间"),
  content: z.string().min(1, "请输入合同内容")
}).refine((data) => !data.beginTime || !data.endTime || data.endTime >= data.beginTime, {
  message: "结束时间不能早于开始时间",
  path: ["endTime"]
});

export type ContractFormValues = z.infer<typeof schema>;

export function ContractForm({
  customers,
  initial,
  submitText = "提交",
  requireAttachment = true,
  onSubmit
}: {
  customers: Customer[];
  initial?: Contract;
  submitText?: string;
  requireAttachment?: boolean;
  onSubmit: (values: Omit<ContractFormValues, "attachmentName"> & { attachments?: Attachment[] }) => Promise<void> | void;
}) {
  const [loading, setLoading] = useState(false);
  const [attachment, setAttachment] = useState<Attachment | undefined>(initial?.attachments?.[0]);
  const [uploadError, setUploadError] = useState("");
  const [progress, setProgress] = useState<UploadProgress>({ state: "idle", uploaded: 0, total: 0, percent: 0 });
  const [uploader, setUploader] = useState<ReturnType<typeof createChunkUploader> | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<ContractFormValues>({
    resolver: zodResolver(schema),
    mode: "onBlur",
    defaultValues: {
      name: initial?.name || "",
      customerId: initial?.customerId || "",
      beginTime: initial?.beginTime || "",
      endTime: initial?.endTime || "",
      content: initial?.content || ""
    }
  });

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const ext = file.name.split(".").pop()?.toLowerCase();
    if (!ext || !["docx", "pdf"].includes(ext)) {
      setUploadError("仅支持 docx/pdf 格式");
      return;
    }

    setUploadError("");
    setAttachment(undefined);

    const chunker = createChunkUploader(file, (p) => setProgress({ ...p }));
    setUploader(chunker);
    setProgress({ state: "uploading", uploaded: 0, total: 0, percent: 0 });

    try {
      const info = await chunker.start();
      setAttachment({ name: info.name, type: info.type, path: info.path });
      setProgress({ state: "done", uploaded: 0, total: 0, percent: 100 });
      setUploader(null);
    } catch (error) {
      if (error instanceof Error && error.message === "上传已取消") {
        setUploadError("上传已取消");
      } else {
        setUploadError(error instanceof Error ? error.message : "上传失败");
      }
      setProgress({ state: "error", uploaded: 0, total: 0, percent: 0 });
      setUploader(null);
      event.target.value = "";
    }
  };

  const submit = async (values: ContractFormValues) => {
    if (requireAttachment && !attachment) {
      setUploadError("请上传 docx 或 pdf 合同文件用于AI审查");
      return;
    }

    setLoading(true);
    try {
      await onSubmit({
        name: values.name,
        customerId: values.customerId,
        beginTime: values.beginTime,
        endTime: values.endTime,
        content: values.content,
        attachments: attachment ? [attachment] : undefined
      });
      if (!initial) reset();
      setAttachment(undefined);
    } finally {
      setLoading(false);
    }
  };

  const isUploading = progress.state === "uploading" || progress.state === "paused";

  return (
    <form className="grid gap-6" onSubmit={handleSubmit(submit)}>
      <div className="grid gap-6 md:grid-cols-2">
        <Field label="合同名称" error={errors.name?.message} required>
          <input className={inputClass} {...register("name")} />
        </Field>
        <Field label="客户" error={errors.customerId?.message} required>
          <select className={selectClass} {...register("customerId")}>
            <option value="">请选择客户</option>
            {customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}
          </select>
        </Field>
        <Field label="开始时间" error={errors.beginTime?.message} required>
          <input className={inputClass} type="date" {...register("beginTime")} />
        </Field>
        <Field label="结束时间" error={errors.endTime?.message} required>
          <input className={inputClass} type="date" {...register("endTime")} />
        </Field>
      </div>
      <Field label="合同内容" error={errors.content?.message} required>
        <textarea className={`${textareaClass} min-h-[200px]`} {...register("content")} />
      </Field>
      <Field label="合同附件" error={uploadError} required={requireAttachment}>
        {isUploading ? (
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <div className="h-2 flex-1 rounded-full bg-section overflow-hidden">
                <div
                  className={["h-full rounded-full transition-all", progress.state === "paused" ? "bg-warning" : "bg-primary"].join(" ")}
                  style={{ width: `${progress.percent}%` }}
                />
              </div>
              <span className="text-body font-medium text-text min-w-[3ch]">{progress.percent}%</span>
            </div>
            <div className="flex items-center gap-2">
              {progress.state === "paused" ? (
                <Button variant="secondary" className="min-h-8 px-3" icon={<Play className="h-4 w-4" />} onClick={() => uploader?.resume()}>继续</Button>
              ) : (
                <Button variant="secondary" className="min-h-8 px-3" icon={<Pause className="h-4 w-4" />} onClick={() => uploader?.pause()}>暂停</Button>
              )}
              <Button variant="text" className="min-h-8 px-3 text-error" icon={<X className="h-4 w-4" />} onClick={() => uploader?.abort()}>取消</Button>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <label className={[
              "inline-flex cursor-pointer items-center gap-2 rounded-button px-4 py-2 text-body font-medium transition-colors",
              "bg-section text-text hover:bg-hover"
            ].join(" ")}>
              <FileUp className="h-[18px] w-[18px]" />
              {attachment ? "重新上传" : "选择合同文件"}
              <input type="file" className="hidden" accept=".docx,.pdf" onChange={handleFileChange} />
            </label>
            {attachment ? (
              <span className="text-body text-success">{attachment.name} ✓</span>
            ) : (
              <span className="text-body text-muted">支持 docx/pdf，提交后后台提取文字并AI审查</span>
            )}
          </div>
        )}
      </Field>
      <div className="flex justify-end">
        <Button loading={loading || isUploading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">
          {submitText}
        </Button>
      </div>
    </form>
  );
}
