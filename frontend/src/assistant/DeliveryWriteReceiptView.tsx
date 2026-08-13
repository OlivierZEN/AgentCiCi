import { AlertCircle, CheckCircle2 } from "lucide-react";
import type { DeliveryWriteReceipt } from "./deliveryWriteReceipt";

const OBJECT_LABELS: Record<string, string> = {
  dev_project: "研发项目",
  dev_requirement: "需求",
  dev_task: "开发任务",
  dev_defect: "缺陷",
};

export function DeliveryWriteReceiptView({ receipt }: { receipt: DeliveryWriteReceipt }) {
  const succeeded = receipt.status === "SUCCESS";
  return (
    <section
      className={`cici-delivery-receipt ${succeeded ? "is-success" : "is-failure"}`}
      aria-label={succeeded ? "Semattice 写入成功回执" : "Semattice 写入失败回执"}
      role="status"
    >
      <header className="cici-delivery-receipt__header">
        {succeeded ? <CheckCircle2 aria-hidden="true" /> : <AlertCircle aria-hidden="true" />}
        <strong>{succeeded ? "已核验写入" : "未确认写入"}</strong>
        <span>Semattice 实时回执</span>
      </header>
      {succeeded ? (
        <dl className="cici-delivery-receipt__facts">
          <div><dt>对象</dt><dd>{OBJECT_LABELS[receipt.objectApiName ?? ""] ?? receipt.objectApiName}</dd></div>
          <div><dt>记录</dt><dd>{receipt.subject}{receipt.code ? `（${receipt.code}）` : ""}</dd></div>
          <div><dt>记录 ID</dt><dd className="is-mono">{receipt.recordId}</dd></div>
          <div><dt>revision</dt><dd className="is-mono">{receipt.revision}</dd></div>
          <div className="is-wide"><dt>关联号</dt><dd className="is-mono">{receipt.correlationId}</dd></div>
          {receipt.contentDigest ? (
            <div className="is-wide"><dt>内容摘要</dt><dd className="is-mono">{receipt.contentDigest}</dd></div>
          ) : null}
        </dl>
      ) : (
        <p className="cici-delivery-receipt__message">{receipt.message}</p>
      )}
    </section>
  );
}
