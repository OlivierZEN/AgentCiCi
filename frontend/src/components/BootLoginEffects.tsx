import { useEffect, useRef, useState } from "react";

type BootDemoScene = {
  id: string;
  label: string;
  userPrompt: string;
  thoughts: string[];
  answer: string;
  beforeSummary: string;
  beforePainPoints: string[];
};

const BOOT_DEMO_SCENES: BootDemoScene[] = [
  {
    id: "lead-routing",
    label: "销售线索自动分发",
    userPrompt:
      "当有新的销售线索进入时，请按我们的 ICP 进行评分，补充联系人对应的公司信息和意向数据，并将符合条件的线索连同调研简报一起，分配给合适的销售代表。",
    thoughts: ["正在制定执行方案…", "正在选择合适的工具…", "正在配置触发条件…", "正在整理执行指令…"],
    answer: "已完成。新的销售线索现在会自动完成筛选、信息补全，并分配给你的销售团队。",
    beforeSummary: "以前要把评分、补全、分配和通知拆成很多节点手工串起来。",
    beforePainPoints: ["评分规则分散在多个系统", "数据补全与路由链路长", "每次改规则都要重配流程"],
  },
];

function usePrefersReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return;
    }
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setPrefersReducedMotion(media.matches);
    update();
    media.addEventListener?.("change", update);
    return () => media.removeEventListener?.("change", update);
  }, []);

  return prefersReducedMotion;
}

/** Full-viewport Matrix-style digital rain. */
export function BootLoginDataStream() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let raf = 0;
    let w = 0;
    let h = 0;
    let columns: { y: number; speed: number }[] = [];
    let colWidth = 14;
    const CHAR_POOL = "0123456789";

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = canvas.getBoundingClientRect();
      w = Math.max(1, Math.floor(rect.width));
      h = Math.max(1, Math.floor(rect.height));
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      colWidth = Math.max(10, Math.floor(w / 60));
      const n = Math.ceil(w / colWidth) + 1;
      columns = Array.from({ length: n }, () => ({
        y: Math.random() * h,
        speed: 0.3 + Math.random() * 1.2,
      }));
    };

    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);

    let firstFrame = true;
    const frame = () => {
      if (firstFrame) {
        ctx.fillStyle = "#0a1628";
        ctx.fillRect(0, 0, w, h);
        firstFrame = false;
      } else {
        ctx.fillStyle = "rgba(10, 22, 40, 0.06)";
        ctx.fillRect(0, 0, w, h);
      }

      const fs = colWidth * 1.0;
      ctx.font = `bold ${fs}px ui-monospace, "SF Mono", Menlo, monospace`;

      for (let i = 0; i < columns.length; i++) {
        const x = i * colWidth;
        const col = columns[i];
        const ch = CHAR_POOL[Math.floor(Math.random() * CHAR_POOL.length)];

        // Head: bright green
        const headBrightness = 0.9 + Math.random() * 0.1;
        ctx.fillStyle = `rgba(0, 200, 50, ${headBrightness})`;
        ctx.fillText(ch, x, col.y);

        // Tail: dimmer green (one char above)
        ctx.fillStyle = `rgba(0, 160, 40, 0.35)`;
        const tailCh = CHAR_POOL[Math.floor(Math.random() * CHAR_POOL.length)];
        ctx.fillText(tailCh, x, col.y - fs);

        col.y += fs * col.speed;

        if (col.y > h + fs * 2) {
          col.y = -fs * (2 + Math.random() * 10);
          col.speed = 0.3 + Math.random() * 1.2;
        }
      }

      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, []);

  return <canvas ref={ref} className="boot-login__datastream" aria-hidden />;
}

