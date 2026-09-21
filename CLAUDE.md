# CLAUDE.md — Claude Code 侧入口（内容唯一来源：AGENTS.md）

> 本文件**故意不重复任何内容**：项目记忆正文只维护 `AGENTS.md` 一份，这里通过 Claude Code 的
> `@` 导入语法把它读进来（Claude Code 也会直接读仓库根的 `AGENTS.md`，双保险，重复粘贴反而会漂移）。

@AGENTS.md

## 本仓库在两个工具下的对应关系（勿手动改一边）

| 用途 | ZCode 约定 | Claude Code 约定 | 内容来源 |
| --- | --- | --- | --- |
| 项目记忆（每次会话加载） | `AGENTS.md` | 本文件 `@AGENTS.md` 导入 | **只有 `AGENTS.md` 一份** |
| 技能：前端规范 | `.zcode/skills/frontend-ui/SKILL.md` | `.claude/skills/frontend-ui/SKILL.md` | `.zcode/skills/` 为准，`.claude/` 为镜像 |
| 技能：投研数据口径 | `.zcode/skills/quant-domain/SKILL.md` | `.claude/skills/quant-domain/SKILL.md` | 同上 |
| 技能：技能编写规范 | `.zcode/skills/skill-authoring/SKILL.md` | `.claude/skills/skill-authoring/SKILL.md` | 同上；**创建/修改技能前先读它** |

**记忆或技能有新增/修改时，两边必须同步**——技能文件无法跨目录共用，只能各放一份：

```sh
sh sync-memory.sh           # 校验两边是否一致（不一致会列出差异并以退出码 1 失败）
sh sync-memory.sh --write   # 以 .zcode/skills 为准覆盖 .claude/skills（改完技能后执行）
```

记忆正文（`AGENTS.md`）只改一处即可，无需同步；若新增技能，记得两处目录都要有（用 `--write` 一次生成）。
