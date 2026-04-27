import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";

type Props = { content: string; busy?: boolean };

export default function ChatMarkdown({ content, busy }: Props) {
  const trimmed = content.trim();
  if (!trimmed) {
    return (
      <span className="bubble-thinking">
        {busy ? "CiCi 正在组织语言…" : "本次未返回文字内容。"}
      </span>
    );
  }
  return (
    <div className="bubble-markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