/** Full-bleed HUD wireframes with animated stroke dash. */
export function BootLoginHudFrames() {
  return (
    <svg className="boot-login__hud-svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden>
      <defs>
        <linearGradient id="bootHudStroke" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00e5ff" stopOpacity="0.55" />
          <stop offset="50%" stopColor="#4fc3f7" stopOpacity="0.35" />
          <stop offset="100%" stopColor="#1a237e" stopOpacity="0.4" />
        </linearGradient>
        <filter id="bootHudGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="0.35" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <g filter="url(#bootHudGlow)" fill="none" stroke="url(#bootHudStroke)" strokeWidth="0.22" strokeLinecap="round">
        <rect x="3" y="4" width="24" height="32" rx="1.2" className="boot-hud-dash boot-hud-dash--slow" />
        <rect x="71" y="58" width="26" height="34" rx="1.2" className="boot-hud-dash boot-hud-dash--med" />
        <rect x="78" y="8" width="18" height="22" rx="0.8" className="boot-hud-dash boot-hud-dash--fast" />
        <path d="M 48 6 L 88 6 L 88 18 L 62 18 L 62 28 L 48 28 Z" className="boot-hud-dash boot-hud-dash--med" />
        <path d="M 6 68 L 28 68 L 28 92 L 6 92 Z" className="boot-hud-dash boot-hud-dash--slow" />
        <circle cx="50" cy="52" r="18" className="boot-hud-dash boot-hud-dash--ring" />
      </g>
    </svg>
  );
}

export function BootLoginConversationDemo() {
  const prefersReducedMotion = usePrefersReducedMotion();
  const [sceneIndex, setSceneIndex] = useState(0);
  const [playCycle, setPlayCycle] = useState(0);
  const [visibleThoughts, setVisibleThoughts] = useState(0);
  const [answerLength, setAnswerLength] = useState(0);

  useEffect(() => {
    const scene = BOOT_DEMO_SCENES[sceneIndex];
    if (!scene) return;

    if (prefersReducedMotion) {
      setVisibleThoughts(scene.thoughts.length);
      setAnswerLength(scene.answer.length);
      return;
    }

    setVisibleThoughts(0);
    setAnswerLength(0);

    const timeouts: number[] = [];
    let typingTimer = 0;

    scene.thoughts.forEach((_, index) => {
      const timer = window.setTimeout(() => {
        setVisibleThoughts(index + 1);
      }, 900 + index * 560);
      timeouts.push(timer);
    });

    const answerStart = 900 + scene.thoughts.length * 560 + 220;
    timeouts.push(
      window.setTimeout(() => {
        typingTimer = window.setInterval(() => {
          setAnswerLength((value) => {
            if (value >= scene.answer.length) {
              window.clearInterval(typingTimer);
              return value;
            }
            return value + 1;
          });
        }, 24);
      }, answerStart),
    );

    const nextSceneDelay = answerStart + scene.answer.length * 24 + 2200;
    timeouts.push(
      window.setTimeout(() => {
        setSceneIndex((value) => (value + 1) % BOOT_DEMO_SCENES.length);
        setPlayCycle((value) => value + 1);
      }, nextSceneDelay),
    );

    return () => {
      timeouts.forEach((timer) => window.clearTimeout(timer));
      if (typingTimer) {
        window.clearInterval(typingTimer);
      }
    };
  }, [playCycle, prefersReducedMotion, sceneIndex]);

  const scene = BOOT_DEMO_SCENES[sceneIndex];
  const typedAnswer = scene.answer.slice(0, answerLength);
  const reasoningComplete = visibleThoughts >= scene.thoughts.length;
  const assistantVisible = prefersReducedMotion || answerLength > 0;
  const showCursor = !prefersReducedMotion && assistantVisible && answerLength < scene.answer.length;

  return (
    <section className="boot-demo" aria-label="思思 登录前动态对话演示">
      <div className="boot-demo__compare">
        <section className="boot-demo__panel boot-demo__panel--before" aria-label="Before">
          <div className="boot-demo__panel-head">
            <span className="boot-demo__panel-kicker">Before</span>
            <strong>手工拼接工作流</strong>
          </div>
          <div className="boot-demo__thumb-shell" aria-hidden>
            <img className="boot-demo__thumb" src="/lead-workflow-thumb.jpg" alt="" decoding="async" />
          </div>
          <p className="boot-demo__before-copy">{scene.beforeSummary}</p>
          <ul className="boot-demo__before-list">
            {scene.beforePainPoints.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section className="boot-demo__panel boot-demo__panel--after" aria-label="After">
          <div className="boot-demo__panel-head">
            <span className="boot-demo__panel-kicker">After</span>
            <strong>一句话完成配置</strong>
          </div>

          <div className="boot-demo__chat">
            <div className="boot-demo__message boot-demo__message--user">
              <div className="boot-demo__avatar boot-demo__avatar--user" aria-hidden>
                <svg viewBox="0 0 24 24" focusable="false">
                  <path
                    d="M12 12.2a4.1 4.1 0 1 0 0-8.2 4.1 4.1 0 0 0 0 8.2Zm0 2.1c-4.24 0-7.68 2.36-7.68 5.27 0 .24.19.43.43.43h14.5c.24 0 .43-.19.43-.43 0-2.91-3.44-5.27-7.68-5.27Z"
                    fill="currentColor"
                  />
                </svg>
              </div>
              <article className="boot-demo__bubble boot-demo__bubble--user">
                <p>{scene.userPrompt}</p>
              </article>
            </div>

            <section className="boot-demo__reasoning" aria-label="思考摘要">
              <div className="boot-demo__reasoning-head">
                <span className="boot-demo__reasoning-label">思思 思考过程</span>
                <span className={`boot-demo__reasoning-status${reasoningComplete ? " is-done" : ""}`}>
                  <span className="boot-demo__reasoning-dot" />
                  {reasoningComplete ? "已思考 5 秒" : "思考中"}
                </span>
              </div>
              <ul className="boot-demo__reasoning-list">
                {scene.thoughts.map((item, index) => (
                  <li key={`${scene.id}-${item}`} className={`boot-demo__reasoning-item${index < visibleThoughts ? " is-visible" : ""}`}>
                    <span className="boot-demo__reasoning-bullet" />
                    <span className="boot-demo__reasoning-text">{item}</span>
                  </li>
                ))}
              </ul>
            </section>

            <article className={`boot-demo__bubble boot-demo__bubble--assistant${assistantVisible ? " is-visible" : ""}`}>
              <span className="boot-demo__role">思思 回答</span>
              <p>
                {typedAnswer}
                {showCursor ? <span className="boot-demo__cursor" aria-hidden /> : null}
              </p>
            </article>
          </div>
        </section>
      </div>

      <div className="boot-demo__footer">
        <span>{scene.label}</span>
        <span>登录后可进入真实对话与工具协作</span>
      </div>
    </section>
  );
}
