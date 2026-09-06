import { RotateCcw, Save } from "lucide-react";
import { type PointerEvent, useCallback, useRef, useState } from "react";
import { Button } from "./Button";
import { Field, inputClass, selectClass, textareaClass } from "./Form";

type ElectronicSignValues = {
  signDate: string;
  method: string;
  signerName: string;
  remark?: string;
  signatureDataUrl: string;
};

type SignaturePoint = { x: number; y: number };
type SignatureStroke = SignaturePoint[];

const VIEW_BOX_WIDTH = 1000;
const VIEW_BOX_HEIGHT = 240;
const STROKE_WIDTH = 6;
const DEFAULT_STORAGE_KEY = "contract-electronic-signature-draft";

const formatPoint = (value: number) => Number(value.toFixed(2));

const strokeToPath = (stroke: SignatureStroke) => {
  if (!stroke.length) return "";
  const [first, ...rest] = stroke;
  if (!rest.length) {
    return `M ${formatPoint(first.x)} ${formatPoint(first.y)} L ${formatPoint(first.x + 0.1)} ${formatPoint(first.y)}`;
  }
  return [
    `M ${formatPoint(first.x)} ${formatPoint(first.y)}`,
    ...rest.map((point) => `L ${formatPoint(point.x)} ${formatPoint(point.y)}`)
  ].join(" ");
};

const createSignatureDataUrl = (strokes: SignatureStroke[]) => new Promise<string>((resolve, reject) => {
  const paths = strokes
    .filter((stroke) => stroke.length > 0)
    .map((stroke) => `<path d="${strokeToPath(stroke)}" fill="none" stroke="#111827" stroke-width="${STROKE_WIDTH}" stroke-linecap="round" stroke-linejoin="round" />`)
    .join("");
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${VIEW_BOX_WIDTH} ${VIEW_BOX_HEIGHT}" width="${VIEW_BOX_WIDTH}" height="${VIEW_BOX_HEIGHT}"><rect width="100%" height="100%" fill="#ffffff" />${paths}</svg>`;
  const image = new Image();
  image.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = VIEW_BOX_WIDTH;
    canvas.height = VIEW_BOX_HEIGHT;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      reject(new Error("无法生成电子签名图片"));
      return;
    }
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
    resolve(canvas.toDataURL("image/png"));
  };
  image.onerror = () => reject(new Error("无法生成电子签名图片"));
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
});

