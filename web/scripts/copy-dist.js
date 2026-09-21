import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(root, 'dist')
const dest = resolve(root, '..', 'quant-web', 'src', 'main', 'resources', 'static')

if (!existsSync(src)) {
  console.error('dist 目录不存在，请先执行 vite build')
  process.exit(1)
}
rmSync(dest, { recursive: true, force: true })
mkdirSync(dest, { recursive: true })
cpSync(src, dest, { recursive: true })
console.log(`已拷贝前端构建产物: ${src} -> ${dest}`)
