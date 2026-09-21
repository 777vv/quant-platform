<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item v-if="!isCash" label="基金" prop="fundCode">
        <el-select v-model="form.fundCode" filterable placeholder="选择自选池中的基金" style="width: 100%">
          <el-option
            v-for="item in funds"
            :key="item.fundCode"
            :value="item.fundCode"
            :label="`${item.fundCode} ${item.fundName}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="交易类型" prop="tradeType">
        <el-radio-group v-model="form.tradeType" @change="onTypeChange">
          <el-radio-button :value="1">买入</el-radio-button>
          <el-radio-button :value="2">卖出</el-radio-button>
          <el-radio-button :value="3">分红</el-radio-button>
          <el-radio-button :value="4">转入</el-radio-button>
          <el-radio-button :value="5">转出</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="交易日期" prop="tradeDate">
        <el-date-picker
          v-model="form.tradeDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 100%"
        />
      </el-form-item>
      <template v-if="isFundTrade">
        <el-form-item label="成交金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
          <div class="field-hint">按对账单填成交金额与成交数量即可，成交价由两者自动算出（金额 ÷ 数量，最准）</div>
        </el-form-item>
        <el-form-item label="成交数量" prop="share">
          <el-input-number
            v-model="form.share"
            :min="0.01"
            :precision="2"
            :controls="false"
            placeholder="份额"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="成交价">
          <el-input :model-value="priceText" readonly class="auto-field" placeholder="由 金额 ÷ 数量 自动计算" />
          <div class="field-hint">只读字段：成交价 = 成交金额 ÷ 成交数量，保留 4 位小数，不需要手工填写</div>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item :label="isCash ? '划转金额' : '分红金额'" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
          <div v-if="!isCash" class="field-hint">
            默认按现金分红（税前）录入；红利再投资请拆成「分红 + 买入」两笔，便于分别追溯
          </div>
          <div v-else-if="form.tradeType === 4" class="field-hint">从银行转入证券账户的资金</div>
          <div v-else class="field-hint">
            从证券账户转回银行的资金；当前可转出上限 {{ transferableLimit.toFixed(2) }} 元（现金余额）
          </div>
        </el-form-item>
      </template>
      <el-form-item v-if="!isCash" label="手续费" prop="fee">
        <el-input-number v-model="form.fee" :min="0" :precision="2" :controls="false" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注" prop="note">
        <el-input v-model="form.note" maxlength="200" placeholder="选填" />
      </el-form-item>

      <!-- 交易后实时预估：选完基金并填写金额/数量后即时计算（基于最新价，保存前可见影响） -->
      <el-alert v-if="isFundTrade && preview" type="info" :closable="false" class="trade-preview">
        <template #title>交易后预估（按最新价 {{ preview.lastPrice }} 计算）</template>
        <div class="preview-row"><span>持有份额</span><span class="num">{{ preview.shareBefore }} → {{ preview.shareAfter }}</span></div>
        <div class="preview-row"><span>摊薄成本价</span><span class="num">{{ preview.costBefore }} → {{ preview.costAfter }}</span></div>
        <div class="preview-row">
          <span>{{ form.tradeType === 1 ? '预估浮动盈亏' : '预估本笔已实现盈亏' }}</span>
          <span class="num" :class="preview.pnlClass">{{ preview.pnlText }}</span>
        </div>
      </el-alert>
      <el-alert v-else-if="form.tradeType === 3 && form.amount" type="info" :closable="false" class="trade-preview">
        <template #title>分红预估</template>
        <div class="preview-row"><span>现金分红计入</span><span class="num">+{{ form.amount }} 元（计入已实现收益并冲减成本）</span></div>
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { addTrade, holdings, tradeCashBalance, watchlist, type HoldingVO, type WatchItemVO } from '@/api/fund'

/**
 * 交易流水快捷录入（FR2"录流水"）：不依赖详情页，任意列表一键记账。
 *
 * 录入规则（经确认）：
 *  - 买入/卖出：**只填成交金额与成交数量**，成交价由 金额 ÷ 数量 派生（只读，4 位小数）——
 *    对账单上的金额是最准的，避免手填价×量与实际扣款对不上；
 *  - 分红：只填金额，按**现金分红（税前）**处理（价/份额提交 0，后端分红分支只消费金额）；
 *  - 转入/转出：账户级资金划转，只填金额，不关联基金。
 */
