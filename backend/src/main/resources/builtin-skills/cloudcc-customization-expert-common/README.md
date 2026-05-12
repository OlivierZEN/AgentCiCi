# cloudcc-dev-skills

面向 **CloudCC CRM 二次开发** 的模块化文档库，供 Agent / AI 助手在设计与实现时引用。内容与官方能力对齐，强调接口参数完整性与可联调性。

## 用途

- 根目录 `SKILL.md` 定义技能元数据、执行规则与质量标准；本仓库作为「CloudCC 开发」领域知识源，与 Cursor / Claude 等工具配合使用。
- 按模块查阅：`introduction`（概念与边界）→ `devguide`（若有，规范与约束）→ `api`（OpenAPI 风格接口契约）。

## 文档约定

每个业务模块对应一个子目录，文档类型如下。

| 文件 | 说明 |
|------|------|
| `introduction.md` | 模块概念、能力边界、与周边模块关系 |
| `devguide.md` | 开发规范、最佳实践、平台约束（非所有模块都有） |
| `api.md` | 接口视角：服务名、路径与方法、请求/响应、成功失败判定；**不写 CLI 命令与参数位说明** |

编写与维护要求（摘要）见根目录 `SKILL.md`：优先保证参数完整性、字段名与真实接口一致；删除/更新类接口须标清关键 ID；信息不足时明确阻塞点与最短补全路径。

## 目录结构

```text
cloudcc-dev-skills/
├── SKILL.md                 # 技能说明、规则与模块列表
├── README.md                # 本文件
├── application/             # 应用
├── brief/                   # 摘要类接口（仅 api）
├── button/                  # 按钮
├── classes/                 # Apex 类
├── customPage/              # 自定义页面
├── customSetting/           # 自定义设置
├── fields/                  # 字段
├── menu/                    # 菜单
├── object/                  # 自定义对象
├── pagelayout/              # 页面布局
├── permission/              # 权限
├── profile/                 # 简档
├── recordType/              # 记录类型
├── role/                    # 角色
├── scheduleJob/             # 定时作业
├── script/                  # 脚本
├── timer/                   # 定时类
├── triggers/                # 触发器
├── user/                    # 用户
├── validationRule/          # 校验规则
└── view/                    # 视图
```

## 模块与文档覆盖

以下为当前仓库中**实际存在**的模块及文档文件（有则列 ✓）。

| 模块 | introduction | devguide | api |
|------|:-------------:|:--------:|:---:|
| application | ✓ | ✓ | ✓ |
| brief | — | — | ✓ |
| button | ✓ | ✓ | ✓ |
| classes | ✓ | ✓ | ✓ |
| customPage | ✓ | — | ✓ |
| customSetting | ✓ | — | ✓ |
| fields | ✓ | — | ✓ |
| menu | ✓ | — | ✓ |
| object | ✓ | — | ✓ |
| pagelayout | ✓ | — | ✓ |
| permission | ✓ | — | ✓ |
| profile | ✓ | — | ✓ |
| recordType | ✓ | — | ✓ |
| role | ✓ | — | ✓ |
| scheduleJob | ✓ | — | ✓ |
| script | ✓ | — | ✓ |
| timer | ✓ | — | ✓ |
| triggers | ✓ | ✓ | ✓ |
| user | ✓ | — | ✓ |
| validationRule | ✓ | — | ✓ |
| view | ✓ | — | ✓ |

`SKILL.md` 中另列有 `project`、`config` 等模块名，若仓库中尚未出现对应目录，表示尚未纳入或待补充。

## 使用建议

1. 先读 `SKILL.md` 中的适用范围与执行规则。
2. 做某一模块开发时：依次阅读该模块的 `introduction.md` → `devguide.md`（若有）→ `api.md`。
3. 与 **cloudcc-cli** 联用时：CLI 负责拉取/同步环境侧能力；本仓库负责**模块语义与接口契约**，二者互补。

## 许可与归属

文档内容面向 CloudCC 平台能力说明；具体许可以仓库上层或组织策略为准。若需对外分发，请与平台方版权与合规要求保持一致。
