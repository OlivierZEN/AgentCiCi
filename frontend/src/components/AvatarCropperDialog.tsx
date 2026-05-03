import { type PointerEventHandler, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { cropAvatarDataUrl } from "../shared/avatar";

type AvatarCropperDialogProps = {
  open: boolean;
  sourceDataUrl: string;
  title?: string;
  onCancel: () => void;
  onConfirm: (avatarBase64: string) => void | Promise<void>;
};

const FRAME_SIZE = 280;
const MIN_ZOOM = 1;
const MAX_ZOOM = 4;

type Offset = { x: number; y: number };
type DragState = {
  pointerId: number;
  startX: number;
  startY: number;
  originOffset: Offset;
};

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(value, max));
}

export default function AvatarCropperDialog({
  open,
  sourceDataUrl,
  title = "裁剪头像",
  onCancel,
  onConfirm,
}: AvatarCropperDialogProps) {
  const [imageSize, setImageSize] = useState<{ width: number; height: number } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState<Offset>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const dragRef = useRef<DragState | null>(null);

  useEffect(() => {
    if (!open || !sourceDataUrl) {
      return;
    }
    let cancelled = false;
    setError("");
    setZoom(1);
    setOffset({ x: 0, y: 0 });
    const image = new Image();
    image.onload = () => {
      if (cancelled) return;
      setImageSize({ width: image.width, height: image.height });
    };
    image.onerror = () => {
      if (cancelled) return;
      setImageSize(null);
      setError("图片读取失败，请重新选择");
    };
    image.src = sourceDataUrl;
    return () => {
      cancelled = true;
    };
  }, [open, sourceDataUrl]);

  const baseScale = useMemo(() => {
    if (!imageSize) return 1;
    return FRAME_SIZE / Math.min(imageSize.width, imageSize.height);
  }, [imageSize]);
  const displayScale = useMemo(() => baseScale * zoom, [baseScale, zoom]);
  const displaySize = useMemo(() => {
    if (!imageSize) {
      return { width: FRAME_SIZE, height: FRAME_SIZE };
    }
    return {
      width: imageSize.width * displayScale,
      height: imageSize.height * displayScale,
    };
  }, [imageSize, displayScale]);

  const clampOffset = useCallback(
    (next: Offset, zoomValue: number) => {
      if (!imageSize) return { x: 0, y: 0 };
      const scaledWidth = imageSize.width * baseScale * zoomValue;
      const scaledHeight = imageSize.height * baseScale * zoomValue;
      const maxX = Math.max(0, (scaledWidth - FRAME_SIZE) / 2);
      const maxY = Math.max(0, (scaledHeight - FRAME_SIZE) / 2);
      return {
        x: clamp(next.x, -maxX, maxX),
        y: clamp(next.y, -maxY, maxY),
      };
    },
    [imageSize, baseScale],
  );

  const onZoomChange = useCallback(
    (value: number) => {
      const nextZoom = clamp(value, MIN_ZOOM, MAX_ZOOM);
      setZoom(nextZoom);
      setOffset((prev) => clampOffset(prev, nextZoom));
    },
    [clampOffset],
  );

  const onPointerDown: PointerEventHandler<HTMLDivElement> = useCallback(
    (event) => {
      if (!imageSize || submitting) return;
      event.preventDefault();
      event.currentTarget.setPointerCapture(event.pointerId);
      dragRef.current = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        originOffset: offset,
      };
      setIsDragging(true);
    },
    [imageSize, offset, submitting],
  );

  const onPointerMove: PointerEventHandler<HTMLDivElement> = useCallback(
    (event) => {
      const drag = dragRef.current;
      if (!drag || drag.pointerId !== event.pointerId || submitting) return;
      const next = {
        x: drag.originOffset.x + (event.clientX - drag.startX),
        y: drag.originOffset.y + (event.clientY - drag.startY),
      };
      setOffset(clampOffset(next, zoom));
    },
    [clampOffset, zoom, submitting],
  );

  const endPointerDrag: PointerEventHandler<HTMLDivElement> = useCallback((event) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    dragRef.current = null;
    setIsDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }, []);

  const handleReset = useCallback(() => {
    setZoom(1);
    setOffset({ x: 0, y: 0 });
    setError("");
  }, []);

  const handleConfirm = useCallback(async () => {
    if (!imageSize || submitting) return;
    const imageLeft = FRAME_SIZE / 2 - displaySize.width / 2 + offset.x;
    const imageTop = FRAME_SIZE / 2 - displaySize.height / 2 + offset.y;
    const cropSide = FRAME_SIZE / displayScale;
    const sx = (0 - imageLeft) / displayScale;
    const sy = (0 - imageTop) / displayScale;

    setSubmitting(true);
    setError("");
    try {
      const avatar = await cropAvatarDataUrl(sourceDataUrl, { sx, sy, side: cropSide }, 256);
      await onConfirm(avatar);
    } catch (err) {
      setError(err instanceof Error ? err.message : "头像处理失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }, [imageSize, submitting, offset, sourceDataUrl, onConfirm, displayScale, displaySize.height, displaySize.width]);

  if (!open) return null;

  return (
    <div className="cici-avatar-cropper-backdrop" onClick={() => (!submitting ? onCancel() : undefined)}>
      <section className="cici-avatar-cropper" onClick={(event) => event.stopPropagation()}>
        <header className="cici-avatar-cropper__header">
          <h4>{title}</h4>
          <button type="button" className="cici-btn cici-btn--ghost" onClick={onCancel} disabled={submitting}>
            取消
          </button>
        </header>

        <div className="cici-avatar-cropper__canvas-wrap">
          <div
            className={`cici-avatar-cropper__canvas${isDragging ? " is-dragging" : ""}`}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={endPointerDrag}
            onPointerCancel={endPointerDrag}
          >
            {sourceDataUrl ? (
              <img
                src={sourceDataUrl}
                alt="头像裁剪源图"
                className="cici-avatar-cropper__image"
                style={{
                  width: `${displaySize.width}px`,
                  height: `${displaySize.height}px`,
                  left: `${FRAME_SIZE / 2 - displaySize.width / 2 + offset.x}px`,
                  top: `${FRAME_SIZE / 2 - displaySize.height / 2 + offset.y}px`,
                }}
                draggable={false}
              />
            ) : null}
            <div className="cici-avatar-cropper__mask" />
          </div>
        </div>

        <div className="cici-avatar-cropper__controls">
          <label htmlFor="avatar-zoom" className="cici-avatar-cropper__zoom-label">
            缩放
          </label>
          <input
            id="avatar-zoom"
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.01}
            value={zoom}
            onChange={(event) => onZoomChange(Number(event.target.value))}
            disabled={submitting || !imageSize}
          />
          <span className="cici-avatar-cropper__zoom-value">{Math.round(zoom * 100)}%</span>
        </div>

        {error ? <div className="cici-modal__notice">{error}</div> : null}

        <footer className="cici-avatar-cropper__footer">
          <button type="button" className="cici-btn cici-btn--ghost" onClick={handleReset} disabled={submitting}>
            重置取景
          </button>
          <button type="button" className="cici-btn cici-btn--primary" onClick={() => void handleConfirm()} disabled={submitting || !imageSize}>
            {submitting ? "处理中…" : "应用裁剪"}
          </button>
        </footer>
      </section>
    </div>
  );
}
