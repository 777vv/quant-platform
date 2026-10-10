<template>
  <!-- AI 助手头像：圆脸表情（两只眼睛 + 微笑 + 腮红）+ 右上小火花，颜色跟随 currentColor -->
  <svg
    class="ai-avatar"
    :class="{ 'ai-avatar--animated': animated }"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    aria-hidden="true"
  >
    <g class="ai-avatar-eyes">
      <ellipse class="ai-avatar-eye" cx="8.4" cy="10.3" rx="1.55" ry="1.95" />
      <ellipse class="ai-avatar-eye" cx="15.6" cy="10.3" rx="1.55" ry="1.95" />
    </g>
    <path class="ai-avatar-smile" d="M8.7 15.1 C9.9 16.9 14.1 16.9 15.3 15.1" />
    <ellipse class="ai-avatar-blush" cx="5.1" cy="13.9" rx="1.25" ry="0.85" />
    <ellipse class="ai-avatar-blush" cx="18.9" cy="13.9" rx="1.25" ry="0.85" />
    <path class="ai-avatar-spark" d="M19.6 2.1 C19.8 3.7 20.6 4.5 22.2 4.7 C20.6 4.9 19.8 5.7 19.6 7.3 C19.4 5.7 18.6 4.9 17 4.7 C18.6 4.5 19.4 3.7 19.6 2.1 Z" />
  </svg>
</template>

<script setup lang="ts">
/**
 * AI 助手头像（品牌标记，单一来源）。
 * 画法：**圆脸表情**——球体本身即"脸"，头像只画表情（两只椭圆眼 + 微笑 + 腮红），
 * 右上角一枚小火花点出"AI 生成"语义。风格亲切、像个助手，但不照搬任何现成产品的标识。
 * 用途：右下角悬浮球、对话面板标题角标、助手消息角标三处共用同一枚。
 * 颜色继承当前元素的 color（浮球/深色标题栏上是反白色），不写死色值。
 */
withDefaults(defineProps<{
  /** 头像边长（px），默认 24 */
  size?: number
  /** 是否播放动画（呼吸起伏 + 眨眼 + 火花闪动）：浮球与欢迎区开，静态场景关 */
  animated?: boolean
}>(), {
  size: 24,
  animated: false
})
</script>

<style scoped>
.ai-avatar {
  display: block;
}

.ai-avatar-eye,
.ai-avatar-smile,
.ai-avatar-blush,
.ai-avatar-spark {
  fill: currentColor;
  stroke: none;
}

/* 腮红压低透明度，避免抢眼睛的注意力 */
.ai-avatar-blush {
  opacity: 0.45;
}

/* 呼吸起伏：整枚头像轻微上下浮动，像在"待命" */
.ai-avatar--animated {
  animation: ai-avatar-bob 3.4s ease-in-out infinite;
}

@keyframes ai-avatar-bob {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-1.5px);
  }
}

/* 眨眼：只缩放眼睛所在的组（transform-box: fill-box 让缩放以眼睛自身为中心） */
.ai-avatar--animated .ai-avatar-eyes {
  transform-box: fill-box;
  transform-origin: center;
  animation: ai-avatar-blink 4.6s infinite;
}

@keyframes ai-avatar-blink {
  0%,
  92%,
  100% {
    transform: scaleY(1);
  }

  95% {
    transform: scaleY(0.12);
  }
}

/* 火花闪动：与浮球的光环同节奏地呼吸 */
.ai-avatar--animated .ai-avatar-spark {
  animation: ai-avatar-twinkle 2.6s ease-in-out infinite;
}

@keyframes ai-avatar-twinkle {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.45;
  }
}

/* 尊重系统"减弱动态效果"设置：全部动画关掉 */
@media (prefers-reduced-motion: reduce) {
  .ai-avatar--animated,
  .ai-avatar--animated .ai-avatar-eyes,
  .ai-avatar--animated .ai-avatar-spark {
    animation: none;
  }
}
</style>
