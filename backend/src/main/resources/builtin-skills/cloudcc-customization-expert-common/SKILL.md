---
name: cloudcc-customization-expert-common
description: CloudCC CRM 二次开发设计与实施。优先通过 `introduction` 获取官方模块文档，再给出方案与代码。适用于对象/字段、权限、触发器、类、脚本、菜单、应用、视图、自定义设置、定时作业与定时类等模块。
---

# cloudcc-customization-expert-common

## 适用范围

- 文档根目录：本技能目录下的 `<module>/`
- 文档类型：
  - `introduction.md`
  - `devguide.md`
  - `api.md`

## 执行规则

1. 只写接口视角，不写 CLI 命令和参数位说明。
2. `api.md` 必须包含：
   - 服务名称
   - 接口地址与方法
   - 请求参数（必填/可选/默认值/条件必填）
   - 请求体示例
   - 返回成功/失败判定
3. 优先保证参数完整性，避免“概述型”描述。
4. 仅做与当前模块直接相关的改动，不扩散重构。

## 质量标准

- 文档可直接支持联调与实现。
- 字段命名与真实接口保持一致。
- 删除、更新接口必须明确关键 ID 参数。
- 若信息不足，明确阻塞点并给出下一步最短路径。

## 模块列表

- `object`
- `fields`
- `recordType`
- `button`
- `pagelayout`
- `validationRule`
- `user`
- `role`
- `profile`
- `permission`
- `classes`
- `triggers`
- `timer`
- `scheduleJob`
- `customPage`
- `script`
- `menu`
- `application`
- `view`
- `customSetting`
