<template>
  <!-- 自适应网格：容器有多宽就排几列（每列至少 300px），避免每个参数独占一整行留出大片空白 -->
  <div class="param-grid">
    <template v-if="type === 'GRID'">
      <el-form-item>
        <template #label><ParamLabel text="网格模式" help="等差：每格价格差固定（upper−lower）÷格数；等比：每格按固定百分比（上沿÷下沿 开格数次方）。价格波动大选等比，窄幅震荡选等差。" /></template>
        <el-radio-group v-model="form.mode">
          <el-radio-button value="arithmetic">等差</el-radio-button>
          <el-radio-button value="geometric">等比</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="网格上沿" help="网格区间的最高价。现价上穿此价视为突破上沿 → 建议清仓离场（不再按格交易）。" /></template>
        <el-input-number v-model="form.upper" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="网格下沿" help="网格区间的最低价。现价跌破此价视为区间失效 → 只观望不买入；须满足 0 &lt; 下沿 &lt; 上沿。" /></template>
        <el-input-number v-model="form.lower" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="格数" help="上下沿之间切成几格，决定触发密度：格数越多、每格越小、交易越频繁（2~100，默认 10）。" /></template>
        <el-input-number v-model="form.grids" :min="2" :max="100" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="每格份额" help="每下穿一格建议买入、每上穿一格建议卖出的份额（份）。这是网格的单次交易量，不是总仓位。" /></template>
        <el-input-number v-model="form.sharePerGrid" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="底仓份额" help="回测开始时先建立的初始持仓份额（仅在回测首日执行一次），用于模拟「手上已有仓位」的状态。" /></template>
        <el-input-number v-model="form.basePosition" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="type === 'VAL_PERCENTILE'">
      <el-form-item>
        <template #label><ParamLabel text="低估阈值%" help="跟踪指数 PE 百分位低于此值视为低估 → 建议加仓。必须小于高估阈值（1~49）。" /></template>
        <el-input-number v-model="form.lowPct" :precision="1" :min="1" :max="49" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="高估阈值%" help="PE 百分位高于此值视为高估 → 建议减仓。必须大于低估阈值（51~99）。" /></template>
        <el-input-number v-model="form.highPct" :precision="1" :min="51" :max="99" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="分档数" help="把低估区切成几档来分批加仓（同理高估区减仓）：档数越多、每次加减越少（1~10）。" /></template>
        <el-input-number v-model="form.steps" :min="1" :max="10" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="回看窗口(年)" help="算 PE 百分位时回看多少年的估值历史。窗口越长越平滑但越迟钝（1~30，默认 8）。" /></template>
        <el-input-number v-model="form.windowYears" :min="1" :max="30" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="每档份额" help="每跨越一档低估/高估时建议买入/卖出的份额（份）。" /></template>
        <el-input-number v-model="form.sharePerStep" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="底仓份额" help="建仓的起始份额：低于低估阈值时至少持这么多，回测首日先建立。" /></template>
        <el-input-number v-model="form.basePosition" :min="0" :controls="false" />
      </el-form-item>
    </template>

    <template v-else-if="type === 'OSC_UP'">
      <el-form-item>
        <template #label><ParamLabel text="初始仓位份额" help="仅回测首日生效：开始回测时先按这个份额买入建仓（份），后续信号从「已持有初始仓位」起步；填 0 等于不建仓。取值 0 ~ 满仓份额。" /></template>
        <el-input-number v-model="form.initialShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="底仓份额" help="卖出下限：建议卖出后的持仓份额不低于这个数（份）。须小于满仓份额。" /></template>
        <el-input-number v-model="form.baseShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="满仓份额" help="买入上限：建议买入后的持仓份额不超过这个数（份）。请让它与你的可用资金匹配，回测时本金必须买得起它。" /></template>
        <el-input-number v-model="form.fullShare" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="K线天数" help="向前回看多少根 K 线（含当日，2~500，默认 60）。它同时是涨跌对比区间的封顶：对比区间取「K线天数那根」与「上次实际交易那根」中更靠近今天的那根作为起点。" /></template>
        <el-input-number v-model="form.windowDays" :min="2" :max="500" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="上涨减仓%" help="现价较对比区间内的【最低点】上涨超过这个百分比 → 建议卖出「卖出份额」份（默认 5%）。" /></template>
        <el-input-number v-model="form.riseReducePct" :precision="1" :min="0.1" :max="99.9" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="下跌加仓%" help="现价较对比区间内的【最高点】下跌超过这个百分比 → 建议买入「买入份额」份（默认 5%）。" /></template>
        <el-input-number v-model="form.fallAddPct" :precision="1" :min="0.1" :max="99.9" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="买入份额" help="每次加仓信号的买入份额（份）。建议不超过「满仓份额 − 底仓份额」，否则每次都会被满仓上限截断。" /></template>
        <el-input-number v-model="form.buyShare" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="卖出份额" help="每次减仓信号的卖出份额（份）。买入份额小于「底仓份额」时不会被底仓下限截断太多。" /></template>
        <el-input-number v-model="form.sellShare" :min="1" :controls="false" />
      </el-form-item>
      <div class="osc-summary">
        <div class="osc-title">策略逻辑速览</div>
        <ul>
          <li><b>对比区间</b>：从「K线天数那根」或「上次实际交易那根」中<b>更靠近今天</b>的一根开始，到今天为止。</li>
          <li><b>上涨减仓</b>：现价较区间<b>最低点</b>涨幅超过阈值 → 卖出「卖出份额」份（不会低于底仓）。</li>
          <li><b>下跌加仓</b>：现价较区间<b>最高点</b>跌幅超过阈值 → 买入「买入份额」份（不会超过满仓）。</li>
          <li><b>同时满足</b>时<b>买入优先</b>；夹取后可交易份额为 0（已满仓/已到底仓）不出信号。</li>
          <li><b>2 个交易日冷却</b>：距上次<b>实际交易</b>不足 2 个交易日时只提示不交易；信号没照做（无交易流水）不算数，次日照样重新提示。</li>
        </ul>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { h, reactive, watch } from 'vue'
