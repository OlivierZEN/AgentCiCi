---
task_id: TASK-298
feature_id: FEAT-179
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
---

# TASK-298 - 场景模型能力过滤与推荐说明

## 范围

- 厂商目录只信任远程拉取或受控连通性检测所确认并持久化的能力元数据。
- 每个场景只返回能力与协议兼容的已选模型，无法确认能力的模型不进入候选。
- 路由写入在服务端重复校验；路由页面说明适用能力、推荐原则及空状态处置。

## 验收

- Embedding、OCR、实时/文件 ASR、代码解释器、联网搜索和网页抓取不显示不兼容模型。
- 直接调用路由写接口无法写入不兼容或能力未知的模型。
- 推荐候选与说明来自服务端场景定义，不由前端推断。
- 定向测试、构建、桌面端路由检查和本地开发环境发布通过；UAT/生产不修改。

## 验证结果

- `mvn -q -DskipTests package` 通过；前端路由定向 4 项与 production build 通过。
- Spring 集成在应用上下文创建前被既有 `agentcici_test` Flyway V81 checksum 漂移阻断；未执行 repair 或修改历史迁移。
- backend/frontend 已从本地 `main@1df52acc8860` 构建并发布为 `2.8.61-dev.1df52ac`；健康、运行版本/镜像 label、页面制品与完整 `./stack verify` 均通过。

## 下一步

- 在 UAT 完成实际厂商目录刷新、每个启用场景的路由配置及真实业务验收；测试库基线恢复后补跑 Spring 集成。
