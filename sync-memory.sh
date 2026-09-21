#!/bin/sh
# 项目记忆与技能的"双工具"一致性脚本
#
# 背景：两个工具读取的记忆与技能位置不同，记忆正文可以共用（Claude Code 的 CLAUDE.md 用 @ 导入 AGENTS.md），
# 但**技能目录无法共用**（ZCode 读 .zcode/skills，Claude Code 读 .claude/skills），只能各放一份，
# 于是"改一处忘另一处"是必然会发生的事——本脚本把这件事机械化：
#
#   sh sync-memory.sh          校验两边一致（不一致则列出差异并以退出码 1 失败，可挂到提交前检查）
#   sh sync-memory.sh --write  以 .zcode/skills 为准，覆盖生成 .claude/skills（改完技能后执行）
#
# 约定：技能内容以 `.zcode/skills` 为唯一来源，`.claude/skills` 是它的镜像；不要直接编辑镜像。
set -e

cd "$(dirname "$0")"

SRC=".zcode/skills"
DST=".claude/skills"
MEMORY="AGENTS.md"
CLAUDE_MD="CLAUDE.md"

if [ ! -d "$SRC" ]; then
  echo "错误：未找到技能源目录 $SRC"
  exit 1
fi

if [ "$1" = "--write" ]; then
  mkdir -p "$DST"
  rm -rf "$DST"
  mkdir -p "$DST"
  cp -r "$SRC"/. "$DST"/
  echo "已同步：$SRC -> $DST"
  for f in "$SRC"/*/SKILL.md; do
    [ -f "$f" ] || continue
    echo "  · $(dirname "$f" | sed 's|.*/||')"
  done
else
  fail=0

  # 0) 每个技能的 frontmatter 必须合规：name 与目录名一致、description 非空
  #    （规范见 .zcode/skills/skill-authoring/SKILL.md——目录名与 name 不一致会让两工具加载行为不一致）
  for f in "$SRC"/*/SKILL.md; do
    [ -f "$f" ] || continue
    dir=$(dirname "$f" | sed 's|.*/||')
    if [ "$(head -1 "$f")" != "---" ]; then
      echo "frontmatter 缺失：$f 第一行不是 ---"
      fail=1
      continue
    fi
    name=$(sed -n '2p' "$f" | sed -n 's/^name:[[:space:]]*//p' | tr -d '\r')
    desc=$(sed -n '3p' "$f" | sed -n 's/^description:[[:space:]]*//p' | tr -d '\r')
    if [ "$name" != "$dir" ]; then
      echo "frontmatter 不一致：$f 的 name='$name' 与目录名 '$dir' 不同"
      fail=1
    fi
    if [ -z "$desc" ]; then
      echo "frontmatter 缺 description：$f（description 既是"做什么"也是"何时触发"，不能为空）"
      fail=1
    fi
  done

  # 1) 技能两份必须逐字节一致（多出的文件也算不一致，避免镜像里留下已删除的技能）
  if [ ! -d "$DST" ]; then
    echo "不一致：$DST 不存在（执行 sh sync-memory.sh --write 生成）"
    fail=1
  elif ! diff -r "$SRC" "$DST" >/dev/null 2>&1; then
    echo "不一致：$SRC 与 $DST 存在差异——"
    diff -r "$SRC" "$DST" | sed 's/^/    /' || true
    fail=1
  fi

  # 2) Claude Code 侧的记忆入口必须导入 AGENTS.md（否则它读不到项目记忆）
  if [ -f "$CLAUDE_MD" ]; then
    if ! grep -q '^@AGENTS\.md' "$CLAUDE_MD"; then
      echo "不一致：$CLAUDE_MD 缺少 '@AGENTS.md' 导入行"
      fail=1
    fi
  else
    echo "不一致：$CLAUDE_MD 不存在（Claude Code 侧读不到项目记忆）"
    fail=1
  fi

  # 3) 记忆正文只应有一份：CLAUDE.md 里若出现 AGENTS.md 的标题，说明被复制粘贴了
  if [ -f "$CLAUDE_MD" ] && grep -q '^# AGENTS.md' "$CLAUDE_MD"; then
    echo "不一致：$CLAUDE_MD 里出现了 AGENTS.md 的正文（应只保留 @AGENTS.md 导入，避免两处维护漂移）"
    fail=1
  fi

  if [ "$fail" = "0" ]; then
    echo "记忆与技能两边一致：$SRC == $DST，且 $CLAUDE_MD 正确导入 $MEMORY"
  else
    echo ""
    echo "修复：改完技能后执行  sh sync-memory.sh --write"
    exit 1
  fi
fi
