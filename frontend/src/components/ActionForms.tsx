import { zodResolver } from "@hookform/resolvers/zod";
import { RotateCcw, Save } from "lucide-react";
import { type PointerEvent, useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "./Button";
import { Field, inputClass, selectClass, textareaClass } from "./Form";
export { ElectronicSignForm } from "./ElectronicSignForm";

const opinionSchema = z.object({
  content: z.string().min(1, "请输入意见")
});

const approvalSchema = z.object({
  result: z.enum(["approved", "rejected"]),
  content: z.string().min(1, "请输入审批意见")
});

const signSchema = z.object({
  signDate: z.string().min(1, "请选择签订日期"),
  method: z.string().min(1, "请输入签订方式"),
  remark: z.string().optional()
});

export function OpinionForm({ label = "会签意见", onSubmit }: { label?: string; onSubmit: (content: string) => void }) {
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<z.infer<typeof opinionSchema>>({
    resolver: zodResolver(opinionSchema),
    mode: "onBlur"
  });

  return (
    <form
      className="grid gap-6"
      onSubmit={handleSubmit((values) => {
        setLoading(true);
        window.setTimeout(() => {
          onSubmit(values.content);
          setLoading(false);
        }, 700);
      })}
    >
      <Field label={label} error={errors.content?.message} required>
        <textarea className={textareaClass} {...register("content")} />
      </Field>
      <div className="flex justify-end">
        <Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">提交</Button>
      </div>
    </form>
  );
}

export function ApprovalForm({ onSubmit }: { onSubmit: (values: { result: "approved" | "rejected"; content: string }) => void }) {
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<z.infer<typeof approvalSchema>>({
    resolver: zodResolver(approvalSchema),
    mode: "onBlur",
    defaultValues: { result: "approved" }
  });

  return (
    <form
      className="grid gap-6"
      onSubmit={handleSubmit((values) => {
        setLoading(true);
        window.setTimeout(() => {
          onSubmit(values);
          setLoading(false);
        }, 700);
      })}
    >
      <Field label="审批结果" required>
        <div className="flex gap-6 text-body text-text">
          <label className="inline-flex items-center gap-2"><input className="h-4 w-4" type="radio" value="approved" {...register("result")} />通过</label>
          <label className="inline-flex items-center gap-2"><input className="h-4 w-4" type="radio" value="rejected" {...register("result")} />拒绝</label>
        </div>
      </Field>
      <Field label="审批意见" error={errors.content?.message} required>
        <textarea className={textareaClass} {...register("content")} />
      </Field>
      <div className="flex justify-end">
        <Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">提交审批</Button>
      </div>
    </form>
  );
}

export function SignForm({ onSubmit }: { onSubmit: (values: { signDate: string; method: string; remark?: string }) => void }) {
  const [loading, setLoading] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<z.infer<typeof signSchema>>({
    resolver: zodResolver(signSchema),
    mode: "onBlur"
  });

  return (
    <form
      className="grid gap-6"
      onSubmit={handleSubmit((values) => {
        setLoading(true);
        window.setTimeout(() => {
          onSubmit(values);
          setLoading(false);
        }, 700);
      })}
    >
      <div className="grid gap-6 md:grid-cols-2">
        <Field label="签订日期" error={errors.signDate?.message} required>
          <input className={inputClass} type="date" {...register("signDate")} />
        </Field>
        <Field label="签订方式" error={errors.method?.message} required>
          <select className={selectClass} {...register("method")}>
            <option value="">请选择签订方式</option>
            <option value="电子签章">电子签章</option>
            <option value="线下盖章">线下盖章</option>
            <option value="双方邮寄">双方邮寄</option>
          </select>
        </Field>
      </div>
      <Field label="备注">
        <textarea className={textareaClass} {...register("remark")} />
      </Field>
      <div className="flex justify-end">
        <Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">完成签订</Button>
      </div>
    </form>
  );
}

type ElectronicSignValues = {
  signDate: string;
  method: string;
  signerName: string;
  remark?: string;
  signatureDataUrl: string;
};

type SignaturePoint = { x: number; y: number };
type CanvasMetrics = { width: number; height: number; dpr: number };

function LegacyElectronicSignForm({ onSubmit }: { onSubmit: (values: ElectronicSignValues) => void }) {
  const [loading, setLoading] = useState(false);
  const [signatureError, setSignatureError] = useState("");
  const [formError, setFormError] = useState("");
  const [values, setValues] = useState({
    signDate: new Date().toISOString().slice(0, 10),
    method: "电子签章",
    signerName: "",
    remark: ""
  });

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  // ── stroke model (exactly like the Java reference) ──
  // strokes: list of completed strokes; each stroke = array of {x,y} points
  const strokesRef = useRef<SignaturePoint[][]>([]);
  const currentStrokeRef = useRef<SignaturePoint[] | null>(null);
  const hasSignatureRef = useRef(false);
  const canvasMetricsRef = useRef<CanvasMetrics>({ width: 0, height: 0, dpr: 1 });
  const isDrawingRef = useRef(false);
  const pendingResizeRef = useRef(false);

  const configureContext = useCallback((ctx: CanvasRenderingContext2D) => {
    ctx.strokeStyle = "#111827";
    ctx.fillStyle = "#111827";
    ctx.lineWidth = 3;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
  }, []);

  const prepareContext = useCallback((ctx: CanvasRenderingContext2D) => {
    const dpr = canvasMetricsRef.current.dpr || 1;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    configureContext(ctx);
  }, [configureContext]);

  /** Redraw every stroke from scratch — same idea as paintComponent(Graphics) */
  const redrawAll = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    prepareContext(ctx);

    for (const stroke of strokesRef.current) {
      if (stroke.length < 2) {
        // single-point click → tiny dot
        if (stroke.length === 1) {
          ctx.beginPath();
          ctx.arc(stroke[0].x, stroke[0].y, 1.5, 0, Math.PI * 2);
          ctx.fill();
        }
        continue;
      }
      ctx.beginPath();
      ctx.moveTo(stroke[0].x, stroke[0].y);
      for (let i = 1; i < stroke.length; i++) {
        ctx.lineTo(stroke[i].x, stroke[i].y);
      }
      ctx.stroke();
    }
  }, [prepareContext]);

  const syncCanvasSize = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return false;

    const rect = canvas.getBoundingClientRect();
    const nextWidth = Math.max(1, Math.round(rect.width));
    const nextHeight = Math.max(1, Math.round(rect.height));
    const nextDpr = Math.max(1, window.devicePixelRatio || 1);
    const nextBitmapWidth = Math.max(1, Math.round(nextWidth * nextDpr));
    const nextBitmapHeight = Math.max(1, Math.round(nextHeight * nextDpr));
    const current = canvasMetricsRef.current;
    const changed =
      canvas.width !== nextBitmapWidth ||
      canvas.height !== nextBitmapHeight ||
      current.width !== nextWidth ||
      current.height !== nextHeight ||
      current.dpr !== nextDpr;

    if (!changed) return false;

    if (canvas.width !== nextBitmapWidth) {
      canvas.width = nextBitmapWidth;
    }
    if (canvas.height !== nextBitmapHeight) {
      canvas.height = nextBitmapHeight;
    }
    canvasMetricsRef.current = { width: nextWidth, height: nextHeight, dpr: nextDpr };

    const ctx = canvas.getContext("2d");
    if (ctx) {
      prepareContext(ctx);
    }
    return true;
  }, [prepareContext]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const resize = () => {
      if (isDrawingRef.current) {
        pendingResizeRef.current = true;
        return;
      }
      const changed = syncCanvasSize();
      if (!changed) return;
      redrawAll();
    };

    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    window.addEventListener("resize", resize);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", resize);
    };
  }, [redrawAll, syncCanvasSize]);

  const getCanvasPoint = useCallback((event: PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    const rect = canvas.getBoundingClientRect();
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    };
  }, []);

  const beginDraw = useCallback((event: PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    event.preventDefault();
    syncCanvasSize();
    isDrawingRef.current = true;
    pendingResizeRef.current = false;
    try {
      canvas.setPointerCapture(event.pointerId);
    } catch { /* ignore */ }

    const point = getCanvasPoint(event);
    const stroke: SignaturePoint[] = [point];
    currentStrokeRef.current = stroke;
    strokesRef.current.push(stroke);
    hasSignatureRef.current = true;
    if (signatureError) {
      setSignatureError("");
    }
    const ctx = canvas.getContext("2d");
    if (ctx) {
      prepareContext(ctx);
      ctx.beginPath();
      ctx.arc(point.x, point.y, 1.5, 0, Math.PI * 2);
      ctx.fill();
    }
  }, [getCanvasPoint, prepareContext, signatureError, syncCanvasSize]);

  const draw = useCallback((event: PointerEvent<HTMLCanvasElement>) => {
    const stroke = currentStrokeRef.current;
    if (!stroke) return;
    event.preventDefault();
    const previous = stroke[stroke.length - 1];
    const next = getCanvasPoint(event);
    stroke.push(next);
    const ctx = canvasRef.current?.getContext("2d");
    if (ctx && previous) {
      prepareContext(ctx);
      ctx.beginPath();
      ctx.moveTo(previous.x, previous.y);
      ctx.lineTo(next.x, next.y);
      ctx.stroke();
    }
  }, [getCanvasPoint, prepareContext]);

  const endDraw = useCallback((event: PointerEvent<HTMLCanvasElement>) => {
    currentStrokeRef.current = null;
    const wasDrawing = isDrawingRef.current;
    isDrawingRef.current = false;
    try {
      if (event.currentTarget.hasPointerCapture(event.pointerId)) {
        event.currentTarget.releasePointerCapture(event.pointerId);
      }
    } catch { /* ignore */ }
    if (wasDrawing && pendingResizeRef.current) {
      pendingResizeRef.current = false;
      syncCanvasSize();
      redrawAll();
    }
  }, [redrawAll, syncCanvasSize]);

  const handlePointerLeave = useCallback((event: PointerEvent<HTMLCanvasElement>) => {
    try {
      if (event.currentTarget.hasPointerCapture(event.pointerId)) return;
    } catch { /* ignore */ }
    endDraw(event);
  }, [endDraw]);

  const clearSignature = useCallback(() => {
    strokesRef.current = [];
    currentStrokeRef.current = null;
    hasSignatureRef.current = false;
    isDrawingRef.current = false;
    pendingResizeRef.current = false;
    syncCanvasSize();
    redrawAll();
    setSignatureError("");
  }, [redrawAll, syncCanvasSize]);

  const submit = useCallback((event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError("");
    if (!values.signDate || !values.method || !values.signerName.trim()) {
      setFormError("请填写签订日期、签订方式和签署人");
      return;
    }
    const hasSignature = hasSignatureRef.current && strokesRef.current.some((stroke) => stroke.length > 0);
    if (!hasSignature) {
      setSignatureError("请完成电子签名");
      return;
    }
    syncCanvasSize();
    redrawAll();
    const signatureDataUrl = canvasRef.current?.toDataURL("image/png") || "";
    setLoading(true);
    window.setTimeout(() => {
      onSubmit({ ...values, signerName: values.signerName.trim(), signatureDataUrl });
      setLoading(false);
    }, 500);
  }, [values, onSubmit, redrawAll, syncCanvasSize]);

  return (
    <form className="grid gap-6" onSubmit={submit}>
      {formError ? <div className="rounded-card border border-error bg-white px-3 py-2 text-body text-error">{formError}</div> : null}
      <div className="grid gap-6 md:grid-cols-2">
        <Field label="签订日期" required>
          <input className={inputClass} type="date" value={values.signDate} onChange={(event) => setValues({ ...values, signDate: event.target.value })} />
        </Field>
        <Field label="签订方式" required>
          <select className={selectClass} value={values.method} onChange={(event) => setValues({ ...values, method: event.target.value })}>
            <option value="电子签章">电子签章</option>
            <option value="线下盖章">线下盖章</option>
            <option value="双方邮寄">双方邮寄</option>
          </select>
        </Field>
      </div>
      <Field label="签署人" required>
        <input className={inputClass} value={values.signerName} onChange={(event) => setValues({ ...values, signerName: event.target.value })} />
      </Field>
      <Field label="电子签名" error={signatureError} required>
        <div className="rounded-card border border-line bg-section p-3">
          <canvas
            ref={canvasRef}
            className="h-48 w-full touch-none rounded-input border border-line bg-white"
            style={{ touchAction: "none" }}
            onPointerDown={beginDraw}
            onPointerMove={draw}
            onPointerUp={endDraw}
            onPointerCancel={endDraw}
            onPointerLeave={handlePointerLeave}
            onLostPointerCapture={endDraw}
          />
          <div className="mt-2 flex justify-end">
            <Button type="button" variant="secondary" className="min-h-8 px-3" icon={<RotateCcw className="h-4 w-4" />} onClick={clearSignature}>
              清空签名
            </Button>
          </div>
        </div>
      </Field>
      <Field label="备注">
        <textarea className={textareaClass} value={values.remark} onChange={(event) => setValues({ ...values, remark: event.target.value })} />
      </Field>
      <div className="flex justify-end">
        <Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">完成电子签名</Button>
      </div>
    </form>
  );
}
