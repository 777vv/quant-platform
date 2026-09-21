<template>
  <div class="page ai-usage-page">
    <!-- 每日额度（全局一份，与厂商无关） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>每日额度</span>
          <el-tag v-if="activeText" size="small" type="info" effect="plain">{{ activeText }}</el-tag>
        </div>
      </template>
      <el-form :model="quotaForm" label-width="102px">
        <el-row :gutter="16">
          <el-col :span="8" :xs="24">
            <el-form-item label="token 上限">
              <el-input-number
                v-model="quotaForm.dailyTokenLimit"
                :min="0"
                :step="10000"
                :controls="false"
                placeholder="0 = 不限制"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="费用上限">
              <el-input-number
                v-model="quotaForm.dailyCostLimit"
                :min="0"
                :step="1"
                :precision="2"
                :controls="false"
                placeholder="元，0 = 不限制"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8" :xs="24">
            <el-form-item label="预警百分比">
              <el-input-number
                v-model="quotaForm.warnPercent"
                :min="0"
                :max="100"
                :step="5"
                :controls="false"
                placeholder="0 = 不预警"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-note">
          额度是<b>全局一份</b>（不跟厂商走，切厂商不会改动它），按 <b>自然日 00:00</b> 重置：token 与费用任一达到上限
          即拒绝新提问（AI 浮窗会禁用输入并说明原因），且超限时不会调用上游、不产生费用。
          单价是<b>按厂商各存一份</b>的，在【平台配置 → AI 模型配置】里维护——所以换了厂商，费用额度会按新厂商的单价计。
        </div>
        <el-form-item class="form-actions">
          <el-button type="primary" :loading="savingQuota" @click="saveQuota">保存额度</el-button>
          <span v-if="quota">当前额度：token {{ quotaText(quota.dailyTokenLimit) }} / 费用 {{ quotaCostText(quota.dailyCostLimit) }}</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 用量与费用明细 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用量与费用</span>
          <span class="muted">一次咨询一行流水（工具调用的多轮已累加）；费用按各厂商单价快照估算，以厂商账单为准</span>
        </div>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="今日 token">
          {{ formatTokens(usage?.totalTokens ?? 0) }}
          <span class="muted">（输入 {{ formatTokens(usage?.promptTokens ?? 0) }} / 输出 {{ formatTokens(usage?.completionTokens ?? 0) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="今日费用">¥{{ formatCost(usage?.cost ?? 0) }}</el-descriptions-item>
        <el-descriptions-item label="今日次数">
          {{ usage?.requestCount ?? 0 }}
          <span class="muted">（计费对话 {{ usage?.chatCount ?? 0 }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="额度使用">
          {{ usage?.usedPercent ?? 0 }}%
          <span class="muted">（预警线 {{ usage?.warnPercent ?? 0 }}%）</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="usage?.hint"
        class="usage-hint"
        :type="usage.overLimit ? 'error' : 'warning'"
        :closable="false"
        show-icon
        :title="usage.hint"
      />

      <ChartPanel :option="usageChartOption" height="220px" />

      <div class="usage-toolbar">
        <span class="muted">近 7 日：柱=token，线=费用（元）</span>
        <div class="usage-toolbar-actions">
          <el-date-picker
            v-model="usageDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="按日期筛选明细"
            clearable
            @change="reloadUsageLogs"
          />
          <el-button :loading="loadingUsage" @click="loadUsage">刷新</el-button>
        </div>
      </div>

      <el-table :data="usageLogs" size="small" row-key="id">
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="会话" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sessionTitle || '（无会话）' }}</template>
        </el-table-column>
        <el-table-column label="用途" min-width="110">
          <template #default="{ row }">
            {{ row.bizName }}
            <span v-if="row.rounds > 1" class="muted">· {{ row.rounds }} 轮</span>
          </template>
        </el-table-column>
        <el-table-column label="厂商" min-width="90">
          <template #default="{ row }">{{ row.providerName }}</template>
        </el-table-column>
        <el-table-column prop="model" label="模型" min-width="120" show-overflow-tooltip />
        <el-table-column label="输入" min-width="90" align="right" class="num">
          <template #default="{ row }">{{ row.promptTokens.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="输出" min-width="90" align="right" class="num">
          <template #default="{ row }">{{ row.completionTokens.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="合计" min-width="90" align="right" class="num">
          <template #default="{ row }">{{ row.totalTokens.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="费用" min-width="90" align="right" class="num">
          <template #default="{ row }">¥{{ formatCost(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="!row.success" type="danger" size="small">{{ row.errorMsg || '失败' }}</el-tag>
            <el-tag v-else-if="row.estimated" type="warning" size="small">估算</el-tag>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="60" description="还没有用量记录：向 AI 助手提问后这里会出现明细" />
        </template>
      </el-table>

      <div class="usage-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="usageTotal"
          :current-page="usagePage"
          :page-size="usageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onUsagePage"
          @size-change="onUsageSize"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  aiConfig,
  aiQuota,
  aiUsageLogs,
  aiUsageSummary,
  aiUsageToday,
  saveAiQuota,
  type AiConfigVO,
  type AiQuotaVO,
  type AiUsageDayVO,
  type AiUsageLogVO,
  type AiUsageTodayVO
} from '@/api/ai'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import { formatCost, formatTokens } from '@/utils/format'
import type { EChartsOption } from 'echarts'
import { AXIS_LABEL, AXIS_LINE, PE_LINE, PRIMARY, SPLIT_LINE, TEXT_SECONDARY } from '@/utils/palette'

