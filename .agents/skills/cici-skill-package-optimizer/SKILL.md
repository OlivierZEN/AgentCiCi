---
name: cici-skill-package-optimizer
description: Optimize Cici Assistant universal-skill-package@1.0 zip packages and repackage them for re-import.
---

# Cici Skill Package Optimizer

Use this skill when the user provides a Cici Assistant skill package, asks to optimize a Cici skill zip, or mentions `universal-skill-package@1.0`.

## Required Workflow

1. Unzip the package into a temporary working directory.
2. Confirm the package root contains `manifest.json`, `SKILL.md`, and `cici-skill.md`.
3. Read `PACKAGE_SPEC.md` if present. Treat it as the package-level source of truth.
4. If `PACKAGE_SPEC.md` is missing, add a compliant `PACKAGE_SPEC.md` before repackaging.
5. Optimize only the package files, then zip the files back at the package root.
6. Provide a short summary listing changed files and the reason for each change.

## File Responsibilities

- `manifest.json`: package identity and metadata.
- `SKILL.md`: external-agent entrypoint for understanding and using this specific business skill package.
- `cici-skill.md`: Cici Assistant skill specification imported as draft spec text.
- `prompt.md`: runtime prompt fragment and primary optimization target.
- `contract.json`: output contract, risk level, trigger hints, and examples.
- `resources.json`: external resource dependencies by name only.
- `README.md`: human-facing package instructions.
- `PACKAGE_SPEC.md`: package format rules.

## Editing Rules

- Preserve `manifest.format = "universal-skill-package"`.
- Preserve `manifest.formatVersion = "1.0"`.
- Preserve `manifest.packageId` unless the user explicitly asks to create a different skill code.
- Keep all JSON files valid UTF-8 JSON.
- Do not add secrets, tokens, passwords, API keys, connection strings, private credentials, tool runtime configuration, or knowledge-base content.
- Keep all files at the zip root. Do not nest them inside an extra folder.
- Do not add unrelated files.

## Optimization Priorities

1. Improve `prompt.md` for clear execution order, tool-use discipline, safety, and stable outputs.
2. Improve `SKILL.md` for external-agent readability, usage guidance, and package navigation.
3. Improve `cici-skill.md` for Cici capability boundaries, operator readability, escalation rules, and concise instructions.
4. Improve `contract.json` for explicit output expectations, risk level consistency, trigger hints, and user intent examples.
5. Improve `resources.json` only by clarifying resource names, display names, required flags, or matching hints.
6. Update `README.md` only when it helps the human operator understand how to re-import the package.

## Validation

Before returning the optimized package:

- Parse `manifest.json`, `contract.json`, and `resources.json`.
- Confirm `manifest.format` and `manifest.formatVersion` are unchanged.
- Confirm `SKILL.md` and `cici-skill.md` are present.
- Search all files for likely secrets or credentials and remove any found.
- Confirm the final zip can be unpacked into the expected root-level file set.
