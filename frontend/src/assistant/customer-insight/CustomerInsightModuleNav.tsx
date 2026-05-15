import { useEffect, useMemo, useState } from "react";
import { groupSections, statusLabel, statusTone } from "./customerInsightSections";
import type { CustomerInsightSection } from "./customerInsightTypes";

type Props = {
  sections: CustomerInsightSection[];
  activeSectionCode: string;
  onSelect: (sectionCode: string) => void;
};

export function CustomerInsightModuleNav({ sections, activeSectionCode, onSelect }: Props) {
  const groups = useMemo(() => groupSections(sections), [sections]);
  const activeGroupCode = groups.find((group) =>
    (group.sections as CustomerInsightSection[]).some((section) => section.sectionCode === activeSectionCode),
  )?.code;
  const [openGroups, setOpenGroups] = useState<Set<string>>(() => new Set(activeGroupCode ? [activeGroupCode] : []));

  useEffect(() => {
    if (!activeGroupCode) return;
    setOpenGroups((current) => {
      if (current.has(activeGroupCode)) return current;
      const next = new Set(current);
      next.add(activeGroupCode);
      return next;
    });
  }, [activeGroupCode]);

  return (
    <nav className="cici-customer-insight__modules" aria-label="客户洞察模块">
      {groups.map((group) => {
        const open = openGroups.has(group.code);
        const groupActive = group.code === activeGroupCode;
        const groupId = `customer-insight-module-group-${group.code}`;
        return (
          <section key={group.code} className={`cici-customer-insight__module-group${open ? " is-open" : ""}${groupActive ? " is-active" : ""}`}>
            <button
              type="button"
              className="cici-customer-insight__module-group-title"
              onClick={() =>
                setOpenGroups((current) => {
                  const next = new Set(current);
                  if (next.has(group.code)) {
                    next.delete(group.code);
                  } else {
                    next.add(group.code);
                  }
                  return next;
                })
              }
              aria-expanded={open}
              aria-controls={groupId}
            >
              <span>{group.label}</span>
              <small aria-hidden>{open ? "⌃" : "⌄"}</small>
            </button>
            {open ? (
              <div id={groupId} className="cici-customer-insight__module-list">
                {(group.sections as CustomerInsightSection[]).map((section) => {
                  const active = section.sectionCode === activeSectionCode;
                  return (
                    <button
                      key={section.sectionCode}
                      type="button"
                      className={`cici-customer-insight__module${active ? " is-active" : ""}`}
                      onClick={() => onSelect(section.sectionCode)}
                      aria-current={active ? "true" : undefined}
                    >
                      <span>{section.title}</span>
                      <small className={`is-${statusTone(section.status)}`}>{statusLabel(section.status)}</small>
                    </button>
                  );
                })}
              </div>
            ) : null}
          </section>
        );
      })}
    </nav>
  );
}
