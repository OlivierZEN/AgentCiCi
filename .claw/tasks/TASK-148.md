---
kind: task-status
task_id: TASK-148
assignee: MANAGER-001
owner_role: project-manager
status: done
branch: codex/TASK-147-wecom-kf-connection-test
pr_url: n/a
spec_path: docs/specs/FEAT-063-production-domain-cutover.md
assignment_path: .claw/assignments/TASK-148.yaml
updated_at: 2026-06-01T07:33:00Z
updated_by: MANAGER-001
---

# TASK-148 Production Domain Cutover

## Scope

- Stop serving the current production hostnames `agentcici.com`, `www.agentcici.com`, and `autoservice.agentcici.com`.
- Serve the production frontend and API proxy surface on `onechat.agentcici.com` and `x.agentcici.com`.
- Update production Nginx configuration, release runbook smoke commands, deployment record, and in-product public examples that still point to old production hostnames.
- Record DNS and TLS certificate prerequisites for the cutover.

## Out Of Scope

- DNS-provider record changes and certificate issuance in this repository.
- Database, backend API, auth, billing, or product visual changes.
- Historical specs that mention old hostnames as past requirements or feedback evidence.
- Mobile-specific validation.

## Progress

- 2026-06-01T09:05:00Z: Task opened from the user's request to replace online environment domains.
- 2026-06-01T07:33:00Z: Production Nginx SSL config backed up, synced, syntax-tested, and hot-reloaded. New domains are serving; retired hostnames are no longer accepted by the production HTTPS app vhost.

## Changed Files

- `.claw/assignments/TASK-148.yaml`
- `.claw/tasks/TASK-148.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
- `docs/specs/FEAT-063-production-domain-cutover.md`
- `docs/production-release-runbook.md`
- `docs/deploy-runbook.md`
- `deploy/nginx.cici.ssl.conf`
- `frontend/src/help/helpContent.ts`
- `frontend/src/assistant/AssistantApp.tsx`

## Verification Target

- Task-scoped `dev-login.py` and `check-assignment.py` authorization pass.
- Static search confirms active deployment config and runbooks no longer use retired public hostnames.
- Nginx config parses structurally enough for local review; final `nginx -t` must run inside the production frontend container after sync.

## Verification 2026-06-01

- `dev-login.py` for `MANAGER-001` / `TASK-148` with representative deploy/docs/frontend/status files -> allowed.
- `check-assignment.py` for TASK-148 representative files -> allowed.
- `git diff --check` -> success.
- `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- Production backup: `/opt/cici/backups/20260601-153012-before-domain-cutover/nginx.cici.ssl.conf.before-domain-cutover`.
- Production sync: `deploy/nginx.cici.ssl.conf` copied to `/opt/cici/deploy/nginx.cici.ssl.conf`.
- Production `docker exec cici-frontend nginx -t` -> success.
- Production `docker exec cici-frontend nginx -s reload` -> success; `cici-frontend` remained healthy.
- Public smoke:
  - `http://onechat.agentcici.com/` -> `301` to HTTPS.
  - `http://x.agentcici.com/` -> `301` to HTTPS.
  - `https://onechat.agentcici.com/` -> `200`.
  - `https://x.agentcici.com/` -> `200`.
  - `https://onechat.agentcici.com/auth/me` without token -> backend JSON response `400`, proving API proxy reaches backend.
  - `https://x.agentcici.com/auth/me` without token -> backend JSON response `400`.
  - `https://agentcici.com/` -> empty reply from default HTTPS server, no longer served by app vhost.
  - `https://autoservice.agentcici.com/` -> empty reply from default HTTPS server, no longer served by app vhost.
  - `https://www.agentcici.com/` -> DNS no longer resolved from this workstation.
  - Server-local backend health `http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.

## Handoff

- DNS and TLS were sufficient for the observed public smoke after reload.
- Keep monitoring external clients that still call `agentcici.com`, `www.agentcici.com`, or `autoservice.agentcici.com`; they now need to move to `onechat.agentcici.com` or `x.agentcici.com`.
