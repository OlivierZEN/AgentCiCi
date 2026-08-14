import { describe, expect, it } from "vitest";
import {
  MAX_CHAT_IMAGE_BYTES,
  composerCanSubmit,
  validateChatImages,
} from "./chatAttachments";

const image = (name: string, type: string, size: number) => ({ name, type, size });

describe("chatAttachments", () => {
  it("accepts supported images within the remaining conversation slots", () => {
    const result = validateChatImages([
      image("one.png", "image/png", 128),
      image("two.jpg", "image/jpeg", 256),
    ], 8);

    expect(result.accepted.map((item) => item.name)).toEqual(["one.png", "two.jpg"]);
    expect(result.rejected).toEqual([]);
  });

  it("rejects oversized unsupported and eleventh images with explicit reasons", () => {
    const result = validateChatImages([
      image("too-large.webp", "image/webp", MAX_CHAT_IMAGE_BYTES + 1),
      image("document.pdf", "application/pdf", 200),
      image("last.png", "image/png", 200),
    ], 10);

    expect(result.accepted).toEqual([]);
    expect(result.rejected.map((item) => item.reason)).toEqual([
      "too-large.webp 超过 20MB",
      "document.pdf 不是支持的 PNG、JPG 或 WebP 图片",
      "每个会话最多上传 10 张图片",
    ]);
  });

  it("allows image-only send only after every queued image is ready", () => {
    expect(composerCanSubmit("", ["ready", "ready"])).toBe(true);
    expect(composerCanSubmit("说明一下", [])).toBe(true);
    expect(composerCanSubmit("", ["uploading"])).toBe(false);
    expect(composerCanSubmit("说明一下", ["ready", "error"])).toBe(false);
  });
});
