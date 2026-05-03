import type { CSSProperties } from "react";

type AvatarViewProps = {
  src?: string | null;
  fallback: string;
  className: string;
  style?: CSSProperties;
  alt: string;
};

export default function AvatarView({ src, fallback, className, style, alt }: AvatarViewProps) {
  const imageSrc = (src ?? "").trim();
  return (
    <div className={className} style={style}>
      {imageSrc ? (
        <img
          src={imageSrc}
          alt={alt}
          style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "inherit" }}
        />
      ) : (
        fallback
      )}
    </div>
  );
}
