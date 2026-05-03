export const AVATAR_UPLOAD_MAX_BYTES = 5 * 1024 * 1024;

export function getDisplayInitial(value: string, fallback = "?") {
  const text = (value ?? "").trim();
  if (!text) return fallback;
  return text.slice(0, 2);
}

export function validateAvatarFile(file: File) {
  const mimeType = (file.type || "").toLowerCase();
  if (!(mimeType.includes("png") || mimeType.includes("jpeg") || mimeType.includes("jpg") || mimeType.includes("webp"))) {
    throw new Error("请选择 JPG、PNG 或 WebP 图片");
  }
  if (file.size > AVATAR_UPLOAD_MAX_BYTES) {
    throw new Error("图片过大，请选择 5MB 以内的图片");
  }
}

export async function readAvatarFileAsDataUrl(file: File): Promise<string> {
  validateAvatarFile(file);
  return await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function loadImage(dataUrl: string): Promise<HTMLImageElement> {
  return await new Promise<HTMLImageElement>((resolve, reject) => {
    const node = new Image();
    node.onload = () => resolve(node);
    node.onerror = reject;
    node.src = dataUrl;
  });
}

export async function cropAvatarDataUrl(
  sourceDataUrl: string,
  crop: { sx: number; sy: number; side: number },
  outputSize = 256,
): Promise<string> {
  const img = await loadImage(sourceDataUrl);
  const side = Math.max(1, crop.side);
  const maxSx = Math.max(0, img.width - side);
  const maxSy = Math.max(0, img.height - side);
  const sx = Math.max(0, Math.min(crop.sx, maxSx));
  const sy = Math.max(0, Math.min(crop.sy, maxSy));

  const canvas = document.createElement("canvas");
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("头像处理失败，请稍后重试");
  }
  ctx.clearRect(0, 0, outputSize, outputSize);
  ctx.save();
  ctx.beginPath();
  ctx.arc(outputSize / 2, outputSize / 2, outputSize / 2, 0, Math.PI * 2);
  ctx.closePath();
  ctx.clip();
  ctx.drawImage(img, sx, sy, side, side, 0, 0, outputSize, outputSize);
  ctx.restore();

  return canvas.toDataURL("image/webp", 0.82);
}

export async function processAvatarFile(file: File): Promise<string> {
  const dataUrl = await readAvatarFileAsDataUrl(file);
  const img = await loadImage(dataUrl);
  const side = Math.min(img.width, img.height);
  const sx = Math.floor((img.width - side) / 2);
  const sy = Math.floor((img.height - side) / 2);
  return await cropAvatarDataUrl(dataUrl, { sx, sy, side }, 256);
}
