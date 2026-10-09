<template>
  <div class="batch-backtest">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>批量回测</span>
          <span class="muted">一套策略参数 × 多只基金，批次内 4 线程并发执行；同一时间只允许一个运行中的批次</span>
          <el-tooltip content="有运行中的批次时不能发起新批次" :disabled="!hasRunning" placement="top">
            <span>
              <el-button v-if="canRun" type="primary" size="small" :disabled="hasRunning" @click="openDialog">批量回测</el-button>
            </span>
          </el-tooltip>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border size="small">
        <el-table-column prop="id" label="序号" width="70" />
        <el-table-column label="策略名称" min-width="150">
          <template #default="{ row }">
            <!-- 点击弹「策略配置详情」：整批统一的策略与参数一目了然 -->
            <el-link type="primary" :underline="false" @click="openParamDialog(row)">{{ typeName(row.strategyType) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="160">
          <template #default="{ row }">
            <span class="num">{{ fmtTime(row.createdAt) }}</span>
            <div v-if="row.finishedAt" class="sub-line num">{{ fmtTime(row.finishedAt) }} 完成</div>
            <div v-else-if="row.status === 0" class="sub-line text-up">运行中…</div>
          </template>
        </el-table-column>
        <el-table-column label="数量（总/成功/失败）" min-width="210">
          <template #default="{ row }">
            <span class="num">总 {{ row.totalCount }} · 成功 {{ row.successCount }} · 失败 {{ row.failCount }}</span>
            <el-progress
              v-if="row.status === 0"
              :percentage="Math.round(((row.successCount + row.failCount) / Math.max(row.totalCount, 1)) * 100)"
              :stroke-width="6"
              class="batch-progress"
            />
            <el-tag v-else-if="row.failCount > 0" type="warning" size="small" class="batch-done-tag">部分失败</el-tag>
            <el-tag v-else type="success" size="small" class="batch-done-tag">全部成功</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="操作账号" min-width="100">
          <template #default="{ row }">{{ row.createdBy ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- 发起批量回测弹窗：选基金（带标签快筛）→ 配策略参数 → 一键回测 -->
    <el-dialog v-model="dialogVisible" title="发起批量回测" width="880px" top="4vh">
      <el-form label-position="top" class="batch-form">
        <!-- 策略类型与「策略逻辑速览」同一行（V5.98）：下拉收窄，速览按钮在旁点开弹框 -->
        <el-form-item label="策略类型">
          <div class="type-row">
            <el-select v-model="form.strategyType" style="width: 260px" @change="onTypeChange">
              <el-option v-for="t in typeOptions" :key="t.type" :value="t.type" :label="t.name" />
            </el-select>
            <el-button v-if="hasSummary" link type="primary" size="small" @click="paramFormRef?.openSummary()">策略逻辑速览</el-button>
            <span v-if="prefillHint" class="muted prefill-hint">{{ prefillHint }}</span>
          </div>
        </el-form-item>
        <StrategyParamForm ref="paramFormRef" :key="`batch-${form.strategyType}`" v-model="form.params" :type="form.strategyType" summary-mode="button" />
        <!-- 开始/结束日期固定同一排（V5.97 用户反馈不换行）；初始资金占第三格 -->
        <div class="bt-grid">
          <el-form-item label="开始日期">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span>
                初始资金
                <el-tooltip content="「按基金自动」= 每只基金按 满仓份额 × 各自开始日期价 × 1.01 计算（与基金详情页同一口径），横截面对比时资金效率才可比；「统一金额」= 整批用同一个数" placement="top">
                  <el-icon class="th-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-radio-group v-model="capitalMode">
              <el-radio-button value="auto">按基金自动</el-radio-button>
              <el-radio-button value="fixed">统一金额</el-radio-button>
            </el-radio-group>
            <el-input-number
              v-if="capitalMode === 'fixed'"
              v-model="fixedCapital"
              :min="1000"
              :controls="false"
              class="capital-input"
            />
          </el-form-item>
        </div>

        <el-form-item label="选择基金">
          <div class="fund-picker">
            <div class="picker-toolbar">
              <el-input
                v-model="pickerKeywordInput"
                placeholder="基金名称或代码，回车查询"
                clearable
                style="width: 220px"
                @clear="applyPickerKeyword"
                @keyup.enter="applyPickerKeyword"
              />
              <el-select
                v-model="pickerTagsSelected"
                multiple
                collapse-tags
                collapse-tags-tooltip
                clearable
                placeholder="按标签快筛（可多选）"
                style="width: 210px"
              >
                <el-option v-for="t in pickerTags" :key="t" :value="t" :label="t" />
              </el-select>
              <span class="muted">已选 {{ pickedCodes.length }} / {{ fundPool.length }} 只（勾选左侧框）</span>
              <el-button link type="primary" size="small" @click="pickAll">
                <el-icon><CircleCheck /></el-icon>&nbsp;全选
              </el-button>
              <el-button link type="primary" size="small" @click="clearPicked">
                <el-icon><CircleClose /></el-icon>&nbsp;清空
              </el-button>
            </div>
            <el-table
              ref="pickerTableRef"
              :data="pickerRows"
              size="small"
              height="240"
              @selection-change="(rows: FundPick[]) => (pickedCodes = rows.map((r) => r.fundCode))"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column prop="fundCode" label="代码" width="90" />
              <el-table-column prop="fundName" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="标签" min-width="140">
                <template #default="{ row }">
                  <el-tag v-for="t in row.tags" :key="t" size="small" type="info" effect="plain" class="pick-tag">{{ t }}</el-tag>
                  <span v-if="!row.tags?.length" class="muted">—</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">开始回测（{{ pickedCodes.length }} 只）</el-button>
      </template>
    </el-dialog>

    <!-- 策略配置详情弹框（批次级参数，与基金详情的详情弹框同一套渲染） -->
    <el-dialog v-model="paramDialogVisible" title="策略配置详情" width="520px">
      <el-descriptions v-if="paramDialogBatch" :column="1" border size="small">
        <el-descriptions-item label="策略">{{ typeName(paramDialogBatch.strategyType) }}</el-descriptions-item>
        <el-descriptions-item label="区间">{{ paramDialogBatch.startDate }} ~ {{ paramDialogBatch.endDate }}</el-descriptions-item>
        <el-descriptions-item label="初始资金">{{ capitalText(paramDialogBatch) }}</el-descriptions-item>
        <el-descriptions-item v-for="item in paramDialogItems" :key="item.label" :label="item.label">
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="paramDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情抽屉：批次口径 + 每只基金的回测记录（与基金详情回测列表同款列） -->
    <el-drawer v-model="drawerVisible" size="94%" :title="drawerTitle">
      <template #header>
        <div class="drawer-head">
          <span>批次 #{{ detail?.batch.id }} · {{ detail ? typeName(detail.batch.strategyType) : '' }}</span>
          <span v-if="detail" class="muted">
            {{ detail.batch.startDate }} ~ {{ detail.batch.endDate }} · 初始资金{{ capitalText(detail.batch) }} ·
            {{ detail.batch.createdBy ?? '--' }} · 发起于 {{ fmtTime(detail.batch.createdAt) }}
          </span>
        </div>
      </template>
      <div v-if="detail" class="drawer-body">
        <!-- 策略配置卡片（V5.97 用户要求：整批一套参数，放表格上方，行内不再重复「策略详情」列） -->
        <el-descriptions :column="3" border size="small" class="batch-param-card">
          <el-descriptions-item label="策略">{{ typeName(detail.batch.strategyType) }}</el-descriptions-item>
          <el-descriptions-item label="区间">{{ detail.batch.startDate }} ~ {{ detail.batch.endDate }}</el-descriptions-item>
          <el-descriptions-item label="初始资金">{{ capitalText(detail.batch) }}</el-descriptions-item>
          <el-descriptions-item v-for="item in batchParamItems" :key="item.label" :label="item.label">
            {{ item.value }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-toolbar">
          <el-select v-model="filterFund" clearable filterable placeholder="全部基金" style="width: 220px" size="small">
            <el-option v-for="code in fundCodesInBatch" :key="code" :value="code" :label="`${fundName(code)} ${code}`" />
          </el-select>
          <el-select v-model="filterTags" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部标签" style="width: 200px" size="small">
            <el-option v-for="t in tagOptionsInBatch" :key="t" :value="t" :label="t" />
          </el-select>
          <span class="muted">共 {{ sortedRecords.length }} 条 · 默认按持仓年化% 从大到小</span>
          <template v-if="detail.batch.status === 0">
            <el-tag type="warning" size="small">运行中</el-tag>
            <span class="num">已跑完 {{ detail.batch.successCount + detail.batch.failCount }} / {{ detail.batch.totalCount }} 只</span>
            <span class="muted">进度每 2 秒自动刷新</span>
          </template>
        </div>
        <el-table :data="sortedRecords" border size="small">
          <el-table-column prop="id" label="ID" width="64" />
          <el-table-column label="区间" min-width="172">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column label="基金" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="fund-link" @click="$router.push(`/funds/${row.fundCode}`)">{{ fundName(row.fundCode) }}</span>
              <span class="fund-code">{{ row.fundCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                {{ row.status === 1 ? '成功' : row.status === 2 ? '失败' : '运行中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总收益%" min-width="88" align="right">
            <template #default="{ row }">{{ row.totalReturnPct ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="持仓收益%" min-width="92" align="right">
            <template #default="{ row }">
              <span :class="retClass(row.positionReturnPct)">{{ row.positionReturnPct ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="持仓年化%" min-width="92" align="right">
            <template #default="{ row }">
              <span :class="retClass(row.positionReturnPct)">{{ annualizedFromPct(row.positionReturnPct, row.startDate, row.endDate) ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最大回撤%" min-width="92" align="right">
            <template #default="{ row }">{{ row.maxDrawdownPct ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="tradeCount" label="交易数" min-width="74" align="right" />
          <el-table-column label="平均仓位份额" min-width="106" align="right">
            <template #default="{ row }">{{ row.avgPositionShare ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="持有总收益%" min-width="100" align="right">
            <template #default="{ row }">
              <span :class="retClass(row.benchTotalReturnPct)">{{ row.benchTotalReturnPct ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="持有最大回撤%" min-width="112" align="right">
            <template #default="{ row }">{{ row.benchMaxDrawdownPct ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="持有年化%" min-width="92" align="right">
            <template #default="{ row }">
              <span :class="retClass(row.benchTotalReturnPct)">{{ annualizedFromPct(row.benchTotalReturnPct, row.startDate, row.endDate) ?? '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="失败原因" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMsg || '--' }}</template>
          </el-table-column>
          <!-- 与基金详情回测列表同一套三件套（用户拍板）；宽 204 = 三按钮 + 间距 + 内边距（见 frontend-ui 3.3 公式） -->
          <el-table-column label="操作" width="204" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="userStore.can(PERM.ACTION_STRATEGY) && row.status === 1"
                size="small"
                type="primary"
                plain
                @click="applyRecord(row)"
              >应用</el-button>
              <el-button size="small" @click="$router.push(`/backtest/${row.id}`)">结果</el-button>
              <el-button
                v-if="userStore.can(PERM.ACTION_STRATEGY) && row.status !== 0"
                size="small"
                type="danger"
                plain
                @click="removeRecord(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, QuestionFilled } from '@element-plus/icons-vue'
import { annualizedFromPct } from '@/utils/metrics'
import { STRATEGY_TYPE_NAMES, describeStrategyParams } from '@/utils/strategyParams'
import { PERM } from '@/utils/permissions'
import { useUserStore } from '@/stores/user'
import StrategyParamForm from '@/components/strategy/StrategyParamForm.vue'
import { addStrategy, allStrategyConfigs, deleteBacktest, strategyTypes, updateStrategy, type StrategyConfig, type StrategyTypeVO } from '@/api/strategy'
import { batchBacktestDetail, batchBacktestPage, createBatchBacktest, type BacktestBatch, type BacktestBatchDetail, type BatchBacktestRequest } from '@/api/batchBacktest'
import type { BacktestRecord } from '@/api/strategy'
import { marketSignalOverview } from '@/api/marketSignals'

const userStore = useUserStore()
const canRun = computed(() => userStore.can(PERM.ACTION_BATCH_BACKTEST))

// ===== 批次列表 =====
const rows = ref<BacktestBatch[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const hasRunning = computed(() => rows.value.some((r) => r.status === 0))

async function load() {
  loading.value = true
  try {
    const result = await batchBacktestPage(page.value, size.value)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/** 注册表优先（名字最准），注册表外的（已下线/未加载）回退公共映射 */
const typeOptions = ref<StrategyTypeVO[]>([])
function typeName(type: string): string {
  return typeOptions.value.find((t) => t.type === type)?.name ?? STRATEGY_TYPE_NAMES[type] ?? type
}

function fmtTime(value: string | null): string {
  return value ? value.replace('T', ' ').substring(0, 19) : '--'
}

function capitalText(batch: BacktestBatch): string {
  return batch.initialCapital == null ? '按基金自动（满仓份额×开始日价×1.01）' : `统一 ${batch.initialCapital} 元`
}

/** 收益率着色：正红负绿（红涨绿跌） */
function retClass(value: number | null): string {
  if (value == null) {
    return ''
  }
  return value >= 0 ? 'text-up' : 'text-down'
}

// ===== 进度轮询：有运行中批次（或抽屉开着运行中批次）时每 2.5 秒刷新 =====
let timer: number | null = null

function pollTick() {
  const listDirty = rows.value.some((r) => r.status === 0)
  const detailDirty = drawerVisible.value && detail.value?.batch.status === 0
  if (!listDirty && !detailDirty) {
    return
  }
  if (listDirty) {
    load()
  }
  if (detailDirty && detail.value) {
    refreshDetail(detail.value.batch.id, true)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadTypes(), loadFundPool()])
  timer = window.setInterval(pollTick, 2500)
})

// ===== 自选池与标签（一次加载，发起弹窗与批次详情的标签筛选共用）=====
const fundPool = ref<FundPick[]>([])
/** 基金代码 → 标签（市场信号总览自带 tags，一次调用拿全，避免逐基金查标签） */
const fundTags = computed(() => {
  const map = new Map<string, string[]>()
  fundPool.value.forEach((f) => map.set(f.fundCode, f.tags))
  return map
})

async function loadFundPool() {
  try {
    const overview = await marketSignalOverview('10y')
    fundPool.value = overview.rows.map((r) => ({ fundCode: r.fundCode, fundName: r.fundName, tags: r.tags }))
  } catch {
    fundPool.value = []
  }
}

onUnmounted(() => {
  if (timer != null) {
    window.clearInterval(timer)
  }
})

async function loadTypes() {
  try {
    typeOptions.value = await strategyTypes()
  } catch {
    typeOptions.value = []
  }
}

// ===== 发起批量回测 =====
const dialogVisible = ref(false)
const submitting = ref(false)
const capitalMode = ref<'auto' | 'fixed'>('auto')
const fixedCapital = ref<number | null>(null)
const form = ref<{ strategyType: string; params: Record<string, unknown>; startDate: string; endDate: string }>({
  strategyType: '',
  params: {},
  startDate: '',
  endDate: ''
})

interface FundPick {
  fundCode: string
  fundName: string
  tags: string[]
}

/** 选择基金区：关键词模糊查询（V6.03 用户口径，与全局筛选同风格——输入值/生效值分离，回车或清空生效） */
const pickerKeywordInput = ref('')
const appliedPickerKeyword = ref('')
/** 标签快筛（V6.05 改多选）：多个标签之间是"任一命中"（与市场信号页同口径） */
const pickerTagsSelected = ref<string[]>([])
const pickedCodes = ref<string[]>([])
// eslint 无；此处仅调 toggleRowSelection / clearSelection，ElTable 泛型实例类型 vue-tsc 推不动，按组件实例弱类型处理
const pickerTableRef = ref<{
  toggleRowSelection: (row: unknown, selected: boolean) => void
  clearSelection: () => void
} | null>(null)

const pickerTags = computed(() => {
  const set = new Set<string>()
  fundPool.value.forEach((f) => f.tags?.forEach((t) => set.add(t)))
  return [...set].sort()
})

const pickerRows = computed(() => {
  const kw = appliedPickerKeyword.value.trim().toLowerCase()
  return fundPool.value.filter((f) => {
    // 关键词：基金名称或代码模糊匹配（V6.03）
    if (kw && !f.fundCode.toLowerCase().includes(kw) && !f.fundName.toLowerCase().includes(kw)) {
      return false
    }
    // 标签快筛（多选，任一命中即保留；与市场信号页同口径）：与关键词组合
    if (pickerTagsSelected.value.length > 0
        && !(f.tags ?? []).some((t) => pickerTagsSelected.value.includes(t))) {
      return false
    }
    return true
  })
})

/** 回车/清空后把输入值应用到筛选 */
function applyPickerKeyword() {
  appliedPickerKeyword.value = pickerKeywordInput.value
}

function pickAll() {
  pickerRows.value.forEach((r) => pickerTableRef.value?.toggleRowSelection(r, true))
}

/**
 * 清空已选（V6.04 修 bug）：只把 pickedCodes 置空只会清掉计数与按钮文案，
 * **表格内部的勾选态仍在**（ElTable 自持一份选中行），必须调 clearSelection() 才能真正取消勾选——
 * 用户反馈："全选后基金都勾选了，但清空的时候勾选没有取消"。
 */
function clearPicked() {
  pickerTableRef.value?.clearSelection()
  pickedCodes.value = []
}

async function openDialog() {
  form.value.strategyType = typeOptions.value[0]?.type ?? ''
  form.value.params = {}
  form.value.startDate = ''
  form.value.endDate = ''
  capitalMode.value = 'auto'
  fixedCapital.value = null
  prefillHint.value = ''
  pickedCodes.value = []
  pickerTagsSelected.value = []
  pickerKeywordInput.value = ''
  appliedPickerKeyword.value = ''
  dialogVisible.value = true
  // 默认类型也回填上一次批量配置（与手动切换同一逻辑）
  if (form.value.strategyType) {
    await onTypeChange()
  }
  if (fundPool.value.length === 0) {
    await loadFundPool()
    if (fundPool.value.length === 0) {
      ElMessage.warning('自选池加载失败，请关闭后重试')
    }
  }
}

/** 参数表单引用（速览弹框由本页按钮触发，见 StrategyParamForm.openSummary） */
const paramFormRef = ref<{ openSummary: () => void } | null>(null)
/** 当前类型是否有速览内容（MA_TP_GRID 等四种有，未识别类型隐藏按钮） */
const hasSummary = computed(() =>
  ['MA_BREAK', 'MA_TP_GRID', 'OSC_UP'].includes(form.value.strategyType) ||
  ['DIV_GRID', 'NDX_GRID', 'PYRAMID_GRID', 'INV_PYRAMID_GRID'].includes(form.value.strategyType)
)
/** 回填提示（该策略上一次批量的参数与区间），空 = 没有历史批量 */
const prefillHint = ref('')

/**
 * 换策略类型：自动回填该策略**上一次批量**的配置（V5.98 用户口径）——参数、区间、初始资金口径一体回填；
 * 没有历史批量则用表单默认值。取数走批次分页接口按类型过滤取第 1 条（id 倒序即最近一次）。
 */
async function onTypeChange() {
  prefillHint.value = ''
  let hit: BacktestBatch | null = null
  try {
    const result = await batchBacktestPage(1, 1, form.value.strategyType)
    hit = result.records[0] ?? null
  } catch {
    hit = null
  }
  if (hit) {
    let params: Record<string, unknown> = {}
    try {
      params = JSON.parse(hit.params) as Record<string, unknown>
    } catch {
      params = {}
    }
    form.value.params = params
    form.value.startDate = hit.startDate
    form.value.endDate = hit.endDate
    if (hit.initialCapital != null) {
      capitalMode.value = 'fixed'
      fixedCapital.value = hit.initialCapital
    } else {
      capitalMode.value = 'auto'
      fixedCapital.value = null
    }
    prefillHint.value = `已回填上次批量 #${hit.id}（${hit.startDate} ~ ${hit.endDate}）的参数与区间`
  } else {
    form.value.params = {}
    form.value.startDate = ''
    form.value.endDate = ''
    capitalMode.value = 'auto'
    fixedCapital.value = null
  }
}

async function submit() {
  if (!form.value.strategyType) {
    ElMessage.warning('请选择策略类型')
    return
  }
  if (pickedCodes.value.length === 0) {
    ElMessage.warning('请至少勾选一只基金')
    return
  }
  if (!form.value.startDate || !form.value.endDate) {
    ElMessage.warning('请选择回测区间')
    return
  }
  if (capitalMode.value === 'fixed' && !(Number(fixedCapital.value) > 0)) {
    ElMessage.warning('统一初始资金须大于 0（或切回「按基金自动」）')
    return
  }
  const request: BatchBacktestRequest = {
    strategyType: form.value.strategyType,
    params: form.value.params,
    startDate: form.value.startDate,
    endDate: form.value.endDate,
    initialCapital: capitalMode.value === 'fixed' ? Number(fixedCapital.value) : null,
    fundCodes: pickedCodes.value
  }
  submitting.value = true
  try {
    const batchId = await createBatchBacktest(request)
    ElMessage.success(`批次 #${batchId} 已提交，正在后台执行`)
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

// ===== 策略配置详情（批次级 + 记录级共用渲染） =====
const paramDialogVisible = ref(false)
const paramDialogBatch = ref<BacktestBatch | null>(null)
const paramDialogItems = ref<{ label: string; value: string }[]>([])


function openParamDialog(row: BacktestBatch) {
  paramDialogBatch.value = row
  paramDialogItems.value = describeStrategyParams(row.strategyType, row.params)
  paramDialogVisible.value = true
}

// ===== 批次详情抽屉 =====
const drawerVisible = ref(false)
const detail = ref<BacktestBatchDetail | null>(null)
const detailLoading = ref(false)
const drawerTitle = computed(() => (detail.value ? `批次 #${detail.value.batch.id} 回测明细` : '批次详情'))

/** 基金名（用选择器里那份自选池；没加载到回退代码） */
function fundName(code: string): string {
  return fundPool.value.find((f) => f.fundCode === code)?.fundName ?? code
}

// ===== 抽屉内筛选与排序（V5.97）：基金下拉 + 标签多选；默认按持仓年化% 从大到小（空值恒最后）=====
const filterFund = ref<string | null>(null)
const filterTags = ref<string[]>([])
const batchParamItems = computed(() =>
  detail.value ? describeStrategyParams(detail.value.batch.strategyType, detail.value.batch.params) : []
)

const fundCodesInBatch = computed(() => (detail.value ? [...new Set(detail.value.records.map((r) => r.fundCode))] : []))

const tagOptionsInBatch = computed(() => {
  const set = new Set<string>()
  fundCodesInBatch.value.forEach((code) => fundTags.value.get(code)?.forEach((t) => set.add(t)))
  return [...set].sort()
})

const sortedRecords = computed(() => {
  if (!detail.value) {
    return []
  }
  const filtered = detail.value.records.filter((r) => {
    if (filterFund.value && r.fundCode !== filterFund.value) {
      return false
    }
    if (filterTags.value.length > 0 && !(fundTags.value.get(r.fundCode) ?? []).some((t) => filterTags.value.includes(t))) {
      return false
    }
    return true
  })
  const annual = (r: BacktestRecord) => annualizedFromPct(r.positionReturnPct, r.startDate, r.endDate)
  return [...filtered].sort((a, b) => {
    const av = annual(a)
    const bv = annual(b)
    if (av == null && bv == null) return 0
    if (av == null) return 1
    if (bv == null) return -1
    return bv - av
  })
})

async function openDetail(row: BacktestBatch) {
  drawerVisible.value = true
  filterFund.value = null
  filterTags.value = []
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await batchBacktestDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

/** 轮询刷新抽屉（静默，不转 loading 免得表格闪） */
async function refreshDetail(id: number, silent: boolean) {
  if (!silent) {
    detailLoading.value = true
  }
  try {
    detail.value = await batchBacktestDetail(id)
  } finally {
    if (!silent) {
      detailLoading.value = false
    }
  }
}

// ===== 记录级操作：应用 / 结果（跳结果页）/ 删除 —— 与基金详情回测列表同一套语义 =====
/** 全量策略配置（应用时判断该基金是否已有同类型配置） */
const configPool = ref<StrategyConfig[]>([])

async function applyRecord(row: BacktestRecord) {
  let params: Record<string, unknown>
  try {
    params = JSON.parse(row.params) as Record<string, unknown>
  } catch {
    ElMessage.error('回测参数解析失败，无法应用')
    return
  }
  const typeNameText = typeName(row.strategyType)
  let configs = configPool.value
  if (configs.length === 0) {
    configs = await allStrategyConfigs()
    configPool.value = configs
  }
  const existing = configs.find((s) => s.fundCode === row.fundCode && s.strategyType === row.strategyType)
  if (existing) {
    await ElMessageBox.confirm(
      `基金 ${row.fundCode} 已配置「${typeNameText}」策略。应用后将按本次回测参数覆盖其参数（启用状态与备注保持不变）。继续？`,
      '应用回测策略',
      { type: 'warning', confirmButtonText: '覆盖参数', cancelButtonText: '取消' }
    )
    await updateStrategy(existing.id, { strategyType: row.strategyType, params })
    ElMessage.success(`已按回测参数更新 ${row.fundCode} 的「${typeNameText}」策略`)
  } else {
    await ElMessageBox.confirm(
      `将按本次回测参数为基金 ${row.fundCode} 新增「${typeNameText}」策略配置（默认启用）。继续？`,
      '应用回测策略',
      { type: 'info', confirmButtonText: '新增策略', cancelButtonText: '取消' }
    )
    await addStrategy(row.fundCode, { strategyType: row.strategyType, params })
    ElMessage.success(`已为 ${row.fundCode} 新增「${typeNameText}」策略`)
  }
}

async function removeRecord(row: BacktestRecord) {
  await ElMessageBox.confirm(
    `确认删除回测 #${row.id}（${fundName(row.fundCode)} ${row.startDate} ~ ${row.endDate}）？删除后不可恢复；批次的成功/失败计数是发起时的快照，不会随之减少。`,
    '删除回测记录',
    { type: 'warning' }
  )
  const deleted = await deleteBacktest(row.id)
  if (!deleted) {
    ElMessage.warning('回测记录不存在或已被删除')
  } else {
    ElMessage.success('回测记录已删除')
  }
  if (detail.value) {
    await refreshDetail(detail.value.batch.id, true)
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
}
.card-header > span:first-child {
  font-weight: var(--q-font-weight-medium);
}
.card-header .el-button {
  margin-left: auto;
}
.batch-progress {
  margin-top: var(--q-space-1);
}
.batch-done-tag {
  margin-left: var(--q-space-2);
}
.sub-line {
  font-size: var(--q-font-size-xs);
  line-height: 1.4;
}
.fund-picker {
  width: 100%;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  margin-bottom: var(--q-space-2);
}
.pick-tag {
  margin-right: var(--q-space-1);
}
.drawer-head {
  display: flex;
  align-items: baseline;
  gap: var(--q-space-4);
  flex-wrap: wrap;
}
.drawer-head > span:first-child {
  font-weight: var(--q-font-weight-medium);
}
.running-tip {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  margin-bottom: var(--q-space-3);
}
.fund-link {
  cursor: pointer;
  color: var(--q-color-primary);
  margin-right: var(--q-space-2);
}
.fund-code {
  color: var(--q-color-text-secondary);
  font-size: var(--q-font-size-xs);
}
.type-row {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  width: 100%;
}
.prefill-hint {
  font-size: var(--q-font-size-xs);
}
.bt-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.3fr);
  gap: var(--q-space-3);
}
.batch-param-card {
  margin-bottom: var(--q-space-3);
}
.detail-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  margin-bottom: var(--q-space-3);
  flex-wrap: wrap;
}
.capital-input {
  width: 180px;
  margin-left: var(--q-space-3);
}
</style>
