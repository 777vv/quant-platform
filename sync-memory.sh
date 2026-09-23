#!/bin/sh
# 项目记忆与技能的"双工具"同步脚本
#
# 背景：ZCode 读 AGENTS.md 与 .zcode/skills；Claude Code 读 CLAUDE.md 与 .claude/skills。
# 四个位置两两成对：CLAUDE.md 是 AGENTS.md 的完整镜像，.claude/skills 是 .zcode/skills 的完整镜像。
# "改一处忘另一处"必然会发生——本脚本把同步机械化（用户口径：只要一边动了，必须同步两边）：
#
#   sh sync-memory.sh          校验两边一致（不一致则列出差异并以退出码 1 失败）
#   sh sync-memory.sh --write  以 AGENTS.md 与 .zcode/skills 为准，重新生成 CLAUDE.md 与 .claude/skills
#
# 约定：内容只改 AGENTS.md 与 .zcode/skills；CLAUDE.md 与 .claude/skills 是生成物，不要手改。
# （V5.7 起废弃"CLAUDE.md 只放 @导入"的单源设计——用户指出那一侧看不到规则，改为完整镜像。）
set -e

cd "$(dirname "$0")"

SRC=".zcode/skills"
DST=".claude/skills"
MEMORY="AGENTS.md"
CLAUDE_MD="CLAUDE.md"
CLAUDE_HEADER='<!-- CLAUDE.md 是 AGENTS.md 的完整镜像（由 sh sync-memory.sh --write 生成，勿手改）。规则：记忆/技能只要动了任何一边，必须立即同步另一边 -->'

# CLAUDE.md 的期望内容 = 镜像头 + 空行 + AGENTS.md 全文
expected_claude() {
  printf '%s

' "$CLAUDE_HEADER"
  cat "$MEMORY"
}

if [ "$1" = "--write" ]; then
  mkdir -p "$DST"
  rm -rf "$DST"
  mkdir -p "$DST"
  cp -r "$SRC"/. "$DST"/
  expected_claude > "$CLAUDE_MD"
  echo "已同步：$SRC -> $DST"
  for f in "$SRC"/*/SKILL.md; do
    [ -f "$f" ] || continue
    echo "  · $(dirname "$f" | sed 's|.*/||')"
  done
  echo "已同步：$MEMORY -> $CLAUDE_MD（完整镜像）"
else
  fail=0

  # 0) 每个技能的 frontmatter 必须合规：name 与目录名一致、description 非空
  for f in "$SRC"/*/SKILL.md; do
    [ -f "$f" ] || continue
    dir=$(dirname "$f" | sed 's|.*/||')
    if [ "$(head -1 "$f")" != "---" ]; then
      echo "frontmatter 缺失：$f 第一行不是 ---"
      fail=1
      continue
    fi
    name=$(sed -n '2p' "$f" | sed -n 's/^name:[[:space:]]*//p' | tr -d '')
    desc=$(sed -n '3p' "$f" | sed -n 's/^description:[[:space:]]*//p' | tr -d '')
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

  # 2) CLAUDE.md 必须是 AGENTS.md 的完整镜像（V5.7：不再用 @导入单源，用户要求两边都要有正文）
  if [ ! -f "$CLAUDE_MD" ]; then
    echo "不一致：$CLAUDE_MD 不存在（执行 sh sync-memory.sh --write 生成）"
    fail=1
  else
    tmp=$(mktemp)
    expected_claude > "$tmp"
    if ! cmp -s "$CLAUDE_MD" "$tmp"; then
      echo "不一致：$CLAUDE_MD 与 $MEMORY 的镜像内容不同（AGENTS.md 改了但 CLAUDE.md 没同步）——"
      diff "$CLAUDE_MD" "$tmp" | sed 's/^/    /' | head -20 || true
      fail=1
    fi
    rm -f "$tmp"
  fi

  if [ "$fail" = "0" ]; then
    echo "记忆与技能两边一致：$SRC == $DST，且 $CLAUDE_MD == $MEMORY 完整镜像"
  else
    echo ""
    echo "修复：执行  sh sync-memory.sh --write  后再校验"
    exit 1
  fi
fi
