<template>
  <div class="page">
    <!-- 筛选条件：全部可选，改任一条件都回到第 1 页重新查询 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="filterFundCode"
          placeholder="按基金代码筛选"
          clearable
          style="width: 170px"
          @clear="reload(1)"
          @keyup.enter="reload(1)"
        />
        <el-select v-model="filterDirection" placeholder="全部方向" clearable style="width: 120px" @change="reload(1)">
          <el-option v-for="item in DIRECTIONS" :key="item.value" :value="item.value" :label="item.label" />
        </el-select>
        <el-select v-model="filterStrategy" placeholder="全部策略" clearable style="width: 140px" @change="reload(1)">
          <el-option v-for="item in strategyOptions" :key="item.type" :value="item.type" :label="item.name" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          clearable
          style="width: 260px"
          @change="reload(1)"
        />
        <el-button type="primary" @click="reload(1)">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
        <span class="muted">共 {{ total }} 条</span>
      </div>
    </el-card>

    <el-card shadow="never" class="board-card">
      <template #header>
        <div class="card-header">
          <span>信号记录</span>
          <span class="muted">按信号日期倒序；每日 21:00 由定时任务生成，点击行可标记已读并进入基金详情</span>
        </div>
      </template>
      <el-table v-loading="loading" row-key="id" :data="records" size="small" @row-click="openSignal">
        <el-table-column prop="signalDate" label="信号日期" min-width="100" />
        <el-table-column label="方向" min-width="80">
          <template #default="{ row }">
            <el-tag :type="directionTagOf(row.direction)" size="small" effect="dark">
              {{ directionName(row.direction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="基金" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.fundCode }}
            <span v-if="row.fundName" class="muted">{{ row.fundName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="strategyName" label="策略" min-width="100" />
        <el-table-column label="信号价" min-width="96" align="right">
          <template #default="{ row }">
            <span class="num">{{ row.priceAt == null ? '--' : row.priceAt.toFixed(4) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="suggestDesc" label="建议说明" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.suggestDesc || '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="130">
          <template #default="{ row }">
            <el-tag v-if="row.readFlag === 0" type="danger" size="small" effect="plain">未读</el-tag>
            <el-tag v-if="row.notifiedFlag === 1" type="info" size="small" effect="plain">已邮件通知</el-tag>
            <span v-if="row.readFlag !== 0 && row.notifiedFlag !== 1" class="muted">--</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="reload(1)"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { markSignalsRead, signalsPage, strategyTypes, type SignalItem, type StrategyTypeVO } from '@/api/strategy'

/**
 * 信号查询（FR5）：全部历史信号的筛选 + 服务端分页。
 * 与仪表盘「今日信号」卡同源（signal_record 表）；这里是全量可筛选视角，
 * 仪表盘卡只看最近 7 天。点击行 = 标记已读 + 进入基金详情（与仪表盘一致）。
 */

/** 方向选项（与后端信号方向一致） */
const DIRECTIONS = [
  { value: 'BUY', label: '买入' },
  { value: 'SELL', label: '卖出' },
  { value: 'HOLD', label: '持有' }
]

const records = ref<SignalItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

/** 筛选条件：基金代码 / 方向 / 策略 / 日期区间（yyyy-MM-dd） */
const filterFundCode = ref('')
const filterDirection = ref<string | undefined>(undefined)
const filterStrategy = ref<string | undefined>(undefined)
const dateRange = ref<[string, string] | null>(null)

/** 策略选项：从后端策略注册表拉取（不写死，未来加策略自动出现） */
const strategyOptions = ref<StrategyTypeVO[]>([])

/** 方向文案 */
function directionName(direction: string): string {
  return DIRECTIONS.find((item) => item.value === direction)?.label ?? direction
}

/** 方向标签色：买入红 / 卖出绿（红涨绿跌语义）/ 持有中性 */
function directionTagOf(direction: string): 'danger' | 'success' | 'info' {
  if (direction === 'BUY') {
    return 'danger'
  }
  if (direction === 'SELL') {
    return 'success'
  }
  return 'info'
}

/** 按当前筛选条件加载指定页 */
async function load() {
  loading.value = true
  try {
    const result = await signalsPage({
      fundCode: filterFundCode.value.trim() || undefined,
      direction: filterDirection.value,
      strategyType: filterStrategy.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      page: page.value,
      size: size.value
    })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/** 条件变化后回到第 1 页重新查询 */
function reload(targetPage: number) {
  page.value = targetPage
  load()
}

/** 清空全部筛选条件 */
function resetFilter() {
  filterFundCode.value = ''
  filterDirection.value = undefined
  filterStrategy.value = undefined
  dateRange.value = null
  reload(1)
}

/** 点击行：未读则标记已读，并进入该基金详情（与仪表盘信号卡一致） */
async function openSignal(row: SignalItem) {
  if (row.readFlag === 0) {
    await markSignalsRead([row.id])
    row.readFlag = 1
  }
  useRouter().push(`/funds/${row.fundCode}`)
}

onMounted(async () => {
  strategyOptions.value = await strategyTypes().catch(() => [])
  await load()
})
</script>

<style scoped>
.filter-card :deep(.el-card__body) {
  padding: var(--q-space-3) var(--q-space-4);
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--q-space-2);
}

.board-card :deep(.el-card__body) {
  padding: var(--q-space-3) var(--q-space-4);
}

.card-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: var(--q-space-3);
  font-size: var(--q-font-base);
  font-weight: 600;
  color: var(--q-text-primary);
}

.card-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--q-color-primary);
  transform: translateY(-50%);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-3);
}
</style>