const props = withDefaults(
  defineProps<{
    /** 对话框可见性（v-model） */
    modelValue: boolean
    /** 预选基金代码（从持仓/列表行内打开时带出） */
    presetFund?: string
    /** 预设交易类型（1 买 / 2 卖 / 3 分红 / 4 转入 / 5 转出） */
    presetType?: number
  }>(),
  { presetFund: '', presetType: 1 }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 保存成功（父组件刷新持仓/列表） */
  (e: 'saved'): void
}>()

const title = computed(() =>
  props.presetFund ? '录入交易流水' : '记一笔（交易流水）'
)

const formRef = ref<FormInstance>()
const funds = ref<WatchItemVO[]>([])
const saving = ref(false)

/** 本地日期（不能用 toISOString：UTC 凌晨会得到昨天） */
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 表单模型（成交价不在此列：它是派生值，不可手填） */
const form = reactive({
  fundCode: '',
  tradeType: 1,
  tradeDate: today(),
  share: undefined as number | undefined,
  amount: undefined as number | undefined,
  fee: 0,
  note: ''
})

/** 账户级资金划转（转入/转出，不关联基金） */
const isCash = computed(() => form.tradeType === 4 || form.tradeType === 5)

/** 基金交易（买入/卖出：需要成交金额与数量） */
const isFundTrade = computed(() => form.tradeType === 1 || form.tradeType === 2)

/**
 * 派生成交价 = 成交金额 ÷ 成交数量，保留 4 位小数。
 * 金额或数量缺失/为 0 时返回 null（界面上显示为空，保存时由必填校验拦下）。
 */
const derivedPrice = computed<number | null>(() => {
  const amount = Number(form.amount ?? 0)
  const share = Number(form.share ?? 0)
  if (!amount || !share || share <= 0) {
    return null
  }
  return Number((amount / share).toFixed(4))
})

/** 成交价展示文本（只读输入框） */
const priceText = computed(() => (derivedPrice.value == null ? '' : derivedPrice.value.toFixed(4)))

/** 划转无价格/份额，仅金额生效；基金交易要求基金 + 金额 + 数量 */
const rules = computed<FormRules>(() => ({
  fundCode: isCash.value ? [] : [{ required: true, message: '请选择基金', trigger: 'change' }],
  tradeDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  share: isFundTrade.value ? [{ required: true, message: '请输入成交数量', trigger: 'blur' }] : [],
  amount: [{
    required: true,
    message: isCash.value ? '请输入划转金额' : form.tradeType === 3 ? '请输入分红金额' : '请输入成交金额',
    trigger: 'blur'
  }]
}))

/** 全部持仓（弹窗打开时一次拉取，预估用） */
const holdingList = ref<HoldingVO[]>([])

/** 可转出上限（元）= max(现金余额, 0)；转入/出与基金交易不受此限制 */
const transferableLimit = ref(0)

/** 交易后预估值（null=无法预估：未选基金/取不到最新价/金额或数量未填） */
const preview = computed(() => {
  if (!isFundTrade.value || !form.fundCode || !derivedPrice.value || !form.share) {
    return null
  }
  const holding = holdingList.value.find((h) => h.fundCode === form.fundCode)
  // 从未交易过的基金没有持仓行：回退用自选清单的最新价，份额/成本按 0 起算，
  // 这样"首笔买入"也能看到交易后的成本与预估盈亏
  const lastPrice = holding?.lastPrice ?? funds.value.find((f) => f.fundCode === form.fundCode)?.lastPrice
  if (!lastPrice) {
    return null
  }
  const shareBefore = Number(holding?.totalShare ?? 0)
  const costBefore = Number(holding?.avgCostPrice ?? 0)
  const fee = form.fee ?? 0
  const amount = Number(form.amount ?? 0)
  const share = Number(form.share)
  const fmt = (v: number) => v.toFixed(4)
  if (form.tradeType === 1) {
    // 买入：新成本 = (旧成本×旧份额 + 金额 + 费) / 新份额
    const shareAfter = shareBefore + share
    const costAfter = shareAfter > 0 ? (costBefore * shareBefore + amount + fee) / shareAfter : 0
    const pnl = shareAfter * lastPrice - costAfter * shareAfter
    return {
      lastPrice, shareBefore: fmt(shareBefore), shareAfter: fmt(shareAfter),
      costBefore: fmt(costBefore), costAfter: fmt(costAfter),
      pnlText: (pnl >= 0 ? '+' : '') + pnl.toFixed(2), pnlClass: pnl >= 0 ? 'text-up' : 'text-down'
    }
  }
  // 卖出：按摊薄成本结转，已实现 = 卖出金额 - 费 - 卖出份额×成本价
  const shareAfter = Math.max(0, shareBefore - share)
  const realized = amount - fee - share * costBefore
  const costAfter = shareAfter > 0 ? costBefore : 0
  return {
    lastPrice, shareBefore: fmt(shareBefore), shareAfter: fmt(shareAfter),
    costBefore: fmt(costBefore), costAfter: fmt(costAfter),
    pnlText: (realized >= 0 ? '+' : '') + realized.toFixed(2), pnlClass: realized >= 0 ? 'text-up' : 'text-down'
  }
})

