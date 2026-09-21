import { onUnmounted, watch, type Ref } from 'vue'

/**
 * 全屏层按 Esc 退出（V2.1）。
 * 背景：全屏容器是普通 div，不可聚焦，模板上的 @keydown.esc 永远不会触发；
 * 因此改为在打开期间监听 window 的 keydown，关闭或卸载时自动解绑。
 *
 * @param visible 全屏可见状态（ref）
 */
export function useEscToClose(visible: Ref<boolean>) {
  const handler = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      visible.value = false
    }
  }
  watch(visible, (open) => {
    if (open) {
      window.addEventListener('keydown', handler)
    } else {
      window.removeEventListener('keydown', handler)
    }
  })
  onUnmounted(() => window.removeEventListener('keydown', handler))
}
