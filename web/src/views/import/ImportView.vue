<template>
  <div>
    <el-card class="import-card">
      <template #header><span>单只导入（近15年，不足15年自成立起）</span></template>
      <el-row :gutter="12" align="middle">
        <el-col :span="8">
          <el-input v-model="code" placeholder="输入基金代码，如 510300 / 110003" maxlength="12" clearable @keyup.enter="handleCheck">
            <template #append>
              <el-button v-if="userStore.can(PERM.ACTION_IMPORT_FUND)" :loading="checking" @click="handleCheck">校验</el-button>
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
        <el-button v-if="userStore.can(PERM.ACTION_IMPORT_FUND)" type="primary" :loading="importing" :disabled="progress && progress.status === 'RUNNING'" @click="handleImport">
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

    <!-- 批量导入（V5.41）：候选筛选给"不知道该导哪些"的场景，粘贴清单给"已有名单"的场景，两路合并 -->
    <el-card class="import-card block">
      <template #header><span>批量导入（串行导入，自动避开数据源封堵窗口；单批上限 200 只）</span></template>

      <!-- 第一步：候选筛选（可选） -->
      <div class="batch-row">
        <span class="batch-label">候选筛选</span>
        <span class="batch-field">规模 ≥</span>
        <el-input-number v-model="minScale" :min="0" :max="10000" :controls="false" style="width: 100px" />
        <span class="batch-field">亿 且 上市 ≥</span>
        <el-input-number v-model="minYears" :min="0" :max="30" :controls="false" style="width: 76px" />
        <span class="batch-field">年</span>
        <el-button :loading="candidatesLoading" @click="loadCandidates">查询符合条件的 ETF</el-button>
        <span class="muted">共 {{ candidates.length }} 只，已在池中的不可勾选（结果缓存 10 分钟）</span>
      </div>
      <el-table
        v-if="candidates.length"
        ref="candidateTableRef"
        :data="candidates"
        size="small"
        height="320"
        class="block"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="selectableCandidate" />
        <el-table-column prop="fundCode" label="代码" width="92" />
        <el-table-column prop="fundName" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scaleYi" label="规模(亿)" min-width="96" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.scaleYi }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="listedDate" label="上市日期" min-width="104" align="center" />
        <el-table-column label="池内状态" min-width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inPool ? 'info' : 'success'" size="small">{{ row.inPool ? '已在池中' : '未导入' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 第二步：粘贴代码清单（可选，与勾选合并去重） -->
      <div class="batch-row block">
        <span class="batch-label">代码清单</span>
        <el-input
          v-model="pasteText"
          type="textarea"
          :rows="2"
          placeholder="可选：粘贴基金代码（换行/逗号/空格分隔均可，自动提取 6 位数字），如 510310, 510500 159915"
        />
      </div>
      <div class="batch-row">
        <el-button v-if="userStore.can(PERM.ACTION_IMPORT_FUND)" type="primary" :loading="batchRunning" :disabled="pendingCount === 0 || singleRunning" @click="handleStartBatch">
          开始批量导入（{{ pendingCount }} 只）
        </el-button>
        <span class="muted">
          勾选 {{ selectedCount }} 只 + 粘贴 {{ parsedCount }} 只，去重后 {{ pendingCount }} 只；
          串行导入每只间隔 0.5 秒（防数据源封堵），100 只约 8~15 分钟，可离开页面稍后回来看进度
        </span>
      </div>

      <!-- 进度与失败清单 -->
      <el-card v-if="batchProgress" shadow="never" class="block">
        <el-progress :percentage="batchPercent" :status="batchProgressStatus" />
        <div class="progress-step">
          {{ batchProgress.message }}
          <span v-if="batchProgress.currentCode" class="progress-msg">当前：{{ batchProgress.currentCode }}</span>
        </div>
        <div class="batch-stat">
          已完成 {{ batchProgress.done }}/{{ batchProgress.total }} · 成功 {{ batchProgress.success }} · 失败 {{ batchProgress.failed }}
        </div>
        <template v-if="batchProgress.failures.length">
          <el-table :data="batchProgress.failures" size="small" class="block">
            <el-table-column prop="code" label="失败代码" width="110" />
            <el-table-column prop="reason" label="失败原因" min-width="260" show-overflow-tooltip />
          </el-table>
          <el-button v-if="batchProgress.status !== 'RUNNING' && userStore.can(PERM.ACTION_IMPORT_FUND)" size="small" @click="handleRetryFailed">
            重试失败项（{{ batchProgress.failures.length }} 只）
          </el-button>
        </template>
      </el-card>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/permissions'
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkFund, etfCandidates, batchImportProgress, importFund, importProgress, startBatchImport } from '@/api/fund'
import type { BatchImportProgress, EtfCandidate, FundCheckVO, TaskProgressVO } from '@/api/fund'

const userStore = useUserStore()

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

// ===== 批量导入（V5.41）=====