const readStoredStrokes = (storageKey: string): SignatureStroke[] => {
  try {
    const value = window.sessionStorage.getItem(storageKey);
    if (!value) return [];
    const parsed = JSON.parse(value) as SignatureStroke[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const storeStrokes = (storageKey: string, strokes: SignatureStroke[]) => {
  try {
    if (strokes.length) {
      window.sessionStorage.setItem(storageKey, JSON.stringify(strokes));
    } else {
      window.sessionStorage.removeItem(storageKey);
    }
  } catch { /* ignore */ }
};

export function ElectronicSignForm({
  onSubmit,
  storageKey = DEFAULT_STORAGE_KEY
}: {
  onSubmit: (values: ElectronicSignValues) => void | Promise<void>;
  storageKey?: string;
}) {
  const [loading, setLoading] = useState(false);
  const [formError, setFormError] = useState("");
  const [signatureError, setSignatureError] = useState("");
  const [strokes, setStrokes] = useState<SignatureStroke[]>(() => readStoredStrokes(storageKey));
  const [values, setValues] = useState({
    signDate: new Date().toISOString().slice(0, 10),
    method: "电子签章",
    signerName: "",
    remark: ""
  });

  const signatureRef = useRef<SVGSVGElement | null>(null);
  const activePointerIdRef = useRef<number | null>(null);
  const currentStrokeRef = useRef<SignatureStroke | null>(null);

  const getPoint = useCallback((event: PointerEvent<SVGSVGElement>) => {
    const signature = signatureRef.current;
    if (!signature) return { x: 0, y: 0 };
    const rect = signature.getBoundingClientRect();
    const width = Math.max(1, rect.width);
    const height = Math.max(1, rect.height);
    return {
      x: Math.min(VIEW_BOX_WIDTH, Math.max(0, ((event.clientX - rect.left) / width) * VIEW_BOX_WIDTH)),
      y: Math.min(VIEW_BOX_HEIGHT, Math.max(0, ((event.clientY - rect.top) / height) * VIEW_BOX_HEIGHT))
    };
  }, []);

  const beginDraw = useCallback((event: PointerEvent<SVGSVGElement>) => {
    const signature = signatureRef.current;
    if (!signature) return;
    event.preventDefault();
    activePointerIdRef.current = event.pointerId;
    try {
      signature.setPointerCapture(event.pointerId);
    } catch { /* ignore */ }

    const stroke: SignatureStroke = [getPoint(event)];
    currentStrokeRef.current = stroke;
    setStrokes((current) => {
      const next = [...current, stroke];
      storeStrokes(storageKey, next);
      return next;
    });
    setSignatureError("");
  }, [getPoint, storageKey]);

  const draw = useCallback((event: PointerEvent<SVGSVGElement>) => {
    if (activePointerIdRef.current !== event.pointerId) return;
    const stroke = currentStrokeRef.current;
    if (!stroke) return;
    event.preventDefault();

    const nextStroke = [...stroke, getPoint(event)];
    currentStrokeRef.current = nextStroke;
    setStrokes((current) => {
      const next = current.length ? [...current.slice(0, -1), nextStroke] : [nextStroke];
      storeStrokes(storageKey, next);
      return next;
    });
  }, [getPoint, storageKey]);

  const endDraw = useCallback((event: PointerEvent<SVGSVGElement>) => {
    if (activePointerIdRef.current !== event.pointerId) return;
    activePointerIdRef.current = null;
    currentStrokeRef.current = null;
  }, []);

  const clearSignature = useCallback(() => {
    activePointerIdRef.current = null;
    currentStrokeRef.current = null;
    setStrokes([]);
    storeStrokes(storageKey, []);
    setSignatureError("");
  }, [storageKey]);

  const submit = useCallback(async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError("");
    if (!values.signDate || !values.method || !values.signerName.trim()) {
      setFormError("请填写签订日期、签订方式和签署人");
      return;
    }
    const signatureStrokes = strokes.filter((stroke) => stroke.length > 0);
    if (!signatureStrokes.length) {
      setSignatureError("请完成电子签名");
      return;
    }

    setLoading(true);
    try {
      const signatureDataUrl = await createSignatureDataUrl(signatureStrokes);
      await onSubmit({ ...values, signerName: values.signerName.trim(), signatureDataUrl });
      storeStrokes(storageKey, []);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "电子签名生成失败");
    } finally {
      setLoading(false);
    }
  }, [onSubmit, storageKey, strokes, values]);

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
      <div className="block">
        <span className="mb-2 block text-body font-medium text-text">
          电子签名
          <span className="text-error"> *</span>
        </span>
        <div className="rounded-card border border-line bg-section p-3">
          <svg
            ref={signatureRef}
            data-signature-engine="svg-session-v3"
            className="h-48 w-full touch-none select-none rounded-input border border-line bg-white"
            viewBox={`0 0 ${VIEW_BOX_WIDTH} ${VIEW_BOX_HEIGHT}`}
            preserveAspectRatio="none"
            role="img"
            aria-label="电子签名"
            onPointerDown={beginDraw}
            onPointerMove={draw}
            onPointerUp={endDraw}
            onPointerCancel={endDraw}
            onContextMenu={(event) => event.preventDefault()}
          >
            <rect width="100%" height="100%" fill="#ffffff" />
            {strokes.map((stroke, index) => (
              <path
                key={index}
                d={strokeToPath(stroke)}
                fill="none"
                stroke="#111827"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={STROKE_WIDTH}
              />
            ))}
          </svg>
          <div className="mt-2 flex justify-end">
            <Button type="button" variant="secondary" className="min-h-8 px-3" icon={<RotateCcw className="h-4 w-4" />} onClick={clearSignature}>
              清空签名
            </Button>
          </div>
        </div>
        {signatureError ? <span className="mt-1 block text-note text-error">{signatureError}</span> : null}
      </div>
      <Field label="备注">
        <textarea className={textareaClass} value={values.remark} onChange={(event) => setValues({ ...values, remark: event.target.value })} />
      </Field>
      <div className="flex justify-end">
        <Button loading={loading} icon={<Save className="h-[18px] w-[18px]" />} type="submit">完成电子签名</Button>
      </div>
    </form>
  );
}
