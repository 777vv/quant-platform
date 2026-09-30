<template>
  <div class="page">
    <!-- 筛选条件：全部可选，改任一条件都回到第 1 页重新查询 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="filterFundCode"
          placeholder="按基金代码筛选"
          clearable
          style="width: 180px"
          @clear="reload(1)"
          @keyup.enter="reload(1)"
        />
        <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 130px" @change="reload(1)">
          <el-option v-for="item in TRADE_TYPES" :key="item.value" :value="item.value" :label="item.label" />
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
        <span class="muted">共 {{ total }} 笔</span>
        <el-button type="primary" class="entry-btn" @click="entryVisible = true">记一笔（交易流水）</el-button>
      </div>
    </el-card>

    <!-- 流水明细（只读：更正经详情页的流水 Tab 操作，避免在总览列表里误删） -->
    <el-card shadow="never" class="board-card">
      <template #header>
        <div class="card-header">
          <span>交易流水</span>
          <span class="muted">按交易日期倒序；金额单位为元，价格保留 4 位小数</span>
        </div>
      </template>
      <el-table v-loading="loading" row-key="id" :data="records" size="small">
        <el-table-column prop="tradeDate" label="交易日期" min-width="120" />
        <el-table-column label="类型" min-width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagOf(row.tradeType)" size="small">{{ typeTextOf(row.tradeType) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 基金名称列作为弹性列吸收剩余宽度：长名称天然受用，比让"备注"吃掉空白合理 -->
        <el-table-column label="基金" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.fundCode">
              {{ row.fundCode }}
              <span v-if="fundNameOf(row.fundCode)" class="muted">{{ fundNameOf(row.fundCode) }}</span>
            </template>
            <span v-else class="muted">账户资金（无基金）</span>
          </template>
        </el-table-column>
        <el-table-column label="成交价" min-width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ hasPrice(row) ? row.price.toFixed(4) : '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成交数量" min-width="130" align="right">
          <template #default="{ row }">
            <span class="num">{{ hasPrice(row) ? row.share.toFixed(2) : '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatAmount(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="手续费" min-width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ formatAmount(row.fee) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.note || '--' }}</template>
        </el-table-column>
        <el-table-column label="录入时间" min-width="150">
          <template #default="{ row }">
            <span class="muted">{{ (row.createdAt ?? '').replace('T', ' ').substring(0, 16) || '--' }}</span>
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
    <!-- 记一笔（V5.52）：录入入口从基金池页挪到本页，保存后回到第 1 页展示新流水 -->
    <TradeEntryDialog v-model="entryVisible" @saved="onSaved" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageTrades, watchlist, type TradeFlow } from '@/api/fund'
import { formatAmount } from '@/utils/format'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'

/**
 * 交易流水总览（FR2）：把全部流水集中列出，支持按基金/类型/日期区间筛选 + 服务端分页。
 * 只读页面——更正与删除仍在基金详情页的「交易流水」Tab 内完成，避免在总览列表里误删。
 */

/** 交易类型选项（与后端 TradeTypeEnum 一致） */
const TRADE_TYPES = [
  { value: 1, label: '买入' },
  { value: 2, label: '卖出' },
  { value: 3, label: '分红' },
  { value: 4, label: '转入' },
  { value: 5, label: '转出' }
]

const records = ref<TradeFlow[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

/** 筛选条件：基金代码 / 交易类型 / 日期区间（yyyy-MM-dd） */
const filterFundCode = ref('')
const filterType = ref<number | undefined>(undefined)
const dateRange = ref<[string, string] | null>(null)

/** 基金代码 → 名称（用于流水里的基金列；已移出自选的基金取不到名称，只显示代码） */
const fundNameMap = ref<Record<string, string>>({})

/** 交易类型文案 */
function typeTextOf(tradeType: number): string {
  return TRADE_TYPES.find((item) => item.value === tradeType)?.label ?? '未知'
}

/** 交易类型标签色：买卖用涨跌语义无关的 EP 语义色，划转/分红用中性色区分 */
function typeTagOf(tradeType: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (tradeType) {
    case 1:
      return 'danger'
    case 2:
      return 'success'
    case 3:
      return 'warning'
    default:
      return 'info'
  }
}

/** 是否展示价格与数量：只有买入/卖出有成交价与份额，分红与划转恒为 0 */
function hasPrice(row: TradeFlow): boolean {
  return row.tradeType === 1 || row.tradeType === 2
}

/** 基金名称查询（取不到时返回空串，模板显示为只留代码） */
function fundNameOf(fundCode: string): string {
  return fundNameMap.value[fundCode] ?? ''
}

/** 按当前筛选条件加载指定页 */
async function load() {
  loading.value = true
  try {
    const result = await pageTrades({
      fundCode: filterFundCode.value.trim() || undefined,
      tradeType: filterType.value,
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
  filterType.value = undefined
  dateRange.value = null
  reload(1)
}

/** 记一笔对话框（V5.52：录入入口从基金池页挪到本页） */
const entryVisible = ref(false)

/** 保存成功后回到第 1 页：新流水按交易日期倒序排在最前，直接可见 */
function onSaved() {
  reload(1)
}

onMounted(async () => {
  // 基金名称映射：自选池（含已移出但仍有流水的基金取不到名称，属预期）
  const funds = await watchlist().catch(() => [])
  const map: Record<string, string> = {}
  funds.forEach((fund) => {
    map[fund.fundCode] = fund.fundName
  })
  fundNameMap.value = map
  await load()
})
</script>

<style scoped>
.filter-card :deep(.el-card__body) {
  padding: var(--q-space-3) var(--q-space-4);
}

.entry-btn {
  margin-left: auto;
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