/** 当前启用的厂商与模型（额度页给出上下文：额度按谁计量） */
const aiConfigVO = ref<AiConfigVO | null>(null)

/** 每日额度表单（全局一份；null = 保持原值，0 = 不限制） */
const quota = ref<AiQuotaVO | null>(null)
const savingQuota = ref(false)
const quotaForm = reactive({
  dailyTokenLimit: 0 as number | null,
  dailyCostLimit: 0 as number | null,
  warnPercent: 80 as number | null
})

/** 用量：今日概览、近 7 日趋势、明细分页 */
const usage = ref<AiUsageTodayVO | null>(null)
const usageDays = ref<AiUsageDayVO[]>([])
const usageLogs = ref<AiUsageLogVO[]>([])
const usageTotal = ref(0)
const usagePage = ref(1)
const usageSize = ref(20)
const usageDate = ref('')
const loadingUsage = ref(false)

/** 当前厂商文案（如「当前：DeepSeek / deepseek-flash」） */
const activeText = computed(() => {
  const model = aiConfigVO.value?.model
  const provider = aiConfigVO.value?.extras?.provider
  return model ? `当前：${provider ? `${provider} / ` : ''}${model}` : ''
})

/** 加载当前厂商信息 + 额度 + 用量 */
async function loadAll() {
  loadingUsage.value = true
  try {
    const [config, quotaVO, today, days] = await Promise.all([
      aiConfig(),
      aiQuota(),
      aiUsageToday(),
      aiUsageSummary(7)
    ])
    aiConfigVO.value = config
    quota.value = quotaVO
    quotaForm.dailyTokenLimit = quotaVO.dailyTokenLimit ?? 0
    quotaForm.dailyCostLimit = quotaVO.dailyCostLimit ?? 0
    quotaForm.warnPercent = quotaVO.warnPercent ?? 0
    usage.value = today
    usageDays.value = days
  } finally {
    loadingUsage.value = false
  }
  await loadUsageLogs()
}

/** 仅刷新用量部分（明细表右上角的刷新按钮） */
async function loadUsage() {
  loadingUsage.value = true
  try {
    const [today, days] = await Promise.all([aiUsageToday(), aiUsageSummary(7)])
    usage.value = today
    usageDays.value = days
  } finally {
    loadingUsage.value = false
  }
  await loadUsageLogs()
}

