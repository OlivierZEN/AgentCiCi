create table if not exists semattice_provisioning_binding (
    reservation_id varchar(64) primary key,
    org_id varchar(64) not null unique references org(id),
    idempotency_key varchar(128) not null unique,
    state varchar(32) not null,
    semattice_tenant_id varchar(64),
    semattice_operation_id varchar(128),
    failure_code varchar(64),
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint ck_semattice_provisioning_binding_state check (state in ('RESERVED', 'PROVISIONED', 'FAILED'))
);

create index if not exists idx_semattice_provisioning_binding_state_updated
    on semattice_provisioning_binding(state, updated_at desc);
