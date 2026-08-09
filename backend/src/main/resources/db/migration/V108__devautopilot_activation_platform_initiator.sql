-- 平台负责应用生命周期，不代替租户指定某位人类负责人。
ALTER TABLE tenant_application_activation
    ALTER COLUMN created_by_member_id DROP NOT NULL;
