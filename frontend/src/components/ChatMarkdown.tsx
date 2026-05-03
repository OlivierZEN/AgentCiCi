import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";

type Props = { content: string; busy?: boolean };

export default function ChatMarkdown({ content, busy }: Props) {
  const trimmed = content.trim();
  if (!trimmed) {
    if (busy) {
      return (
        <span className="bubble-thinking" role="status" aria-label="正在生成回复">
          <span className="bubble-thinking__dot" aria-hidden="true" />
          <span className="bubble-thinking__dot" aria-hidden="true" />
          <span className="bubble-thinking__dot" aria-hidden="true" />
        </span>
      );
    }
    return (
      <span className="bubble-empty">
        本次未返回文字内容。
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
