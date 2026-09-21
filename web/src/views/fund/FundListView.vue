<template>
  <div>
    <el-row class="page-toolbar" justify="space-between">
      <div class="toolbar-left">
        <span class="muted">筛选</span>
        <el-input
          v-model="keyword"
          size="small"
          placeholder="代码或名称"
          clearable
          style="width: 170px"
          @input="onKeywordInput"
          @clear="reloadWatch(1)"
          @keyup.enter="reloadWatch(1)"
        />
        <el-select
          v-model="tagFilter"
          size="small"
          clearable
          placeholder="全部标签"
          style="width: 150px"
          @change="reloadWatch(1)"
          @clear="reloadWatch(1)"
        >
          <el-option v-for="tag in tagLibraryList" :key="tag.id" :value="tag.name" :label="tag.name" />
        </el-select>
        <el-button size="small" @click="reloadWatch(1)">查询</el-button>
        <el-button size="small" @click="tagManageVisible = true">标签管理</el-button>
      </div>
      <div class="toolbar-right">
        <span class="muted">已选 {{ selectedFunds.length }}/3</span>
        <el-button size="small" :disabled="selectedFunds.length < 2" @click="gotoCompare">对比走势</el-button>
        <el-button type="primary" @click="openEntry()">记一笔（交易流水）</el-button>
      </div>
    </el-row>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="自选基金" name="watch">
        <el-row class="toolbar" justify="space-between">
          <el-col :span="8">
            <span class="muted">关键词与标签筛选均为服务端筛选，作用于全部分页</span>
          </el-col>
          <el-col :span="3" style="text-align: right">
            <el-button type="primary" @click="$router.push('/import')">导入基金</el-button>
          </el-col>
        </el-row>
        <!-- row-key 必填：筛选/增删后行会被复用，缺 row-key 时单元格内 v-for 的标签会残留上一行的标签 -->
        <el-table
          ref="watchTableRef"
          row-key="fundCode"
          @selection-change="onSelectionChange"
          v-loading="watchLoading"
          :data="watchPage"
          border
          stripe
        >
          <!-- reserve-selection：翻页/换筛选后保留已勾选（最多 3 只用于走势对比） -->
          <el-table-column type="selection" width="42" reserve-selection :selectable="selectableFund" />
          <el-table-column prop="fundCode" label="代码" width="88" />
          <el-table-column prop="fundName" label="名称" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <!-- 名称即详情入口（点击进基金详情），无需再放独立的"详情"按钮 -->
              <span
                class="fund-link"
                :class="{ 'name-holding': row.holding }"
                :title="`查看 ${row.fundName} 详情`"
                @click="$router.push(`/funds/${row.fundCode}`)"
              >{{ row.fundName }}</span>
              <el-tag v-if="row.holding" size="small" type="primary" class="name-flag">持仓</el-tag>
              <el-tag size="small" type="info" effect="plain" class="name-flag">
                {{ row.fundType === 1 ? 'ETF' : '场外' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="跟踪指数" width="150">
            <template #default="{ row }">
              <div v-if="row.indexName" class="index-cell">
                <span class="index-cell-name">{{ row.indexName }}</span>
                <span v-if="row.indexCode" class="index-cell-code">{{ row.indexCode }}</span>
              </div>
              <span v-else class="muted">--</span>
            </template>
          </el-table-column>
          <el-table-column label="标签" width="120">
            <template #default="{ row }">
              <template v-if="fundTagMap[row.fundCode]?.length">
                <el-tag
                  v-for="name in fundTagMap[row.fundCode]"
                  :key="`${row.fundCode}-${name}`"
                  size="small"
                  class="tag-chip"
                >
                  {{ name }}
                </el-tag>
              </template>
              <span v-else class="muted">--</span>
            </template>
          </el-table-column>
          <el-table-column label="最新价/净值" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.lastPrice, 4) }}</template>
          </el-table-column>
          <el-table-column label="涨跌幅%" width="100" align="right">
            <template #default="{ row }">
              <span :class="changeClass(row.changePct)">{{ fmt(row.changePct, 2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规模(亿)" width="95" align="right">
            <template #default="{ row }">
              <el-tooltip v-if="row.fundScale != null" :content="`净资产规模，截止 ${row.fundScaleDate ?? '未知'}`" placement="top">
                <span class="num">{{ row.fundScale.toFixed(2) }}</span>
              </el-tooltip>
              <span v-else class="num">--</span>
            </template>
          </el-table-column>
          <el-table-column label="溢价率" width="95" align="right">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.premiumRate != null"
                :content="`按净值日 ${row.premiumDate} 的收盘价与单位净值计算`"
                placement="top"
              >
                <span class="num">{{ signedPct(row.premiumRate) }}</span>
              </el-tooltip>
              <span v-else class="num">--</span>
            </template>
          </el-table-column>
          <el-table-column label="运作费率" width="100" align="right">
            <template #default="{ row }">
              <el-tooltip v-if="row.opFeeRate != null" :content="feeBreakdown(row)" placement="top">
                <span class="num">{{ row.opFeeRate.toFixed(2) }}%</span>
              </el-tooltip>
              <span v-else class="num">--</span>
            </template>
          </el-table-column>
          <el-table-column label="股息率(TTM)" width="110" align="right">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.dividendYieldTtm != null"
                content="过去 12 个月每份分红 ÷ 最新价（当前时点口径）"
                placement="top"
              >
                <span class="num">{{ row.dividendYieldTtm.toFixed(2) }}%</span>
              </el-tooltip>
              <span v-else class="num muted">--</span>
            </template>
          </el-table-column>
          <el-table-column label="估值百分位%" width="110" align="right">
            <template #default="{ row }">{{ row.valuationPercentile == null ? '--' : row.valuationPercentile }}</template>
          </el-table-column>
          <el-table-column label="已配策略" width="125">
            <template #default="{ row }">
              <template v-if="strategyTags(row.fundCode).length">
                <el-tag
                  v-for="tag in strategyTags(row.fundCode)"
                  :key="`${row.fundCode}-${tag}`"
                  size="small"
                  :type="tag === '网格交易' ? 'primary' : 'success'"
                  class="strategy-tag"
                >
                  {{ tag }}
                </el-tag>
              </template>
              <span v-else class="muted">未配置</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastSyncDate" label="最后同步" width="100">
            <template #default="{ row }">{{ row.lastSyncDate || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :loading="syncingCode === row.fundCode" @click="handleSync(row)">同步</el-button>
              <el-button size="small" @click="openTagEdit(row.fundCode)">标签</el-button>
              <el-button size="small" type="danger" plain @click="handleRemove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="watchPageNo"
            v-model:page-size="watchPageSize"
            :total="watchTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadWatch"
            @size-change="reloadWatch(1)"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="持仓基金" name="hold">
        <el-table
          row-key="fundCode"
          v-loading="holdingLoading"
          :data="filteredHolding"
          :empty-text="tagFilter ? `当前标签「${tagFilter}」下无持仓基金` : '暂无持仓'"
          border
          stripe
        >
          <el-table-column prop="fundCode" label="代码" width="90" />
          <el-table-column prop="fundName" label="名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="持有份额" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.totalShare, 2) }}</template>
          </el-table-column>
          <el-table-column label="成本价" width="90" align="right">
            <template #default="{ row }">{{ fmt(row.avgCostPrice, 4) }}</template>
          </el-table-column>
          <el-table-column label="现价/净值" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.lastPrice, 4) }}</template>
          </el-table-column>
          <el-table-column label="市值" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.marketValue, 2) }}</template>
          </el-table-column>
          <el-table-column label="当日盈亏" width="100" align="right">
            <template #default="{ row }">
              <span :class="changeClass(row.dayPnl)">{{ fmt(row.dayPnl, 2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="浮动盈亏" width="140" align="right">
            <template #default="{ row }">
              <span :class="changeClass(row.floatingPnl)">
                {{ fmt(row.floatingPnl, 2) }}（{{ row.floatingPnlPct == null ? '--' : row.floatingPnlPct }}%）
              </span>
            </template>
          </el-table-column>
          <el-table-column label="已实现盈亏" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.realizedPnl, 2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openEntry(row.fundCode, 1)">录流水</el-button>
              <el-button size="small" @click="$router.push(`/funds/${row.fundCode}`)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <TradeEntryDialog v-model="entryVisible" :preset-fund="entryFund" :preset-type="entryType" @saved="onSaved" />
    <TagManageDialog v-model="tagManageVisible" @changed="loadTags" />
    <FundTagEditDialog v-model="tagEditVisible" :fund-code="tagEditFund" @saved="loadTags" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { allFundTags, holdings, removeFund, syncFund, syncProgress, tagLibrary, watchlistPage } from '@/api/fund'
import type { FundTagVO, HoldingVO, WatchItemVO } from '@/api/fund'
import { allStrategyConfigs } from '@/api/strategy'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'
import TagManageDialog from '@/components/fund/TagManageDialog.vue'
import FundTagEditDialog from '@/components/fund/FundTagEditDialog.vue'

/** 走势对比最多选择数 */
const MAX_COMPARE = 3
import type { StrategyConfig } from '@/api/strategy'

const activeTab = ref('watch')
/** 关键词筛选（服务端 like 匹配代码/名称） */
const keyword = ref('')
/** 自选表当前页数据（服务端分页；不再一次拉全量） */
const watchPage = ref<WatchItemVO[]>([])
/** 自选表分页状态 */
const watchPageNo = ref(1)
const watchPageSize = ref(20)
const watchTotal = ref(0)
/** 自选表实例（分页后清空勾选用） */
const watchTableRef = ref<{ clearSelection: () => void } | null>(null)
const holdingItems = ref<HoldingVO[]>([])
const watchLoading = ref(false)
const holdingLoading = ref(false)
const syncingCode = ref('')
let pollTimer: number | undefined
let keywordTimer: number | undefined

/**
 * 持仓表同样受页头标签筛选约束（筛选控件在标签页之外，对两个标签页一致生效）；
 * 未选标签时返回全部持仓。
 */
const filteredHolding = computed(() => {
  if (!tagFilter.value) {
    return holdingItems.value
  }
  return holdingItems.value.filter((item) => (fundTagMap.value[item.fundCode] ?? []).includes(tagFilter.value))
})

/** 带符号百分比（溢价率用：正为溢价、负为折价） */
function signedPct(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

/**
 * 运作费率明细（悬浮提示用）：管理费 + 托管费 + 销售服务费。
 * 后端保证 opFeeRate 非空时至少有一项有值，缺项按 0 展示便于对账。
 */
function feeBreakdown(row: WatchItemVO): string {
  const part = (label: string, value: number | null | undefined) => `${label} ${(value ?? 0).toFixed(2)}%`
  return `${part('管理费', row.mgmtFeeRate)} + ${part('托管费', row.custFeeRate)} + ${part('销售服务费', row.salesFeeRate)}（年化）`
}

function fmt(value: number | null | undefined, digits: number): string {
  return value === null || value === undefined ? '--' : value.toFixed(digits)
}
function changeClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return ''
  return value > 0 ? 'text-up' : 'text-down'
}

/** 全量策略配置（按基金代码分组，供"已配策略"列展示） */
const router = useRouter()
const strategyMap = ref<Record<string, string[]>>({})

/** 标签：库列表 + 各基金标签映射 + 筛选值 + 两个弹窗状态 */
const tagLibraryList = ref<FundTagVO[]>([])
const fundTagMap = ref<Record<string, string[]>>({})
const tagFilter = ref('')
const tagManageVisible = ref(false)
const tagEditVisible = ref(false)
const tagEditFund = ref('')

/** 自选列表勾选的基金（最多 3 只，用于走势对比） */
const selectedFunds = ref<string[]>([])

function onSelectionChange(rows: WatchItemVO[]) {
  // 超选时保留前 3 只（配合 selectableFund 限制，正常不会超）
  selectedFunds.value = rows.map((r) => r.fundCode).slice(0, MAX_COMPARE)
}

/**
 * 是否允许勾选该行：已达上限时只允许取消已选中的行。
 * 分页后行是「翻页 + 跨页保留勾选」，必须按基金代码判断而不是按行对象。
 */
function selectableFund(row: WatchItemVO): boolean {
  return selectedFunds.value.includes(row.fundCode) || selectedFunds.value.length < MAX_COMPARE
}

function openTagEdit(code: string) {
  tagEditFund.value = code
  tagEditVisible.value = true
}

/** 跳转对比页并带上已选基金 */
function gotoCompare() {
  router.push({ path: '/compare', query: { codes: selectedFunds.value.join(',') } })
}

/** 拉取标签库与基金标签映射（列表展示与筛选共用） */
async function loadTags() {
  tagLibraryList.value = await tagLibrary()
  fundTagMap.value = await allFundTags()
}

/** 快捷记账对话框状态（preset 为空 = 页头"记一笔"，不带预选） */
const entryVisible = ref(false)
const entryFund = ref('')
const entryType = ref(1)

function openEntry(fundCode?: string, tradeType?: number) {
  entryFund.value = fundCode ?? ''
  entryType.value = tradeType ?? 1
  entryVisible.value = true
}

/** 记账成功后刷新持仓与自选（份额/市值可能变化） */
function onSaved() {
  loadWatch()
  loadHoldings()
}

/**
 * 加载自选表当前页（服务端分页 + 服务端筛选）。
 * 关键词与标签必须交给服务端：前端筛选只能筛到当前页的数据。
 */
async function loadWatch() {
  watchLoading.value = true
  try {
    const result = await watchlistPage({
      keyword: keyword.value.trim() || undefined,
      tag: tagFilter.value || undefined,
      page: watchPageNo.value,
      size: watchPageSize.value
    })
    watchPage.value = result.records
    watchTotal.value = result.total
    // 删除末页最后一条后当前页会越界，回退一页重查
    if (result.records.length === 0 && result.total > 0 && watchPageNo.value > 1) {
      watchPageNo.value -= 1
      await loadWatch()
    }
  } finally {
    watchLoading.value = false
  }
}

/** 条件变化后回到指定页重新查询 */
function reloadWatch(targetPage: number) {
  watchPageNo.value = targetPage
  loadWatch()
}

/** 关键词输入防抖（300ms）后回到第 1 页查询，避免每敲一个字都打接口 */
function onKeywordInput() {
  if (keywordTimer) {
    window.clearTimeout(keywordTimer)
  }
  keywordTimer = window.setTimeout(() => reloadWatch(1), 300)
}

/** 拉取全部策略配置并按基金代码归类为展示名列表 */
async function loadStrategies() {
  const configs: StrategyConfig[] = await allStrategyConfigs()
  const grouped: Record<string, string[]> = {}
  configs.forEach((config) => {
    const name = config.strategyName || config.strategyType
    grouped[config.fundCode] = [...(grouped[config.fundCode] ?? []), name]
  })
  strategyMap.value = grouped
}

/** 某基金已配置的策略展示名（未配置返回空数组） */
function strategyTags(fundCode: string): string[] {
  return strategyMap.value[fundCode] ?? []
}

async function loadHoldings() {
  holdingLoading.value = true
  try {
    holdingItems.value = await holdings()
  } finally {
    holdingLoading.value = false
  }
}

function handleSync(row: WatchItemVO) {
  syncingCode.value = row.fundCode
  syncFund(row.fundCode)
    .then((res) => {
      pollTimer = window.setInterval(async () => {
        const p = await syncProgress(res.taskId)
        if (p.status !== 'RUNNING') {
          window.clearInterval(pollTimer)
          syncingCode.value = ''
          if (p.status === 'DONE') {
            ElMessage.success(`${row.fundName}：${p.step}`)
            loadWatch()
          } else {
            ElMessage.error(p.message || '同步失败')
          }
        }
      }, 1200)
    })
    .catch(() => {
      syncingCode.value = ''
    })
}

async function handleRemove(row: WatchItemVO) {
  await ElMessageBox.confirm(
    `确认将 ${row.fundName}(${row.fundCode}) 移出自选？历史数据保留，重新导入可恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  await removeFund(row.fundCode)
  ElMessage.success('已移出自选')
  loadWatch()
}

onMounted(() => {
  // 自选表由服务端分页查询；策略列与标签映射与分页无关，单独拉一次
  loadWatch()
  loadHoldings()
  loadStrategies()
  loadTags()
})

onUnmounted(() => {
  window.clearInterval(pollTimer)
  if (keywordTimer) {
    window.clearTimeout(keywordTimer)
  }
})
</script>

<style scoped>
.index-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.index-cell-name {
  color: var(--q-text-regular);
}

.index-cell-code {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
  font-variant-numeric: tabular-nums;
}

.name-holding {
  font-weight: 600;
}

/* 可点击的基金名称：主色 + 悬停下划线，提示"这是详情入口" */
.fund-link {
  color: var(--q-color-primary);
  cursor: pointer;
}

.fund-link:hover {
  text-decoration: underline;
}

.name-flag {
  margin-left: var(--q-space-1);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-3);
}

.page-toolbar {
  margin-bottom: 8px;
}

.toolbar {
  margin-bottom: var(--q-space-3);
}
.muted {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

.strategy-tag {
  margin-right: 4px;
}
</style>