const minScale = ref(10)
const minYears = ref(6)
const candidatesLoading = ref(false)
const candidates = ref<EtfCandidate[]>([])
const selectedCandidates = ref<EtfCandidate[]>([])
const pasteText = ref('')
const batchRunning = ref(false)
const batchProgress = ref<BatchImportProgress | null>(null)
let batchTimer: number | undefined

/** 单只导入进行中时禁止再发起批量（共享东财请求路径，避免自我撞车） */
const singleRunning = computed(() => importing.value || (!!progress.value && progress.value.status === 'RUNNING'))

async function loadCandidates() {
  candidatesLoading.value = true
  try {
    candidates.value = await etfCandidates(minScale.value, minYears.value)
    // 新查询结果重置勾选，避免"上一次的勾选"错配到本次清单
    selectedCandidates.value = []
  } finally {
    candidatesLoading.value = false
  }
}

/** 已在池中的行不可勾选（服务端也会跳过，这里提前挡掉省额度） */
function selectableCandidate(row: EtfCandidate): boolean {
  return !row.inPool
}

function onSelectionChange(rows: EtfCandidate[]) {
  selectedCandidates.value = rows
}

/** 粘贴文本里提取 6 位数字代码（换行/逗号/空格等分隔符都不影响） */
const parsedCount = computed(() => parsedCodes().length)

function parsedCodes(): string[] {
  return [...new Set(pasteText.value.match(/\d{6}/g) ?? [])]
}

const selectedCount = computed(() => selectedCandidates.value.length)

/** 待导入 = 勾选 ∪ 粘贴（去重）；页面上如实展示三个数字，避免"到底导多少只"说不清 */
const pendingCount = computed(() => {
  const all = new Set([...selectedCandidates.value.map((c) => c.fundCode), ...parsedCodes()])
  return all.size
})

function handleStartBatch() {
  const codes = [...new Set([...selectedCandidates.value.map((c) => c.fundCode), ...parsedCodes()])]
  if (codes.length === 0) {
    ElMessage.warning('请先勾选候选 ETF 或粘贴基金代码')
    return
  }
  launch(codes)
}

/** 重试失败项：只把失败的代码再跑一轮 */
function handleRetryFailed() {
  const codes = batchProgress.value?.failures.map((f) => f.code) ?? []
  if (codes.length === 0) {
    return
  }
  launch(codes)
}

function launch(codes: string[]) {
  batchRunning.value = true
  startBatchImport(codes)
    .then((res) => {
      if (res.skippedExisting.length > 0) {
        ElMessage.info(`其中 ${res.skippedExisting.length} 只已在自选池，自动跳过`)
      }
      startBatchPoll(res.taskId, res.accepted.length)
    })
    .finally(() => {
      batchRunning.value = false
    })
}

function startBatchPoll(taskId: string, total: number) {
  batchProgress.value = {
    taskId, status: 'RUNNING', message: '任务已排队', total, done: 0, success: 0, failed: 0,
    currentCode: null, currentName: null, failures: [], startedAt: null, finishedAt: null
  }
  stopBatchPoll()
  batchTimer = window.setInterval(async () => {
    batchProgress.value = await batchImportProgress(taskId)
    if (batchProgress.value.status !== 'RUNNING') {
      stopBatchPoll()
      if (batchProgress.value.status === 'DONE') {
        ElMessage.success('批量导入结束，可在基金池查看')
      } else {
        ElMessage.error(batchProgress.value.message)
      }
    }
  }, 2000)
}

function stopBatchPoll() {
  if (batchTimer) {
    window.clearInterval(batchTimer)
    batchTimer = undefined
  }
}

onUnmounted(stopBatchPoll)

const batchPercent = computed(() => {
  if (!batchProgress.value) return 0
  if (batchProgress.value.status === 'DONE') return 100
  const { total, done } = batchProgress.value
  return total > 0 ? Math.min(99, Math.round((done / total) * 100)) : 5
})

const batchProgressStatus = computed(() => {
  if (!batchProgress.value) return undefined
  if (batchProgress.value.status === 'FAILED') return 'exception'
  if (batchProgress.value.status === 'DONE') return 'success'
  return undefined
})
</script>

<style scoped>
/* 两张卡与基金池等列表页一致：铺满内容区（此前误限 900px 导致右侧留白） */
.import-card {
  width: 100%;
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

/* 批量导入卡：一行一个语义（筛选条件 / 粘贴清单 / 发起按钮），标签固定宽对齐 */
.batch-row {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
  margin-top: 12px;
}

.batch-label {
  flex: none;
  width: 64px;
  font-weight: 600;
  color: var(--q-text-primary);
  font-size: var(--q-font-sm);
}

.batch-field {
  flex: none;
  color: var(--q-text-regular);
  font-size: var(--q-font-sm);
}

.muted {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.batch-stat {
  margin-top: 8px;
  font-size: var(--q-font-sm);
  color: var(--q-text-regular);
}
</style>
