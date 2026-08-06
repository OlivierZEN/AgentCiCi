# Keycloak 生产人工运维交接

更新时间：2026-08-05

## 目标与边界

本交接用于让人工运维人员接管 AgentCiCi 统一身份中心（SSO）的日常检查、故障诊断和数据库凭据轮换。

- 本文不保存密码、私钥、Bearer Token、管理员密码或任何可复用凭据。
- 凭据只保存在 SSO 主机的 root 专用文件中，禁止提交到 Git、工单、聊天工具或截图。
- 当前自动化使用的 SSH 私钥不属于交接制品；人工运维人员应通过阿里云控制台和自己的 SSH 公钥取得服务器访问权。

## 已核实的生产拓扑

| 项目 | 事实 |
| --- | --- |
| SSO 主机 | `115.29.222.70` |
| Keycloak | `26.7.0`，由 `keycloak.service` 管理 |
| Keycloak 运行用户 | `keycloak` |
| Keycloak 目录 | `/opt/keycloak/current` |
| Keycloak 配置 | `/etc/keycloak/keycloak.conf`，权限 `0640 root:keycloak` |
| PostgreSQL | `16.13`，由 `postgresql-16.service` 管理 |
| PostgreSQL 监听范围 | `127.0.0.1:5432` 与 `::1:5432`，未对公网开放 |
| 当前服务状态 | `keycloak.service`、`postgresql-16.service` 均为 `active` |

Keycloak 通过本机 PostgreSQL 连接运行；不要为方便远程维护直接开放 `5432`。人工诊断应通过 SSH 登录到 SSO 主机后本地执行，或使用受控 SSH 隧道。

## 凭据交接位置

在 SSO 主机上，仅 root 可读取：

```text
/root/agentcici-ops-handover/keycloak-postgres.env
```

该文件权限为 `0600 root:root`，包含以下连接变量：

- `KEYCLOAK_DB_URL`
- `KEYCLOAK_DB_USERNAME`
- `KEYCLOAK_DB_PASSWORD`
- `KEYCLOAK_PSQL_URL`

同目录还包含：

```text
/root/agentcici-ops-handover/README.md
/root/agentcici-ops-handover/verify-keycloak-postgres.sh
```

验证脚本已经在创建时以真实配置完成一次非交互式连接验证；它只输出数据库连接的非敏感结果。

## 人工运维日常操作

登录 SSO 主机后执行：

```bash
sudo -i
systemctl status keycloak postgresql-16 --no-pager
journalctl -u keycloak -n 200 --no-pager
/root/agentcici-ops-handover/verify-keycloak-postgres.sh
```

修改 Keycloak 本身的配置后，先做语法/备份检查，再执行：

```bash
systemctl restart keycloak
systemctl is-active keycloak
```

任何数据库密码轮换必须按下列顺序完成：

1. 备份 PostgreSQL 和 `/etc/keycloak/keycloak.conf`。
2. 变更 PostgreSQL 中 Keycloak 专用角色的密码。
3. 原子更新 `/etc/keycloak/keycloak.conf` 的 `db-password` 与 root 专用交接文件。
4. 重启 Keycloak，并运行 `verify-keycloak-postgres.sh` 和服务健康检查。
5. 确认无异常后，安全销毁临时文件和终端历史中的敏感输入。

## 已知安全约束

- `/etc/keycloak/bootstrap-admin.env` 也含敏感管理信息，当前权限为 `0640 root:keycloak`；不得复制或显示其内容。
- Keycloak 的数据库账号仅应服务于 Keycloak；人工排障应优先只读，业务数据更正必须独立评审并留下备份与审计记录。
- 人工接管完成后，应立即确认至少两名受控运维人员拥有独立 SSH 公钥和云控制台权限，并移除不再需要的自动化访问授权。