/** 保存每日额度（全局一份，保存即生效） */
async function saveQuota() {
  savingQuota.value = true
  try {
    await saveAiQuota({
      dailyTokenLimit: quotaForm.dailyTokenLimit,
      dailyCostLimit: quotaForm.dailyCostLimit,
      warnPercent: quotaForm.warnPercent
    })
    ElMessage.success('每日额度已保存并生效')
    await loadAll()
  } finally {
    savingQuota.value = false
  }
}

/** 明细分页查询（按当前页码/每页/日期条件） */
async function loadUsageLogs() {
  const result = await aiUsageLogs({
    page: usagePage.value,
    size: usageSize.value,
    date: usageDate.value || undefined
  })
  usageLogs.value = result.records
  usageTotal.value = result.total
}

/** 日期筛选变化：回到第 1 页重查 */
function reloadUsageLogs() {
  usagePage.value = 1
  loadUsageLogs()
}

/** 翻页 */
function onUsagePage(page: number) {
  usagePage.value = page
  loadUsageLogs()
}

/** 每页条数变化：回到第 1 页重查 */
function onUsageSize(size: number) {
  usageSize.value = size
  usagePage.value = 1
  loadUsageLogs()
}

/** 额度文案：0/空 = 不限制 */
function quotaText(value: number | null | undefined): string {
  return value && value > 0 ? `${formatTokens(value)} token` : '不限制'
}

/** 费用额度文案：0/空 = 不限制 */
function quotaCostText(value: number | null | undefined): string {
  return value && value > 0 ? `¥${formatCost(value)}` : '不限制'
}

/** 时间展示（yyyy-MM-dd HH:mm） */
function formatTime(time?: string): string {
  return time ? time.replace('T', ' ').substring(0, 16) : '--'
}

/** 近 7 日用量图：柱=token（左轴）、线=费用（右轴）；配色取 palette，网格用百分比 */
const usageChartOption = computed<EChartsOption>(() => {
  const rows = usageDays.value
  return {
    grid: { left: '8%', right: '8%', top: 28, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['token', '费用(元)'], right: 8, top: 0, textStyle: { color: TEXT_SECONDARY } },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.date.slice(5)),
      axisLine: { lineStyle: { color: AXIS_LINE } },
      axisLabel: { color: AXIS_LABEL }
    },
    yAxis: [
      {
        type: 'value',
        name: 'token',
        splitLine: { lineStyle: { color: SPLIT_LINE } },
        axisLabel: { color: AXIS_LABEL }
      },
      { type: 'value', name: '元', splitLine: { show: false }, axisLabel: { color: AXIS_LABEL } }
    ],
    series: [
      {
        name: 'token',
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: { color: PRIMARY },
        data: rows.map((row) => row.totalTokens)
      },
      {
        name: '费用(元)',
        type: 'line',
        yAxisIndex: 1,
        symbol: 'none',
        smooth: true,
        itemStyle: { color: PE_LINE },
        data: rows.map((row) => row.cost)
      }
    ]
  }
})

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
/* 与平台配置页保持同一观感：宽屏居中且几乎铺满（SKILL 3.6.0） */
.ai-usage-page {
  max-width: 1400px;
  margin: 0 auto;
}

/* 卡片标题：品牌色短竖条 + 右侧次要信息（与全站一致） */
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

.card-header .muted {
  font-weight: 400;
}

.form-note {
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  line-height: 1.6;
  margin-bottom: var(--q-space-3);
}

.form-actions {
  margin-bottom: 0;
}

.usage-hint {
  margin-top: var(--q-space-3);
}

/* 图表与明细之间的工具条：左侧口径说明、右侧筛选与刷新 */
.usage-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--q-space-3);
  margin: var(--q-space-3) 0;
}

.usage-toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--q-space-2);
}

.usage-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-3);
}
</style>
