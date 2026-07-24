create table account_external_identity (
    id varchar(64) primary key,
    account_id varchar(64) not null references user_account(id) on delete cascade,
    issuer varchar(512) not null,
    subject varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_account_external_identity_account unique (account_id),
    constraint uk_account_external_identity_issuer_subject unique (issuer, subject)
);

create index idx_account_external_identity_issuer_subject
    on account_external_identity(issuer, subject);
