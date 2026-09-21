<template>
  <div class="login-page q-on-dark">
    <!-- 装饰层：细网格 + 品牌光晕 + K 线剪影（纯装饰，不进无障碍树） -->
    <div class="login-grid" aria-hidden="true" />
    <div class="login-glow" aria-hidden="true" />
    <svg class="login-candles" viewBox="0 0 1000 200" preserveAspectRatio="none" aria-hidden="true">
      <!-- 远景一排：更淡更低，制造纵深 -->
      <g class="candles candles--far">
        <template v-for="item in FAR_CANDLES" :key="`f${item.x}`">
          <line :x1="item.x" :y1="item.wickTop" :x2="item.x" :y2="item.wickBottom" vector-effect="non-scaling-stroke" />
          <rect :x="item.x - 5" :y="item.bodyTop" width="10" :height="item.bodyHeight" rx="1" />
        </template>
      </g>
      <!-- 近景一排：主视觉 + 一条均线 -->
      <g class="candles candles--near">
        <template v-for="item in NEAR_CANDLES" :key="`n${item.x}`">
          <line
            :class="item.up ? 'candle--up' : 'candle--down'"
            :x1="item.x"
            :y1="item.wickTop"
            :x2="item.x"
            :y2="item.wickBottom"
            vector-effect="non-scaling-stroke"
          />
          <rect
            :class="item.up ? 'candle--up' : 'candle--down'"
            :x="item.x - 7"
            :y="item.bodyTop"
            width="14"
            :height="item.bodyHeight"
            rx="1.5"
          />
        </template>
        <polyline class="login-ma" :points="maPoints" />
      </g>
    </svg>

    <div class="login-panel">
      <div class="login-brand">
        <div class="brand-name">个人量化投资助手</div>
        <div class="brand-sub">指数基金 · 买卖建议 · 不做自动交易</div>
      </div>
      <el-card class="login-card">
        <template #header>
          <div class="login-title">登录</div>
        </template>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">登 录</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 登录页背景图形元素（固定图案，纯装饰）。
 * 单根 K 线用「横坐标 / 影线上端 / 影线下端 / 实体上端 / 实体高度 / 是否收涨」描述，
 * 坐标基于 viewBox 0 0 1000 200（y 越小越靠上）。图案手工排布而非随机生成，
 * 保证每次渲染一致，形态像一段上涨后的高位震荡。
 */
interface Candle {
  /** 横坐标（viewBox 坐标系） */
  x: number
  /** 影线最高点 y */
  wickTop: number
  /** 影线最低点 y */
  wickBottom: number
  /** 实体顶部 y */
  bodyTop: number
  /** 实体高度 */
  bodyHeight: number
  /** 是否收涨（红涨绿跌） */
  up: boolean
}

/** 近景 K 线：主视觉 */
const NEAR_CANDLES: Candle[] = [
  { x: 40, wickTop: 150, wickBottom: 190, bodyTop: 158, bodyHeight: 26, up: true },
  { x: 90, wickTop: 138, wickBottom: 178, bodyTop: 146, bodyHeight: 24, up: true },
  { x: 140, wickTop: 120, wickBottom: 162, bodyTop: 128, bodyHeight: 26, up: false },
  { x: 190, wickTop: 104, wickBottom: 146, bodyTop: 112, bodyHeight: 26, up: true },
  { x: 240, wickTop: 88, wickBottom: 132, bodyTop: 96, bodyHeight: 28, up: true },
  { x: 290, wickTop: 76, wickBottom: 118, bodyTop: 84, bodyHeight: 24, up: false },
  { x: 340, wickTop: 62, wickBottom: 108, bodyTop: 72, bodyHeight: 28, up: true },
  { x: 390, wickTop: 58, wickBottom: 100, bodyTop: 66, bodyHeight: 24, up: true },
  { x: 440, wickTop: 70, wickBottom: 112, bodyTop: 78, bodyHeight: 26, up: false },
  { x: 490, wickTop: 84, wickBottom: 126, bodyTop: 92, bodyHeight: 26, up: false },
  { x: 540, wickTop: 92, wickBottom: 138, bodyTop: 100, bodyHeight: 30, up: true },
  { x: 590, wickTop: 78, wickBottom: 122, bodyTop: 86, bodyHeight: 26, up: true },
  { x: 640, wickTop: 64, wickBottom: 110, bodyTop: 74, bodyHeight: 28, up: true },
  { x: 690, wickTop: 58, wickBottom: 102, bodyTop: 66, bodyHeight: 26, up: false },
  { x: 740, wickTop: 46, wickBottom: 92, bodyTop: 56, bodyHeight: 28, up: true },
  { x: 790, wickTop: 36, wickBottom: 84, bodyTop: 46, bodyHeight: 30, up: true },
  { x: 840, wickTop: 30, wickBottom: 76, bodyTop: 40, bodyHeight: 26, up: false },
  { x: 890, wickTop: 22, wickBottom: 70, bodyTop: 32, bodyHeight: 30, up: true },
  { x: 940, wickTop: 16, wickBottom: 64, bodyTop: 26, bodyHeight: 28, up: true },
  { x: 990, wickTop: 12, wickBottom: 58, bodyTop: 22, bodyHeight: 26, up: true }
]

