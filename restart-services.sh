#!/bin/bash
# cc-agentcici 前后端服务重启脚本
# 用法: ./restart-services.sh

set -e

BACKEND_DIR="/Volumes/AISpace/codehouse/cc-agentcici/backend"
FRONTEND_DIR="/Volumes/AISpace/codehouse/cc-agentcici/frontend"
BACKEND_PORT=8080
FRONTEND_PORT=5173

echo "=== cc-agentcici 服务重启 ==="

# 1. 停止旧进程
echo "[1/4] 停止旧进程..."
BACKEND_PID=$(lsof -ti:$BACKEND_PORT 2>/dev/null || true)
FRONTEND_PID=$(lsof -ti:$FRONTEND_PORT 2>/dev/null || true)

if [ -n "$BACKEND_PID" ]; then
  echo "  停止后端 (PID: $BACKEND_PID)"
  kill -9 $BACKEND_PID 2>/dev/null || true
  sleep 1
else
  echo "  后端未在运行"
fi

if [ -n "$FRONTEND_PID" ]; then
  echo "  停止前端 (PID: $FRONTEND_PID)"
  kill -9 $FRONTEND_PID 2>/dev/null || true
  sleep 1
else
  echo "  前端未在运行"
fi

# 2. 启动后端
echo "[2/4] 启动后端 (Spring Boot)..."
cd "$BACKEND_DIR"
nohup mvn spring-boot:run -Dspring-boot.run.profiles=local > /tmp/cici-backend.log 2>&1 &
echo "  后端 PID: $!"

# 3. 启动前端
echo "[3/4] 启动前端 (Vite)..."
cd "$FRONTEND_DIR"
nohup npm run dev > /tmp/cici-frontend.log 2>&1 &
echo "  前端 PID: $!"

# 4. 等待并验收
echo "[4/4] 等待服务启动..."
MAX_WAIT=60
WAITED=0

while [ $WAITED -lt $MAX_WAIT ]; do
  BACKEND_OK=false
  FRONTEND_OK=false

  curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$BACKEND_PORT/actuator/health 2>/dev/null | grep -q "200" && BACKEND_OK=true
  curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:$FRONTEND_PORT/ 2>/dev/null | grep -q "200" && FRONTEND_OK=true

  if $BACKEND_OK && $FRONTEND_OK; then
    echo ""
    echo "=== 服务重启成功 ==="
    echo "  后端: http://127.0.0.1:$BACKEND_PORT - OK"
    echo "  前端: http://127.0.0.1:$FRONTEND_PORT - OK"
    exit 0
  fi

  if ! $BACKEND_OK; then echo "  等待后端... ($WAITED/${MAX_WAIT}s)"; fi
  if ! $FRONTEND_OK; then echo "  等待前端... ($WAITED/${MAX_WAIT}s)"; fi

  sleep 3
  WAITED=$((WAITED + 3))
done

echo ""
echo "=== 启动超时 ==="
curl -s http://127.0.0.1:$BACKEND_PORT/actuator/health >/dev/null 2>&1 || echo "  后端: 未就绪，查看日志 tail -f /tmp/cici-backend.log"
curl -s http://127.0.0.1:$FRONTEND_PORT/ >/dev/null 2>&1 || echo "  前端: 未就绪，查看日志 tail -f /tmp/cici-frontend.log"
exit 1
