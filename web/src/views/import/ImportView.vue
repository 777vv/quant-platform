<template>
  <el-card class="import-card">
    <template #header><span>基金数据导入（近10年，不足10年自成立起）</span></template>
    <el-row :gutter="12" align="middle">
      <el-col :span="8">
        <el-input v-model="code" placeholder="输入基金代码，如 510300 / 110003" maxlength="12" clearable @keyup.enter="handleCheck">
          <template #append>
            <el-button :loading="checking" @click="handleCheck">校验</el-button>
          </template>
        </el-input>
      </el-col>
    </el-row>

    <el-alert v-if="checkResult && !checkResult.supported" :title="`不支持导入：${checkResult.reason}`" type="error" show-icon class="block" :closable="false" />

    <el-descriptions v-if="checkResult && checkResult.supported" :column="3" border class="block" title="校验通过">
      <el-descriptions-item label="名称">{{ checkResult.name }}</el-descriptions-item>
      <el-descriptions-item label="类型">
        <el-tag :type="checkResult.fundType === 1 ? 'primary' : 'success'">
          {{ checkResult.fundType === 1 ? '场内ETF' : '场外指数基金' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="基金公司">{{ checkResult.fundCompany || '--' }}</el-descriptions-item>
      <el-descriptions-item label="跟踪指数">{{ checkResult.indexName || '未识别' }}</el-descriptions-item>
      <el-descriptions-item label="成立日期">{{ checkResult.estabDate || '--' }}</el-descriptions-item>
      <el-descriptions-item label="池内状态">
        <el-tag :type="poolTag.type">{{ poolTag.text }}</el-tag>
        <span v-if="checkResult.lastSyncDate" class="pool-note">历史数据截至 {{ checkResult.lastSyncDate }}</span>
      </el-descriptions-item>
    </el-descriptions>

    <div v-if="checkResult && checkResult.supported" class="block">
      <el-button type="primary" :loading="importing" :disabled="progress && progress.status === 'RUNNING'" @click="handleImport">
        {{ poolTag.button }}
      </el-button>
      <span class="import-note">{{ poolTag.note }}</span>
    </div>

    <el-card v-if="progress" shadow="never" class="block">
      <el-progress :percentage="progressPercent" :status="progressStatus" />
      <div class="progress-step">
        {{ progress.step }}
        <span v-if="progress.message" class="progress-msg">{{ progress.message }}</span>
      </div>
    </el-card>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkFund, importFund, importProgress } from '@/api/fund'
import type { FundCheckVO, TaskProgressVO } from '@/api/fund'

const code = ref('')
const checking = ref(false)
const importing = ref(false)
const checkResult = ref<FundCheckVO | null>(null)
const progress = ref<TaskProgressVO | null>(null)
let pollTimer: number | undefined

const progressPercent = computed(() => {
  if (!progress.value) return 0
  if (progress.value.status === 'DONE') return 100
  const { total, imported } = progress.value
  return total > 0 ? Math.min(99, Math.round((imported / total) * 100)) : 30
})

const progressStatus = computed(() => {
  if (!progress.value) return undefined
  if (progress.value.status === 'FAILED') return 'exception'
  if (progress.value.status === 'DONE') return 'success'
  return undefined
})

/**
 * 池内状态三态文案（V4.1）：把"在自选池""曾导入已移出""新基金"讲清楚，
 * 并如实说明本次导入会覆盖刷新历史数据（历史导入是从成立日整段重拉、先删后插，不是只补缺的几天；
 * 真正的增量同步是每日定时任务在做）。
 */
const poolTag = computed(() => {
  const result = checkResult.value
  if (!result) {
    return { type: 'info' as const, text: '', button: '', note: '' }
  }
  if (result.inPool) {
    return {
      type: 'info' as const,
      text: '已在自选池',
      button: '重新导入（覆盖刷新）',
      note: '本次会从成立日起整段重拉并覆盖本地历史数据，不会产生重复；只想补最新几天可用基金池的「同步」'
    }
  }
  if (result.removedFromPool) {
    return {
      type: 'warning' as const,
      text: '已移出自选（历史数据保留）',
      button: '恢复到自选池并导入',
      note: '本次会把它重新放回自选池，并覆盖刷新历史数据'
    }
  }
  return {
    type: 'info' as const,
    text: '新基金',
    button: '开始导入',
    note: '将从成立日（不足 10 年）起拉取历史数据'
  }
})

async function handleCheck() {
  // V4.6：放宽为"基本格式"校验，不再强制 6 位纯数字——指数代码的其它写法（如 000922 不带市场、
  // SH000922 带前缀）会在这里被误拦；具体"是不是基金、是不是指数基金"交给后端校验接口判断（它查东财，更准）。
  const trimmed = code.value.trim()
  if (!trimmed || trimmed.length > 12) {
    ElMessage.warning('请输入基金代码（不超过 12 位，如 510300 / 110003）')
    return
  }
  checking.value = true
  checkResult.value = null
  progress.value = null
  try {
    checkResult.value = await checkFund(trimmed)
  } finally {
    checking.value = false
  }
}

function handleImport() {
  if (!checkResult.value) return
  importing.value = true
  progress.value = null
  // 用校验通过的规范化代码发起导入（已 trim；与 checkResult.code 一致）
  importFund(checkResult.value.code)
    .then((res) => startPoll(res.taskId))
    .finally(() => {
      importing.value = false
    })
}

function startPoll(taskId: string) {
  progress.value = { taskId, status: 'RUNNING', step: '任务已提交', total: 0, imported: 0, message: null }
  pollTimer = window.setInterval(async () => {
    const p = await importProgress(taskId)
    progress.value = p
    if (p.status !== 'RUNNING') {
      window.clearInterval(pollTimer)
      if (p.status === 'DONE') {
        ElMessage.success('导入完成，可在基金池查看')
      }
    }
  }, 1500)
}
</script>

<style scoped>
.import-card {
  max-width: 900px;
}

.block {
  margin-top: 16px;
}

.progress-step {
  margin-top: 8px;
  color: var(--q-text-regular);
  font-size: var(--q-font-sm);
}

.progress-msg {
  color: var(--q-color-up);
  margin-left: 8px;
}

/* 池内状态的补充说明（历史数据截止日）与按钮旁的口径说明 */
.pool-note {
  margin-left: var(--q-space-2);
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.import-note {
  margin-left: var(--q-space-3);
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
}
</style>