/**
 * 每次打开：重置表单、带出预选基金，并刷新可选项与持仓。
 * 不再懒加载缓存：保存流水后持仓份额/成本会变、导入基金后自选池会变，
 * 缓存会导致预估里的"持有份额"和基金下拉停留在旧数据；接口失败时沿用上一次的结果。
 */
async function onOpen() {
  form.fundCode = props.presetFund
  form.tradeType = props.presetType
  form.tradeDate = today()
  form.share = undefined
  form.amount = undefined
  form.fee = 0
  form.note = ''
  formRef.value?.clearValidate()
  funds.value = await watchlist().catch(() => funds.value)
  holdingList.value = await holdings().catch(() => holdingList.value)
  // 现金口径：转出额度提示与提交前拦截（后端同样有硬校验）
  const cash = await tradeCashBalance().catch(() => null)
  transferableLimit.value = cash?.transferableLimit ?? 0
}

/** 切换类型时清掉不再参与的字段（分红/划转清数量与手续费，划转连基金一起清） */
function onTypeChange() {
  if (!isFundTrade.value) {
    form.share = undefined
    form.fee = 0
  }
  if (isCash.value) {
    form.fundCode = ''
  }
  formRef.value?.clearValidate()
}

async function submit() {
  if (!isCash.value && funds.value.length === 0) {
    ElMessage.warning('自选池为空，请先在"数据导入"页导入基金')
    return
  }
  // 校验失败时 validate 会 reject，此处返回让表单红字提示（不再静默返回——
  // 旧判断在转入/转出时误命中，曾导致点击保存无任何响应）
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  // 转出不得超过现金余额：前端先拦一次给出可读提示，后端仍会硬校验（防绕过）
  if (form.tradeType === 5 && Number(form.amount) > transferableLimit.value) {
    ElMessage.warning(`转出金额不可超过现金余额，当前可转出 ${transferableLimit.value.toFixed(2)} 元`)
    return
  }
  saving.value = true
  try {
    await addTrade({
      fundCode: isCash.value ? '' : form.fundCode,
      tradeType: form.tradeType,
      tradeDate: form.tradeDate,
      // 成交价由后端按买入/卖出分支要求 > 0，这里提交派生值；分红与划转提交 0
      price: isFundTrade.value ? (derivedPrice.value ?? 0) : 0,
      share: isFundTrade.value ? form.share! : 0,
      amount: form.amount!,
      fee: isCash.value ? 0 : (form.fee ?? 0),
      note: form.note
    })
    ElMessage.success(isCash.value ? '划转已记录，总资产已更新' : '流水已保存，持仓已重算')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.trade-preview {
  margin-top: var(--q-space-2);
}

.preview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--q-font-sm);
  line-height: 1.9;
}

.field-hint {
  width: 100%;
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
  line-height: 1.5;
}

/* 自动派生字段（如成交价）：浅底 + 只读，提示"不用手填" */
.auto-field :deep(.el-input__wrapper) {
  background-color: var(--q-bg-subtle);
  box-shadow: none;
  border: 1px dashed var(--q-border);
}
</style>
