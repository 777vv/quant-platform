<template>
  <!-- 行情弹框（V6.25）：在信号类列表里点【行情】直接看该基金的行情走势（复用基金详情的行情面板），
       只读浏览、不跳转基金详情页 -->
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="1140px"
    top="4vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <div v-if="loading" class="quote-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载行情数据…</span>
    </div>
    <el-empty v-else-if="!detail" description="行情数据加载失败（基金可能已移出自选池）" :image-size="60" />
    <QuoteTrendPanel
      v-else
      :fund-code="detail.fundCode"
      :fund-type="detail.fundType"
      :fund-name="detail.fundName"
      :index-code="detail.indexCode"
      :index-name="detail.indexName"
    />
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 行情弹框（V6.25 用户要求）：信号类列表（平台信号·均线信号、市场信号四个页签）的【行情】按钮点开，
 * 弹框内展示**与基金详情「行情走势」页签相同的内容**（同一枚 QuoteTrendPanel，口径与交互完全一致），
 * 是一个独立弹框、不跳转到基金详情页。
 *
 * 打开方式：`v-model` 控制显示；打开时按 `fundCode` 拉一次基金详情（拿 fundType 决定 K 线/净值口径、
 * 以及跟踪指数用于估值副图提示），拿到后交给面板自己取数。`destroy-on-close` 保证每次打开都是干净状态。
 */
import { ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { fundDetail, type FundDetailVO } from '@/api/fund'
import QuoteTrendPanel from './QuoteTrendPanel.vue'

const props = defineProps<{
  /** 弹框显示态（v-model） */
  modelValue: boolean
  /** 基金代码（打开时按它取详情） */
  fundCode: string
  /** 基金名称（详情未到时先用它做标题兜底） */
  fundName?: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

/** 基金详情（含 fundType/名称/跟踪指数，面板需要的都在里面） */
const detail = ref<FundDetailVO | null>(null)
const loading = ref(false)

/** 标题：优先用详情里的基金名，其次入参名，最后回退代码 */
const title = ref('')

/** 每次打开都重新取详情（列表里的价格/规模等可能已刷新） */
async function onOpen() {
  title.value = `${props.fundName || props.fundCode} · 行情走势`
  detail.value = null
  loading.value = true
  try {
    detail.value = await fundDetail(props.fundCode)
    title.value = `${detail.value.fundName || props.fundCode} · 行情走势`
  } catch {
    // 详情取不到（已移出自选池等）：保持空态，由模板给出说明
    detail.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.quote-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--q-space-2);
  height: 320px;
  color: var(--q-text-secondary);
  font-size: var(--q-font-sm);
}
</style>
