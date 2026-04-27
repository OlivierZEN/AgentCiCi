import React from "react";

type State = {
  hasError: boolean;
  message: string;
  stack: string;
};

type Props = {
  children: React.ReactNode;
};

export default class AppErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, message: "", stack: "" };
  }

  static getDerivedStateFromError(error: unknown): State {
    const e = error instanceof Error ? error : new Error(String(error));
    return {
      hasError: true,
      message: e.message || "Unknown render error",
      stack: e.stack || "",
    };
  }

  componentDidCatch(error: unknown, errorInfo: React.ErrorInfo): void {
    const e = error instanceof Error ? error : new Error(String(error));
    // Keep console output for debugging in DevTools.
    console.error("[AppErrorBoundary]", e, errorInfo);
    this.setState((prev) => ({
      ...prev,
      stack: `${prev.stack || ""}\n${errorInfo.componentStack || ""}`.trim(),
    }));
  }

  private reloadPage = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }
    return (
      <main style={styles.wrap}>
        <section style={styles.card}>
          <h1 style={styles.title}>页面加载失败</h1>
          <p style={styles.text}>应用捕获到前端运行时异常，已阻止白屏。请复制错误信息给开发人员。</p>
          <div style={styles.block}>
            <div style={styles.label}>错误信息</div>
            <pre style={styles.pre}>{this.state.message || "Unknown error"}</pre>
          </div>
          {this.state.stack ? (
            <div style={styles.block}>
              <div style={styles.label}>堆栈信息</div>
              <pre style={styles.pre}>{this.state.stack}</pre>
            </div>
          ) : null}
          <button type="button" style={styles.btn} onClick={this.reloadPage}>
            刷新页面
          </button>
        </section>
      </main>
    );
  }
}

const styles: Record<string, React.CSSProperties> = {
  wrap: {
    minHeight: "100vh",
    margin: 0,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "linear-gradient(175deg, #faf8f5 0%, #efe9df 100%)",
    padding: "24px",
    fontFamily: '"Plus Jakarta Sans", "PingFang SC", sans-serif',
  },
  card: {
    width: "min(980px, 100%)",
    background: "rgba(255, 252, 247, 0.98)",
    border: "1px solid rgba(28, 25, 23, 0.09)",
    borderRadius: 16,
    boxShadow: "0 20px 48px rgba(28, 25, 23, 0.08)",
    padding: 22,
  },
  title: { margin: "0 0 10px", fontSize: 24, color: "#1c1917", fontFamily: '"Fraunces", Georgia, serif' },
  text: { margin: "0 0 12px", color: "#78716c", lineHeight: 1.5 },
  block: { marginTop: 10 },
  label: { fontSize: 13, color: "#44403c", marginBottom: 6, fontWeight: 600 },
  pre: {
    margin: 0,
    padding: 12,
    borderRadius: 10,
    border: "1px solid rgba(28, 25, 23, 0.08)",
    background: "#f7f2ea",
    color: "#1c1917",
    fontSize: 12,
    whiteSpace: "pre-wrap",
    overflowX: "auto",
    maxHeight: 280,
  },
  btn: {
    marginTop: 14,
    border: 0,
    borderRadius: 10,
    padding: "9px 14px",
    color: "#fff",
    background: "linear-gradient(135deg, #14b8a6, #0d9488)",
    cursor: "pointer",
    fontWeight: 600,
  },
};

