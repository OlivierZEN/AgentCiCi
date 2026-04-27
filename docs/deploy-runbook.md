# Deployment Runbook (Step 3)

## Local Environment

1. Start infrastructure:
   - `docker compose up -d`
2. Verify health:
   - `docker compose ps`
3. Start backend with local profile:
   - `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local`
4. Start frontend:
   - `cd frontend && npm run dev`

## Pre-Release Checklist

- Run `scripts/quality-check.sh`
- Ensure `/system/health` returns `UP`
- Verify auth flow:
  - `/auth/sms/send`
  - `/auth/sms/login`
  - `/auth/me`
- Verify orchestrator flow:
  - `/ai/chat`
  - `/ai/sessions`
  - `/ops/metrics/cost`

## Rollback Notes

- Backend rollback: redeploy previous artifact and restart.
- DB rollback: apply Flyway rollback strategy by new forward migration (never mutate applied migration files).
- If Redis issues occur, switch `app.auth.sms.store` to `memory` temporarily for emergency local fallback.