import { ElIcon, ElTooltip } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'

/**
 * 策略参数表单（三种策略各一套字段）。
 * 每个参数标签后带一个「?」，悬浮解释该参数的含义与取值影响（新用户不看文档也能填对）。
 */

/**
 * 参数标签 + 问号说明（局部函数组件，避免每个 el-form-item 重复写 tooltip 结构）。
 *
 * @param props.text 参数名
 * @param props.help 悬浮说明文案
 */
const ParamLabel = (props: { text: string; help: string }) =>
  h('span', { class: 'param-label' }, [
    props.text,
    h(
      ElTooltip,
      {
        placement: 'top',
        effect: 'dark',
        showAfter: 120,
        // 浮层限宽 + 内部正常换行：说明文案较长，不限宽会被挤成一行、又长又难读
        popperClass: 'param-help-popper'
      },
      {
        default: () => h(ElIcon, { class: 'param-help' }, () => h(QuestionFilled)),
        content: () => h('div', { class: 'param-help-text' }, props.help)
      }
    )
  ])

const props = defineProps<{ type: string; modelValue: Record<string, unknown> }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const defaults: Record<string, Record<string, unknown>> = {
  GRID: { mode: 'arithmetic', upper: 5.5, lower: 3.0, grids: 10, sharePerGrid: 2000, basePosition: 4000 },
  VAL_PERCENTILE: { lowPct: 25, highPct: 75, steps: 5, windowYears: 8, sharePerStep: 3000, basePosition: 5000 },
  // 震荡向上（V5.13 重构）：固定份额 + 区间极值触发
  OSC_UP: {
    initialShare: 10000,
    baseShare: 5000,
    fullShare: 20000,
    windowDays: 60,
    riseReducePct: 5,
    fallAddPct: 5,
    buyShare: 5000,
    sellShare: 5000
  }
}

const form = reactive<Record<string, unknown>>({ ...(defaults[props.type] ?? {}), ...props.modelValue })

watch(
  () => props.type,
  (type) => {
    Object.keys(form).forEach((key) => delete form[key])
    Object.assign(form, defaults[type] ?? {})
    emit('update:modelValue', { ...form })
  }
)

watch(
  form,
  () => {
    emit('update:modelValue', { ...form })
  },
  { deep: true }
)
</script>

<style scoped>
/* 参数网格：列数按容器宽度自适应（回测区 1615px → 4 列；弹窗 728px → 2 列；窄屏 → 1 列），
   列宽下限 340px 保证「标签 + 数字输入框」都舒展，避免退化成每列一个 150px 的窄控件 */
.param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  column-gap: var(--q-space-4);
  align-items: start;
}

/* 列间距交给 grid 的 gap，避免 form-item 自带的右边距再叠一层 */
.param-grid :deep(.el-form-item) {
  margin-right: 0;
}

/* 数字/单选等控件铺满所在格子，不再固定 150px 留白 */
.param-grid :deep(.el-input-number) {
  width: 100%;
}

/* 档位说明/逻辑速览占满整行（不参与网格列排布） */
.param-grid :deep(.param-note) {
  grid-column: 1 / -1;
}

.osc-summary {
  grid-column: 1 / -1;
  margin-top: var(--q-space-2);
  padding: var(--q-space-3);
  border-radius: var(--q-radius-sm);
  background: var(--q-bg-page);
  border: 1px solid var(--q-border-light);
  font-size: var(--q-font-xs);
  line-height: 1.8;
  color: var(--q-text-regular);
}

.osc-summary .osc-title {
  font-weight: 600;
  color: var(--q-text-primary);
  margin-bottom: var(--q-space-1);
}

.osc-summary ul {
  margin: 0;
  padding-left: 18px;
}

.param-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.param-help {
  color: var(--q-text-muted);
  cursor: help;
  font-size: 14px;
}

.param-help:hover {
  color: var(--q-color-primary);
}

.param-note {
  margin-top: var(--q-space-2);
}

.param-note :deep(.el-alert__title) {
  font-size: var(--q-font-xs);
  line-height: 1.6;
}
</style>

<!-- 提示浮层被 teleport 到 body，scoped 命中不到，故单独一段（类名带前缀，避免污染全局） -->
<style>
.param-help-popper {
  max-width: 340px;
}

.param-help-text {
  max-width: 316px;
  white-space: normal;
  word-break: break-word;
  line-height: 1.7;
  text-align: left;
}
</style>
