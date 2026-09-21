#!/usr/bin/env node
/**
 * 前端样式规范检查（V2.0）
 * 规则：色值只能出现在 styles/tokens.css、styles/element-override.css、utils/palette.ts 三个白名单文件中；
 *       其余 .vue/.css/.ts 一律使用 var(--q-*) 或从 palette.ts 引入。
 *       同时禁止 <style> 里出现内联十六进制色值与 rgb()/rgba() 字面量。
 * 用法：npm run check:style（退出码 1 表示有违规）
 * 规范全文：.zcode/skills/frontend-ui/SKILL.md
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const SRC = join(ROOT, 'src')

/** 允许出现色值的文件（唯一来源） */
const WHITELIST = ['src/styles/tokens.css', 'src/styles/element-override.css', 'src/utils/palette.ts']

/** 扫描目标扩展名 */
const EXTENSIONS = ['.vue', '.css', '.ts', '.js']

/** 违规模式：十六进制色值、rgb/rgba 字面量 */
const PATTERNS = [
  { name: '十六进制色值', regex: /#[0-9a-fA-F]{3,8}\b/g },
  { name: 'rgb/rgba 字面量', regex: /\brgba?\([^)]*\)/g }
]

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      walk(full, files)
    } else if (EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      files.push(full)
    }
  }
  return files
}

const violations = []
for (const file of walk(SRC)) {
  const rel = relative(ROOT, file).replace(/\\/g, '/')
  if (WHITELIST.includes(rel)) {
    continue
  }
  const lines = readFileSync(file, 'utf8').split('\n')
  lines.forEach((line, index) => {
    for (const { name, regex } of PATTERNS) {
      const matches = line.match(regex)
      if (matches) {
        violations.push({ file: rel, line: index + 1, name, value: matches.join(', '), text: line.trim().slice(0, 80) })
      }
    }
  })
}

if (violations.length === 0) {
  console.log('✅ 样式规范检查通过：未发现白名单之外的硬编码色值')
  process.exit(0)
}

console.error(`❌ 样式规范检查未通过：发现 ${violations.length} 处硬编码色值`)
console.error('   规则：色值只能用 var(--q-*) 或从 utils/palette.ts 引入（规范见 .zcode/skills/frontend-ui/SKILL.md）\n')
for (const v of violations) {
  console.error(`   ${v.file}:${v.line}  [${v.name}] ${v.value}`)
  console.error(`      ${v.text}`)
}
process.exit(1)