/** 远景 K 线：更淡更低，制造纵深感 */
const FAR_CANDLES: Candle[] = [
  { x: 70, wickTop: 168, wickBottom: 198, bodyTop: 176, bodyHeight: 18, up: false },
  { x: 170, wickTop: 158, wickBottom: 194, bodyTop: 166, bodyHeight: 20, up: true },
  { x: 270, wickTop: 146, wickBottom: 190, bodyTop: 156, bodyHeight: 22, up: true },
  { x: 370, wickTop: 138, wickBottom: 184, bodyTop: 148, bodyHeight: 20, up: false },
  { x: 470, wickTop: 130, wickBottom: 180, bodyTop: 140, bodyHeight: 22, up: true },
  { x: 570, wickTop: 122, wickBottom: 176, bodyTop: 132, bodyHeight: 24, up: true },
  { x: 670, wickTop: 112, wickBottom: 170, bodyTop: 124, bodyHeight: 24, up: false },
  { x: 770, wickTop: 100, wickBottom: 164, bodyTop: 112, bodyHeight: 26, up: true },
  { x: 870, wickTop: 88, wickBottom: 156, bodyTop: 100, bodyHeight: 26, up: true },
  { x: 970, wickTop: 78, wickBottom: 150, bodyTop: 90, bodyHeight: 26, up: true }
]

/** 均线折线：取近景实体中点连线，叠在 K 线之上 */
const maPoints = NEAR_CANDLES.map((item) => `${item.x},${item.bodyTop + item.bodyHeight / 2}`).join(' ')

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 登录成功后跳回来源页（无来源则进仪表盘）；校验失败时停留并显示红字 */
async function handleLogin() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/'
    await router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--q-login-bg);
}

/* 细网格底纹 */
.login-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--q-login-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--q-login-grid) 1px, transparent 1px);
  background-size: 44px 44px;
}

/* 中上部光晕：把视线引到登录卡 */
.login-glow {
  position: absolute;
  inset: 0;
  background: var(--q-login-glow);
}

/* K 线剪影：贴底铺满，作为背景主体图形 */
.login-candles {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 58%;
  opacity: 0.8;
}

.candles--far {
  opacity: 0.45;
}

.candles--far rect,
.candles--far line {
  fill: var(--q-login-candle-up);
  stroke: var(--q-login-candle-up);
  stroke-width: 1;
}

.candle--up {
  fill: var(--q-login-candle-up);
  stroke: var(--q-login-candle-up);
}

.candle--down {
  fill: var(--q-login-candle-down);
  stroke: var(--q-login-candle-down);
}

.candles--near line {
  stroke-width: 1.4;
}

.login-ma {
  fill: none;
  stroke: var(--q-login-line);
  stroke-width: 1.6;
  stroke-linejoin: round;
}

.login-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--q-space-6);
}

.login-brand {
  text-align: center;
}

.brand-name {
  font-size: var(--q-font-2xl);
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--q-login-text);
}

.brand-sub {
  margin-top: var(--q-space-2);
  font-size: var(--q-font-sm);
  letter-spacing: 0.06em;
  color: var(--q-login-text-muted);
}

.login-card {
  width: 380px;
}

.login-title {
  text-align: center;
  font-size: var(--q-font-lg);
  font-weight: 600;
  color: var(--q-login-text);
}

.login-button {
  width: 100%;
}
</style>
