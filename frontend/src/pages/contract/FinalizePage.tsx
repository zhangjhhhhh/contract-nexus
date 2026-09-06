import { useState } from "react";
import { FileUp, Pause, Play, Save, X } from "lucide-react";
import { createChunkUploader, type UploadProgress } from "../../api/files";
import { Button } from "../../components/Button";
import { ContractDetail } from "../../components/ContractDetail";
import { ContractTable } from "../../components/ContractTable";
import { Modal } from "../../components/Modal";
import { PageHeader, Section } from "../../components/Page";
import { textareaClass } from "../../components/Form";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";
import type { Attachment, Contract } from "../../types";
import { formatDateTime, processText } from "../../utils/format";

export function FinalizePage() {
  const [active, setActive] = useState<Contract | null>(null);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [attachment, setAttachment] = useState<Attachment | null>(null);
  const [uploadError, setUploadError] = useState("");
  const [progress, setProgress] = useState<UploadProgress>({ state: "idle", uploaded: 0, total: 0, percent: 0 });
  const [uploader, setUploader] = useState<ReturnType<typeof createChunkUploader> | null>(null);
  const session = useAppStore((state) => state.session);
  const contracts = useAppStore((state) => state.contracts);
  const customers = useAppStore((state) => state.customers);
  const users = useAppStore((state) => state.users);
  const processes = useAppStore((state) => state.processes);
  const submitFinalize = useAppStore((state) => state.submitFinalize);
  const showToast = useUiStore((state) => state.showToast);
  const list = contracts.filter((contract) => contract.status === "finalizing" && contract.drafterId === session?.userId);

  const open = (contract: Contract) => {
    setActive(contract);
    setContent(contract.content);
    setAttachment(null);
    setUploadError("");
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploadError("");
    setAttachment(null);

    const chunker = createChunkUploader(file, (p) => setProgress({ ...p }));
    setUploader(chunker);
    setUploading(true);
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
    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      <PageHeader title="待定稿合同" breadcrumb={["合同管理", "待定稿合同"]} />
      <Section title="定稿列表" description="起草人根据会签意见修订合同内容并提交审批。">
        <ContractTable
          contracts={list}
          customers={customers}
          users={users}
          onView={open}
          actions={(contract) => <Button variant="secondary" className="min-h-8 px-3" onClick={() => open(contract)}>定稿</Button>}
        />
      </Section>
      {active ? (
        <Modal title="合同定稿" onClose={() => setActive(null)} width="max-w-6xl">
          <ContractDetail contract={active} customers={customers} users={users} />
          <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_360px]">
            <div>
              <label className="mb-2 block text-body font-medium text-text">合同内容</label>
              <textarea className={`${textareaClass} min-h-[360px]`} value={content} onChange={(event) => setContent(event.target.value)} />
              <div className="mt-4 flex items-center justify-between">
                <div className="flex-1">
                  {progress.state === "uploading" || progress.state === "paused" ? (
                    <div className="space-y-2">
                      <div className="flex items-center gap-3">
                        <div className="h-2 flex-1 rounded-full bg-section overflow-hidden">
                          <div className={["h-full rounded-full transition-all", progress.state === "paused" ? "bg-warning" : "bg-primary"].join(" ")} style={{ width: `${progress.percent}%` }} />
                        </div>
                        <span className="text-body font-medium text-text min-w-[3ch]">{progress.percent}%</span>
                      </div>
                      <div className="flex items-center gap-2">
                        {progress.state === "paused"
                          ? <Button variant="secondary" className="min-h-8 px-3" icon={<Play className="h-4 w-4" />} onClick={() => uploader?.resume()}>继续</Button>
                          : <Button variant="secondary" className="min-h-8 px-3" icon={<Pause className="h-4 w-4" />} onClick={() => uploader?.pause()}>暂停</Button>}
                        <Button variant="text" className="min-h-8 px-3 text-error" icon={<X className="h-4 w-4" />} onClick={() => uploader?.abort()}>取消</Button>
                      </div>
                    </div>
                  ) : (
                    <div>
                      <label className={"inline-flex cursor-pointer items-center gap-2 rounded-button px-3 py-2 text-body font-medium transition-colors bg-section text-text hover:bg-hover"}>
                        <FileUp className="h-[18px] w-[18px]" />
                        {attachment ? "重新上传" : "上传修订文件"}
                        <input type="file" className="hidden" accept=".doc,.docx,.pdf" onChange={handleFileChange} />
                      </label>
                      {attachment ? <span className="ml-3 text-body text-success">{attachment.name} ✓</span> : null}
                      {uploadError ? <span className="ml-3 text-body text-error">{uploadError}</span> : null}
                    </div>
                  )}
                </div>
                <Button
                  loading={loading}
                  icon={<Save className="h-[18px] w-[18px]" />}
                  onClick={() => {
                    if (!content.trim()) {
                      showToast("error", "合同内容不能为空");
                      return;
                    }
                    setLoading(true);
                    window.setTimeout(async () => {
                      try {
                        await submitFinalize(active.id, content, attachment ? [attachment] : undefined);
                        showToast("success", "定稿成功，合同进入审批");
                        setActive(null);
                      } catch (error) {
                        showToast("error", error instanceof Error ? error.message : "定稿失败");
                      } finally {
                        setLoading(false);
                      }
                    }, 700);
                  }}
                >
                  提交定稿
                </Button>
              </div>
            </div>
            <div>
              <h3 className="mb-3 mt-0 text-subtitle font-semibold text-text">流程意见汇总</h3>
              <div className="space-y-3">
                {processes.filter((process) => process.contractId === active.id && (process.type === "countersign" || process.type === "approve")).map((process) => (
                  <div className="rounded-card border border-line p-4" key={process.id}>
                    <div className="text-body font-medium text-text">
                      {processText[process.type]}：{users.find((user) => user.id === process.userId)?.name || process.userId}
                    </div>
                    <div className="mt-1 text-note text-muted">{formatDateTime(process.time)}</div>
                    <p className="m-0 mt-2 text-body text-text">{process.content || "暂无意见"}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Modal>
      ) : null}
    </>
  );
}
