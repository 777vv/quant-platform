<template>
  <div v-if="detail">
    <el-page-header content="返回基金池" class="page-header" @back="$router.push('/funds')" />
    <el-card class="block">
      <template #header>
        <div class="detail-header">
          <span>
            {{ detail.fundName }}（{{ detail.fundCode }}）
            <el-tag :type="detail.fundType === 1 ? 'primary' : 'success'" size="small" class="name-tag">
              {{ detail.fundTypeDesc }}
            </el-tag>
            <!-- 标签：预定义库中已贴到本基金的标签 -->
            <el-tag v-for="tag in tags" :key="tag.id" size="small" type="info" class="name-tag">
              {{ tag.name }}
            </el-tag>
            <el-button v-if="userStore.can(PERM.ACTION_TAG)" link type="primary" size="small" @click="tagEditVisible = true">
              {{ tags.length ? '编辑标签' : '添加标签' }}
            </el-button>
            <span v-if="detail.lastPrice != null" class="price">
              {{ detail.fundType === 1 ? detail.lastPrice.toFixed(4) : detail.lastPrice.toFixed(4) }}
              <span :class="detail.changePct && detail.changePct > 0 ? 'text-up' : 'text-down'">
                {{ detail.changePct == null ? '' : `${detail.changePct > 0 ? '+' : ''}${detail.changePct}%` }}
              </span>
            </span>
          </span>
        </div>
      </template>
      <FundTagEditDialog v-model="tagEditVisible" :fund-code="code" @saved="loadTags" />
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="跟踪指数">
          {{ detail.indexName || '未识别' }}
          <span v-if="detail.indexCode" class="muted">{{ detail.indexCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="成立日期">{{ detail.inceptionDate || '--' }}</el-descriptions-item>
        <el-descriptions-item label="基金公司">{{ detail.fundCompany || '--' }}</el-descriptions-item>
        <el-descriptions-item label="规模(亿)">
          <!-- 优先"每日估算规模"（V6.15：场内 ETF 份额×当日净值），无估算时回退季报披露值 -->
          <el-tooltip v-if="detailScaleText" :content="detailScaleTip" placement="top">
            <span class="num">{{ detailScaleText }}</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="运作费率">
          <el-tooltip v-if="detail.opFeeRate != null" :content="detailFeeBreakdown" placement="top">
            <span class="num">{{ detail.opFeeRate.toFixed(2) }}%</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="溢价率">
          <el-tooltip
            v-if="detail.premiumRate != null"
            :content="`按净值日 ${detail.premiumDate} 的收盘价与单位净值计算`"
            placement="top"
          >
            <span class="num">{{ detail.premiumRate > 0 ? '+' : '' }}{{ detail.premiumRate.toFixed(2) }}%</span>
          </el-tooltip>
          <span v-else class="num">--</span>
        </el-descriptions-item>
        <el-descriptions-item label="最后同步">{{ detail.lastSyncDate || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-tabs v-model="activeTab" class="block">
      <el-tab-pane label="行情走势" name="chart">
        <QuoteTrendPanel
          v-if="detail"
          ref="quotePanelRef"
          :fund-code="code"
          :fund-type="detail.fundType"
          :fund-name="detail.fundName"
          :index-code="detail.indexCode"
          :index-name="detail.indexName"
        />
      </el-tab-pane>

      <!-- 「指数估值」页签已并入行情走势的「估值副图」（V5.57）：PE 走势与行情同一横轴对照查看，
           当前 PE 与分位摘要移到副图开关旁的工具栏提示；未识别跟踪指数/债券指数不适用 PE 的空态文案保留在提示位 -->

      <!-- 信号查询页签（V5.70）：合并本基金的策略信号与均线信号，类型下拉筛选（后续可扩展更多信号类型） -->
      <el-tab-pane label="信号查询" name="signals">
        <div class="sig-toolbar">
          <span class="muted">本基金产生的全部信号 · 共 {{ signalRowsFiltered.length }} 条</span>
          <el-select v-model="signalTypeFilter" size="small" style="width: 140px">
            <el-option value="all" label="全部信号" />
            <el-option value="strategy" label="策略信号" />
            <el-option value="ma" label="均线信号" />
          </el-select>
        </div>
        <el-table v-loading="signalsTabLoading" :data="signalRowsPaged" size="small">
          <el-table-column prop="date" label="日期" min-width="100" />
          <el-table-column label="信号类型" min-width="220">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.kind === 'strategy' ? 'primary' : 'warning'">
                {{ row.kind === 'strategy' ? '策略' : '均线' }}
              </el-tag>
              <span class="sig-type-name">{{ row.typeName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="方向" min-width="80">
            <template #default="{ row }">
              <span :class="row.dirClass">{{ row.direction }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="说明" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">{{ row.detail || '—' }}</template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination
            v-model:current-page="signalPageNo"
            v-model:page-size="signalPageSize"
            :total="signalRowsFiltered.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="signalPageNo = 1"
          />
        </div>
      </el-tab-pane>
      <el-tab-pane label="交易流水" name="trades">
        <div class="toolbar">
          <!-- 新增走共享弹窗（与基金池「记一笔」同一套录入规则）；行内「编辑」仍用本页弹窗做更正 -->
          <el-button v-if="userStore.can(PERM.ACTION_TRADE)" type="primary" size="small" @click="addVisible = true">新增流水</el-button>
        </div>
        <el-table v-loading="tradesLoading" :data="tradeRecords" border size="small">
          <el-table-column prop="tradeDate" label="日期" min-width="120" />
          <el-table-column label="类型" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.tradeType === 1 ? 'danger' : row.tradeType === 2 ? 'success' : 'info'" size="small">
                {{ tradeTypeText(row.tradeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="价格" min-width="120" align="right">
            <template #default="{ row }">{{ row.price.toFixed(4) }}</template>
          </el-table-column>
          <el-table-column label="份额" min-width="140" align="right">
            <template #default="{ row }">{{ row.share.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="140" align="right">
            <template #default="{ row }">{{ row.amount.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="手续费" min-width="110" align="right">
            <template #default="{ row }">{{ row.fee.toFixed(2) }}</template>
          </el-table-column>
          <!-- 备注给出固定宽度：它是本表唯一的弹性列时会吃掉全部剩余宽度（实测 298px），
               固定后剩余宽度由各列按比例分摊，表格依然铺满容器 -->
          <el-table-column prop="note" label="备注" min-width="160" show-overflow-tooltip />
          <!-- 操作列作为唯一的弹性列吸收剩余宽度、按钮靠右结尾：
               若让"备注"当弹性列，它会吃掉全部剩余宽度（实测 298px，用户反馈占太宽） -->
          <el-table-column label="操作" min-width="150" align="right">
            <template #default="{ row }">
              <el-button v-if="userStore.can(PERM.ACTION_TRADE)" size="small" @click="openDialog(row)">编辑</el-button>
              <el-button v-if="userStore.can(PERM.ACTION_TRADE)" size="small" type="danger" plain @click="handleDeleteTrade(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination v-model:current-page="tradePage" :page-size="TRADE_SIZE" :total="tradeTotal" layout="total, prev, pager, next" background @current-change="loadTrades" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="策略配置" name="strategies">
        <div class="toolbar">
          <el-button v-if="userStore.can(PERM.ACTION_STRATEGY)" type="primary" size="small" @click="openStrategyDialog">新增策略</el-button>
        </div>
        <el-table :data="strategies" border size="small">
          <el-table-column label="策略" min-width="150">
            <template #default="{ row }">
              <span class="strategy-name">{{ row.strategyName || strategyNameOf(row.strategyType) }}</span>
              <el-tag size="small" type="info" effect="plain" class="strategy-code">{{ row.strategyType }}</el-tag>
            </template>
          </el-table-column>
          <!-- 参数摘要列已移除（V5.35）：点「编辑」可看完整配置；备注列保留 -->
          <el-table-column label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="启用" width="96">
            <template #default="{ row }">
              <el-switch v-if="userStore.can(PERM.ACTION_STRATEGY)" v-model="row.enabled" :active-value="1" :inactive-value="0" active-text="启用"
                         inline-prompt @change="toggleStrategy(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="userStore.can(PERM.ACTION_STRATEGY)" size="small" link type="primary" @click="openEditStrategy(row)">编辑</el-button>
              <el-button v-if="userStore.can(PERM.ACTION_STRATEGY)" size="small" type="danger" plain @click="handleDeleteStrategy(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="回测" name="backtest">
        <el-card shadow="never" class="block">
          <template #header>发起回测</template>
          <!-- 与下方参数网格同一套栅格 + 标签置顶（V5.78）：筛选字段与策略参数列列对齐、控件同列等宽 -->
          <div class="bt-grid">
            <el-form-item label="策略">
              <div class="strategy-cell">
                <el-select v-model="backtestForm.strategyType" @change="handleBacktestTypeChange">
                  <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
                </el-select>
                <el-button v-if="typeHasSummary(backtestForm.strategyType)" link type="primary" size="small" @click="backtestParamFormRef?.openSummary()">策略逻辑速览</el-button>
              </div>
            </el-form-item>
            <el-form-item label="开始日期">
              <el-date-picker v-model="backtestForm.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="结束日期">
              <el-date-picker v-model="backtestForm.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <!-- V5.79：口径说明改问号悬浮（挪出下方小字，行内更紧凑）；初始资金不可手填 -->
            <el-form-item>
              <template #label>
                <span>
                  初始资金
                  <el-tooltip
                    placement="top"
                    effect="dark"
                    :show-after="120"
                    popper-class="param-help-popper"
                    :content="capitalComputing ? '按开始日期价格计算中…' : '由满仓份额 × 开始日期价 × 1.01（手续费与滑点缓冲）自动算出，不可手填；开始日期当日休市取其后首个交易日价'"
                  >
                    <el-icon class="label-help"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="backtestForm.initialCapital" :min="1000" :controls="false" :readonly="true" class="capital-field" />
            </el-form-item>
          </div>
          <!-- :key 强制重挂载：参数表单只在首次挂载时读 modelValue，不换 key 时外部回填不会显示；
               速览 V5.99 起为按钮模式（不再渲染尾部卡片），按钮在上方策略下拉旁 -->
          <StrategyParamForm
            ref="backtestParamFormRef"
            :key="`bt-${backtestForm.strategyType}-${paramFormKey}`"
            v-model="backtestForm.params"
            :type="backtestForm.strategyType"
            summary-mode="button"
          />
          <div v-if="prefillHint" class="prefill-hint">{{ prefillHint }}</div>
          <el-button v-if="userStore.can(PERM.ACTION_STRATEGY)" type="primary" :loading="backtestRunning" @click="handleBacktest">开始回测</el-button>
        </el-card>
        <!-- 列宽口径：数字列 min-width 均分富余宽度（V5.99 起失败原因列移除，失败详情看批量回测明细/结果页） -->
        <el-table :data="backtestRecords" border size="small">
          <el-table-column prop="id" label="#" width="56" />
          <el-table-column label="策略" min-width="110">
            <template #default="{ row }">
              {{ strategyNameOf(row.strategyType) }}
              <el-tag v-if="row.batchId" size="small" type="info" effect="plain" class="batch-tag" :title="`来自批量回测批次 #${row.batchId}`">批#{{ row.batchId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="区间" min-width="178">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                {{ row.status === 1 ? '成功' : row.status === 2 ? '失败' : '运行中' }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 分组表头：策略表现 / 持有基准对照 / 仓位与资金效率 三组分开，一眼看清对比关系 -->
          <el-table-column label="策略表现" align="center">
            <el-table-column label="总收益%" min-width="92" align="right">
              <template #default="{ row }">{{ row.totalReturnPct ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="最大回撤%" min-width="98" align="right">
              <template #default="{ row }">{{ row.maxDrawdownPct ?? '--' }}</template>
            </el-table-column>
            <el-table-column prop="tradeCount" label="交易数" min-width="76" align="right" />
          </el-table-column>
          <el-table-column label="持有基准对照" align="center">
            <el-table-column label="持有总收益%" min-width="104" align="right">
              <template #default="{ row }">
                <span :class="benchClassOf(row)">{{ row.benchTotalReturnPct ?? '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="持有最大回撤%" min-width="112" align="right">
              <template #default="{ row }">{{ row.benchMaxDrawdownPct ?? '--' }}</template>
            </el-table-column>
            <!-- 持有年化%（V5.96，与批量回测明细同列）：买入持有基准的收益折年化，作策略年化的对照 -->
            <el-table-column label="持有年化%" min-width="92" align="right">
              <template #default="{ row }">
                <span :class="benchClassOf(row)">{{ annualizedFromPct(row.benchTotalReturnPct, row.startDate, row.endDate) ?? '--' }}</span>
              </template>
            </el-table-column>
          </el-table-column>
          <el-table-column label="仓位与资金效率" align="center">
            <el-table-column label="平均仓位份额" min-width="108" align="right">
              <template #default="{ row }">{{ row.avgPositionShare ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="持仓资产收益率%" min-width="116" align="right">
              <template #header>
                持仓资产收益率%
                <el-tooltip content="策略收益 ÷ 平均持仓成本（实际投进去的钱）。平均仓位没打满时它高于总收益率；avg 成本口径，不随行情虚增" placement="top">
                  <el-icon class="th-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <span :class="positionClassOf(row)">{{ row.positionReturnPct ?? '--' }}</span>
              </template>
            </el-table-column>
            <!-- 持仓年化%（V5.95，用户口径）：(1+持仓资产收益率)^(365/区间自然日)−1，
                 把"实际投出资金的收益"折算成年度可比口径；老记录缺 avgPositionCost 时无值显示 -- -->
            <el-table-column label="持仓年化%" min-width="96" align="right">
              <template #header>
                持仓年化%
                <el-tooltip content="(1+持仓资产收益率)^(365/区间天数)−1：按区间自然日把持仓资产收益率复利折算成年化，便于不同区间横向比较；持仓资产收益率为空（老记录）时无值" placement="top">
                  <el-icon class="th-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <template #default="{ row }">
                <span :class="positionClassOf(row)">{{ positionAnnualPctOf(row) ?? '--' }}</span>
              </template>
            </el-table-column>
          </el-table-column>
          <el-table-column label="策略详情" min-width="76">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openStrategyDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <!-- 操作列宽度按按钮数给足（V5.93 实测）：三个 small 按钮 50×3 + 间距 12×2 = 174px，
               单元格还要吃掉内边距 16px + 边框 1px，所以 190px 仍差 1px → 「删除」被挤到第二行。
               取 204px（内容 187px，余量 13px），不要再往临界值上写 -->
          <el-table-column label="操作" width="204" fixed="right">
            <template #default="{ row }">
              <!-- 应用：把本次回测所用参数一键落地为该基金的策略配置（V5.65）；仅成功记录显示 -->
              <el-button
                v-if="userStore.can(PERM.ACTION_STRATEGY) && row.status === 1"
                size="small"
                type="primary"
                plain
                @click="applyBacktest(row)"
              >应用</el-button>
              <el-button size="small" @click="$router.push(`/backtest/${row.id}`)">结果</el-button>
              <!-- 删除（V5.92）：清掉杂乱的旧回测；运行中（status=0）不可删 -->
              <el-button
                v-if="userStore.can(PERM.ACTION_STRATEGY) && row.status !== 0"
                size="small"
                type="danger"
                plain
                @click="handleDeleteBacktest(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination v-model:current-page="btPage" :page-size="BT_SIZE" :total="btTotal" layout="total, prev, pager, next" background @current-change="loadBacktests" />
        </div>

        <!-- 策略配置详情弹框：把 params JSON 解析成带中文标签的键值表 -->
        <el-dialog v-model="strategyDetailVisible" title="策略配置详情" width="520px">
          <el-descriptions v-if="strategyDetailRow" :column="1" border size="small">
            <el-descriptions-item label="策略">{{ strategyNameOf(strategyDetailRow.strategyType) }}</el-descriptions-item>
            <el-descriptions-item label="基金">{{ strategyDetailRow.fundCode }}</el-descriptions-item>
            <el-descriptions-item v-for="item in strategyDetailItems" :key="item.label" :label="item.label">
              {{ item.value }}
            </el-descriptions-item>
          </el-descriptions>
          <template #footer>
            <el-button @click="strategyDetailVisible = false">关闭</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="strategyDialogVisible" :title="editingStrategyId ? '编辑策略配置' : '新增策略配置'" width="1000px" class="strategy-dialog">
      <!-- 标签置顶（V5.78）：与内部参数网格同一套「标签在上、控件铺满」的排布，整弹窗节奏一致 -->
      <el-form label-position="top" class="strategy-form">
        <el-form-item label="策略类型">
          <!-- 编辑模式下禁切类型：类型是 (基金,类型) 唯一键的一半，换类型等于换策略，应删除重建 -->
          <div class="strategy-cell">
            <el-select v-model="newStrategyType" style="flex: 1" :disabled="!!editingStrategyId" @change="(t: string) => applyBacktestParams(t, 'dialog')">
              <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
            </el-select>
            <el-button v-if="typeHasSummary(newStrategyType)" link type="primary" size="small" @click="strategyParamFormRef?.openSummary()">策略逻辑速览</el-button>
          </div>
        </el-form-item>
        <StrategyParamForm
          ref="strategyParamFormRef"
          :key="`new-${newStrategyType}-${paramFormKey}`"
          v-model="newStrategyParams"
          :type="newStrategyType"
          summary-mode="button"
        />
        <el-form-item label="备注">
          <el-input
            v-model="newStrategyRemark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填：记下建仓思路 / 调参缘由（如：窄幅震荡区间，先小仓位试）"
          />
        </el-form-item>
        <div v-if="prefillHint" class="prefill-hint">{{ prefillHint }}</div>
      </el-form>
      <template #footer>
        <el-button @click="strategyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStrategy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="编辑流水" width="480px">
      <el-form :model="tradeForm" label-width="90px">
        <el-form-item label="交易日期">
          <el-date-picker v-model="tradeForm.tradeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="tradeForm.tradeType">
            <el-radio-button :value="1">买入</el-radio-button>
            <el-radio-button :value="2">卖出</el-radio-button>
            <el-radio-button :value="3">分红</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="价格/净值">
          <el-input-number v-model="tradeForm.price" :precision="4" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="份额">
          <el-input-number v-model="tradeForm.share" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="tradeForm.amount" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="手续费">
          <el-input-number v-model="tradeForm.fee" :precision="2" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="tradeForm.note" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTrade">保存</el-button>
      </template>
    </el-dialog>
    <TradeEntryDialog v-model="addVisible" :preset-fund="code" :preset-type="1" @saved="onTradeAdded" />
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { PERM } from '@/utils/permissions'
import { describeStrategyParams } from '@/utils/strategyParams'
import { annualizedFromPct } from '@/utils/metrics'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import FundTagEditDialog from '@/components/fund/FundTagEditDialog.vue'
import QuoteTrendPanel from '@/components/fund/QuoteTrendPanel.vue'
import StrategyParamForm from '@/components/strategy/StrategyParamForm.vue'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'
import {
  addStrategy,
  backtestDetail,
  createBacktest,
  deleteBacktest,
  deleteStrategy,
  fundStrategies,
  pageBacktest,
  signalsPage,
  strategyTypes,
  updateStrategy
} from '@/api/strategy'
import type { BacktestRecord, StrategyConfig, StrategyTypeVO } from '@/api/strategy'
import { maSignalsByFund, type MaSignalItem } from '@/api/marketSignals'
import {
  deleteTrade,
  fundDetail,
  fundKline,
  fundNav,
  fundTags,
  pageTrades,
  updateTrade
} from '@/api/fund'
import type { FundDetailVO, FundTagVO, TradeFlow, TradeFlowRequest } from '@/api/fund'

const userStore = useUserStore()

const route = useRoute()
const code = route.params.code as string

const detail = ref<FundDetailVO | null>(null)
const activeTab = ref('chart')
/** 行情走势面板引用：切回页签时重新取数（保持"回到页签重新取数"的既有体验） */
const quotePanelRef = ref<{ reload: () => void } | null>(null)

/** 本基金已贴标签（来自预定义标签库） */
const tags = ref<FundTagVO[]>([])
const tagEditVisible = ref(false)

/** 拉取本基金标签 */
async function loadTags() {
  const result = await fundTags(code)
  tags.value = result.tags
}
const tradeRecords = ref<TradeFlow[]>([])
const tradesLoading = ref(false)

/** 交易流水分页状态（每页 10 条） */
const tradePage = ref(1)
const tradeTotal = ref(0)
const TRADE_SIZE = 10

/** 回测记录分页状态（每页 10 条） */
const btPage = ref(1)
const btTotal = ref(0)
const BT_SIZE = 10
const dialogVisible = ref(false)
/** 共享录入弹窗（新增流水）可见性 */
const addVisible = ref(false)
const editingId = ref<number | null>(null)

const defaultForm = (): TradeFlowRequest => ({
  fundCode: code,
  tradeType: 1,
  tradeDate: new Date().toISOString().slice(0, 10),
  price: 0,
  share: 0,
  amount: 0,
  fee: 0,
  note: ''
})
const tradeForm = ref<TradeFlowRequest>(defaultForm())

/** 运作费率明细（悬浮提示）：管理费 + 托管费 + 销售服务费 */
const detailFeeBreakdown = computed(() => {
  const part = (label: string, value: number | null | undefined) => `${label} ${(value ?? 0).toFixed(2)}%`
  return `${part('管理费', detail.value?.mgmtFeeRate)} + ${part('托管费', detail.value?.custFeeRate)} + `
    + `${part('销售服务费', detail.value?.salesFeeRate)}（年化）`
})

function tradeTypeText(type: number): string {
  return type === 1 ? '买入' : type === 2 ? '卖出' : '分红'
}

async function loadDetail() {
  detail.value = await fundDetail(code)
  await loadTags()
}

/** 日期字符串加减天数（按本地时区构造，避免 UTC 偏移导致差一天） */
function shiftDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number)
  const base = new Date(y, m - 1, d + days)
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}-${String(base.getDate()).padStart(2, '0')}`
}

/** 交易流水（服务端分页，每页 10 条）；删除/新增后若当前页越界自动回退到最后一页 */
async function loadTrades() {
  tradesLoading.value = true
  try {
    const res = await pageTrades({ fundCode: code, page: tradePage.value, size: TRADE_SIZE })
    const maxPage = Math.max(1, Math.ceil(res.total / TRADE_SIZE))
    if (tradePage.value > maxPage) {
      tradePage.value = maxPage
      const retry = await pageTrades({ fundCode: code, page: tradePage.value, size: TRADE_SIZE })
      tradeRecords.value = retry.records
      tradeTotal.value = retry.total
      return
    }
    tradeRecords.value = res.records
    tradeTotal.value = res.total
  } finally {
    tradesLoading.value = false
  }
}

/**
 * 打开"编辑流水"弹窗（本页专用，用于更正历史流水：价/量/金额都可手改）。
 * 新增流水请走共享的 {@link TradeEntryDialog}（金额+数量 → 价格派生）。
 *
 * @param row 待编辑的流水行
 */
function openDialog(row: TradeFlow) {
  editingId.value = row.id
  tradeForm.value = {
    fundCode: row.fundCode,
    tradeType: row.tradeType,
    tradeDate: row.tradeDate,
    price: row.price,
    share: row.share,
    amount: row.amount,
    fee: row.fee,
    note: row.note
  }
  dialogVisible.value = true
}

/** 留空：编辑分支只做更新（新增已改用共享弹窗） */
async function handleSaveTrade() {
  if (!editingId.value) {
    ElMessage.warning('未指定要更正的流水')
    return
  }
  if (!tradeForm.value.tradeDate || tradeForm.value.amount <= 0) {
    ElMessage.warning('请填写完整的日期、成交价、份额与金额')
    return
  }
  await updateTrade(editingId.value, tradeForm.value)
  ElMessage.success('已保存，持仓已重算')
  dialogVisible.value = false
  loadTrades()
}

/** 共享弹窗新增成功后：刷新流水与本页持仓相关展示 */
function onTradeAdded() {
  loadTrades()
  loadDetail()
}

async function handleDeleteTrade(row: TradeFlow) {
  await ElMessageBox.confirm(`确认删除 ${row.tradeDate} 的${tradeTypeText(row.tradeType)}流水？`, '删除确认', {
    type: 'warning'
  })
  await deleteTrade(row.id)
  ElMessage.success('已删除，持仓已重算')
  loadTrades()
}

// 切到【信号查询】页签时懒加载一次（策略信号 + 均线信号合并）
watch(activeTab, (tab) => {
  if (tab === 'signals' && !signalsTabLoaded.value && !signalsTabLoading.value) {
    loadFundSignals()
  }
})

// 切回【行情走势】页签时重新取数（组件挂载时已自行加载一次，这里只在真正发生切换时触发）
watch(activeTab, (tab) => {
  if (tab === 'chart') {
    quotePanelRef.value?.reload()
  }
})

// ===== 策略配置与回测 =====
const strategies = ref<StrategyConfig[]>([])
const strategyTypeList = ref<StrategyTypeVO[]>([])

/** 策略类型 → 展示名（来自注册表；未知类型回退类型码） */
/**
 * 已下线策略的中文名（V5.28 从注册表移除；历史回测/旧配置仍需可读，作为 strategyNameOf 的第二级回退，
 * 否则 510300 这类含历史 GRID/VAL_PERCENTILE 回测的基金会显示成原始类型码）
 */
const RETIRED_STRATEGY_NAMES: Record<string, string> = {
  GRID: '网格交易(已下线)',
  VAL_PERCENTILE: '估值百分位(已下线)'
}

function strategyNameOf(type: string): string {
  return strategyTypeList.value.find((t) => t.type === type)?.name
    ?? RETIRED_STRATEGY_NAMES[type]
    ?? type
}

/** 策略配置详情弹框 */
const strategyDetailVisible = ref(false)
const strategyDetailRow = ref<BacktestRecord | null>(null)
const strategyDetailItems = ref<{ label: string; value: string }[]>([])

/** 规模展示值（V6.15）：优先每日估算，回退季报披露值 */
const detailScaleText = computed<string | null>(() => {
  if (!detail.value) {
    return null
  }
  if (detail.value.dailyScale != null) {
    return detail.value.dailyScale.toFixed(2)
  }
  return detail.value.fundScale != null ? detail.value.fundScale.toFixed(2) : null
})

/** 规模口径提示：估算标数据日与公式，披露值标截止日 */
const detailScaleTip = computed(() => {
  if (!detail.value) {
    return ''
  }
  if (detail.value.dailyScale != null) {
    return `每日估算规模 = 当日份额 × 当日单位净值（数据日 ${detail.value.dailyScaleDate ?? '未知'}）；定期报告披露值 ${detail.value.fundScale?.toFixed(2) ?? '--'} 亿元（截止 ${detail.value.fundScaleDate ?? '未知'}）`
  }
  return `净资产规模（定期报告披露），截止 ${detail.value.fundScaleDate ?? '未知'}；场内 ETF 才有每日估算值`
})

/** 打开策略配置详情弹框：解析 params JSON → 带中文标签的键值列表（公共工具单源，V5.96 起批量回测页共用） */
function openStrategyDetail(row: BacktestRecord) {
  strategyDetailRow.value = row
  strategyDetailItems.value = describeStrategyParams(row.strategyType, row.params)
  strategyDetailVisible.value = true
}

/** 持有收益的涨跌色：正红负绿（红涨绿跌），与策略总收益同规则 */
function benchClassOf(row: BacktestRecord): string {
  if (row.benchTotalReturnPct == null) {
    return ''
  }
  return row.benchTotalReturnPct >= 0 ? 'text-up' : 'text-down'
}

/** 持仓资产收益率的涨跌色：正红负绿（红涨绿跌） */
function positionClassOf(row: BacktestRecord): string {
  if (row.positionReturnPct == null) {
    return ''
  }
  return row.positionReturnPct >= 0 ? 'text-up' : 'text-down'
}

/**
 * 持仓年化%（V5.95 用户口径）：(1+持仓资产收益率%)^(365/区间自然日)−1。
 * V5.96 起走公共助手 metrics.annualizedFromPct（批量回测明细的持仓/持有年化同源，避免两处各写一份）。
 */
function positionAnnualPctOf(row: BacktestRecord): number | null {
  return annualizedFromPct(row.positionReturnPct, row.startDate, row.endDate)
}
/** 参数表单组件引用：速览按钮触发表单内部的速览弹框（V5.99 按钮模式） */
const backtestParamFormRef = ref<{ openSummary: () => void } | null>(null)
const strategyParamFormRef = ref<{ openSummary: () => void } | null>(null)
/** 该策略类型是否有速览内容（有速览卡片的九种类型；其余隐藏按钮） */
function typeHasSummary(type: string): boolean {
  return ['MA_BREAK', 'MA_TP_GRID', 'OSC_UP', 'DIV_GRID', 'NDX_GRID', 'PYRAMID_GRID', 'INV_PYRAMID_GRID',
    'DUAL_MA', 'TRIPLE_MA'].includes(type)
}
const strategyDialogVisible = ref(false)
/** 编辑中的配置 id（null = 新增模式）——V5.34 详情按钮改为编辑后弹框双模式 */
const editingStrategyId = ref<number | null>(null)
/** 策略备注（新增/编辑共用弹框；V5.34 新增） */
const newStrategyRemark = ref('')
// 策略类型不再硬编码（旧版写死 'GRID'，该策略 V5.28 已下线、会显示成空）：进页按
// 「本基金已配置的策略 → 本基金最近一次回测的策略 → 注册表里的第一个策略」依次兜底
const newStrategyType = ref('')
const newStrategyParams = ref<Record<string, unknown>>({})
/** 参数表单的强制重挂载计数：回填参数后 +1，让表单重新挂载并读到新的 modelValue */
const paramFormKey = ref(0)
/** 回填提示（如「已回填…上次回测的参数」），没有回填时为空 */
const prefillHint = ref('')
const backtestForm = reactive({
  strategyType: '',
  params: {} as Record<string, unknown>,
  startDate: '',
  endDate: '',
  initialCapital: 100000
})
const backtestRecords = ref<BacktestRecord[]>([])

// ===== V5.76 初始资金自动计算：满仓份额 × 开始日期价 × 1.01（费用缓冲），输入框只读 =====
const capitalComputing = ref(false)
/** 并发保护令牌：只认最后一次触发的计算，避免慢请求晚到覆盖新值 */
let capitalCalcToken = 0
/** 开始日期价缓存（键 fundCode|startDate）：同一基金同一开始日期不重复拉行情 */
const capitalPriceCache = new Map<string, number>()

/**
 * 按满仓份额与开始日期价重算初始资金。
 * 价格口径与回测引擎一致：ETF 用前复权收盘价、场外用复权净值；开始日期当天休市时
 * 取其后 21 个自然日内首个交易日的价（行情区间多取 21 天余量）。
 * 价格缺失时保持现值不乱算，发起回测前由 handleBacktest 校验拦截。
 */
async function recalcInitialCapital() {
  const full = Number(backtestForm.params?.fullShare ?? 0)
  const start = backtestForm.startDate
  if (!detail.value || !start || !(full > 0)) {
    return
  }
  capitalComputing.value = true
  const myToken = ++capitalCalcToken
  try {
    const key = `${code}|${start}`
    if (!capitalPriceCache.has(key)) {
      const end = shiftDays(start, 21)
      const points = detail.value.fundType === 1
        ? await fundKline(code, 8000, start, end).catch(() => [])
        : await fundNav(code, 8000, start, end).catch(() => [])
      if (myToken !== capitalCalcToken) {
        return
      }
      const first = (points || []).find((p) => p.date >= start)
      if (first) {
        const price = detail.value.fundType === 1 ? Number(first.close) : Number(first.adjNav ?? first.unitNav)
        if (price > 0) {
          capitalPriceCache.set(key, price)
        }
      }
    }
    const price = capitalPriceCache.get(key)
    if (price !== undefined && price > 0) {
      // 向上取整：宁可资金多备一点，也不让回测因差 1 元被「初始本金不足」拦下
      backtestForm.initialCapital = Math.ceil(full * price * 1.01)
    } else {
      // 价格拿不到（开始日期早于数据起点/行情缺失）：清零交给「发起回测」前的校验拦截，不残留旧值
      backtestForm.initialCapital = 0
    }
  } finally {
    if (myToken === capitalCalcToken) {
      capitalComputing.value = false
    }
  }
}

// 开始日期、参数对象（参数表单挂载即回传默认值，之后每次改动都会换新对象）、详情加载完成，任一变化都重算
watch(() => [backtestForm.startDate, backtestForm.params, detail.value?.fundType], () => recalcInitialCapital())

// ===== 信号查询页签（V5.70）：合并策略信号与均线信号 =====
type SignalKind = 'strategy' | 'ma'
interface SignalTabRow {
  date: string
  kind: SignalKind
  typeName: string
  direction: string
  dirClass: string
  detail: string
}
const signalTypeFilter = ref<'all' | SignalKind>('all')
const signalRows = ref<SignalTabRow[]>([])
const signalsTabLoading = ref(false)
const signalsTabLoaded = ref(false)
const signalPageNo = ref(1)
const signalPageSize = ref(10)

const signalRowsFiltered = computed(() => {
  if (signalTypeFilter.value === 'all') {
    return signalRows.value
  }
  return signalRows.value.filter((row) => row.kind === signalTypeFilter.value)
})

const signalRowsPaged = computed(() => {
  const start = (signalPageNo.value - 1) * signalPageSize.value
  return signalRowsFiltered.value.slice(start, start + signalPageSize.value)
})

/** 策略方向文案与着色（红涨绿跌语义：买入红/卖出绿/持有中性） */
function strategyDirectionOf(direction: string): { direction: string; dirClass: string } {
  if (direction === 'BUY') {
    return { direction: '买入', dirClass: 'text-up' }
  }
  if (direction === 'SELL') {
    return { direction: '卖出', dirClass: 'text-down' }
  }
  return { direction: '持有', dirClass: '' }
}

/** 拉取本基金全部信号（策略 signal_record + 均线 ma_signal），合并按日期倒序 */
async function loadFundSignals() {
  signalsTabLoading.value = true
  try {
    const [strategyPage, maSignals] = await Promise.all([
      signalsPage({ fundCode: code, page: 1, size: 500 }).catch(() => ({ records: [], total: 0 })),
      maSignalsByFund(code).catch(() => [] as MaSignalItem[])
    ])
    const merged: SignalTabRow[] = []
    for (const s of strategyPage.records) {
      const dir = strategyDirectionOf(s.direction)
      merged.push({
        date: s.signalDate,
        kind: 'strategy',
        typeName: s.strategyName,
        direction: dir.direction,
        dirClass: dir.dirClass,
        detail: s.suggestDesc
      })
    }
    for (const m of maSignals) {
      merged.push({
        date: String(m.signalDate),
        kind: 'ma',
        typeName: m.signalDesc,
        direction: m.direction === 'UP' ? '上穿' : '下穿',
        dirClass: m.direction === 'UP' ? 'text-up' : 'text-down',
        detail: `信号价 ${m.priceAt ?? '—'}；${m.maShort}日均线 ${m.maShortVal ?? '—'}、${m.maLong}日均线 ${m.maLongVal ?? '—'}`
      })
    }
    merged.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
    signalRows.value = merged
    signalPageNo.value = 1
    signalsTabLoaded.value = true
  } finally {
    signalsTabLoading.value = false
  }
}
const backtestRunning = ref(false)
/** 回测状态轮询句柄：组件卸载时必须清理，否则离开页面后仍每 1.5 秒轮询直到回测结束 */
let backtestTimer: number | undefined
onUnmounted(() => {
  if (backtestTimer) {
    window.clearInterval(backtestTimer)
    backtestTimer = undefined
  }
})

async function loadStrategies() {
  strategies.value = await fundStrategies(code)
  // 已配置的策略参数预填回测表单
  if (strategies.value.length > 0 && !backtestForm.startDate) {
    const first = strategies.value[0]
    backtestForm.strategyType = first.strategyType
    backtestForm.params = JSON.parse(first.params)
  }
}

/**
 * 取本基金「最近一次该策略的回测参数与区间」（回测列表按 id 倒序，取第一条同类型记录）。
 * 没有历史返回 null，由参数表单填默认值。
 */
async function lastBacktestParams(
  type: string
): Promise<{ params: Record<string, unknown>; startDate: string; endDate: string } | null> {
  if (!type) {
    return null
  }
  try {
    const result = await pageBacktest(code, 1, 20)
    const hit = result.records.find((r) => r.strategyType === type)
    if (!hit) {
      return null
    }
    return { params: JSON.parse(hit.params) as Record<string, unknown>, startDate: hit.startDate, endDate: hit.endDate }
  } catch {
    return null
  }
}

/**
 * 按策略回填参数（取不到则清空 → 表单用默认值），强制表单重挂载并给出提示。
 * 发起回测表单（V5.81）连同上次回测的起止日期一起回填——初始资金由「满仓份额 × 开始日期价」自动重算。
 *
 * @param type   策略类型
 * @param target form=发起回测表单，dialog=新增策略弹框
 */
async function applyBacktestParams(type: string, target: 'form' | 'dialog') {
  const hit = await lastBacktestParams(type)
  if (target === 'form') {
    backtestForm.params = hit ? { ...hit.params } : {}
    if (hit) {
      backtestForm.startDate = hit.startDate
      backtestForm.endDate = hit.endDate
    }
  } else {
    newStrategyParams.value = hit ? { ...hit.params } : {}
  }
  paramFormKey.value += 1
  prefillHint.value = hit
    ? target === 'form'
      ? `已回填「${strategyNameOf(type)}」上次回测（${hit.startDate} ~ ${hit.endDate}）的参数与区间，初始资金已按开始日期价自动算出，可直接开始回测或按需修改`
      : `已回填「${strategyNameOf(type)}」上次回测（截至 ${hit.endDate}）的参数，可直接保存或按需修改`
    : ''
}

/** 回测表单切换策略：自动回填该策略上次回测的参数 */
async function handleBacktestTypeChange(type: string) {
  await applyBacktestParams(type, 'form')
}

/** 打开新增策略弹框（新增模式）：回填该策略上次回测的参数（回测调好的参数可直接建配置） */
async function openStrategyDialog() {
  editingStrategyId.value = null
  newStrategyRemark.value = ''
  strategyDialogVisible.value = true
  if (!newStrategyType.value) {
    newStrategyType.value = defaultStrategyType()
  }
  await applyBacktestParams(newStrategyType.value, 'dialog')
}

/** 打开编辑弹框（V5.34：详情按钮改为编辑）：预填类型/参数/备注，类型不可改 */
async function openEditStrategy(row: StrategyConfig) {
  editingStrategyId.value = row.id
  newStrategyType.value = row.strategyType
  try {
    newStrategyParams.value = JSON.parse(row.params) as Record<string, unknown>
  } catch {
    newStrategyParams.value = {}
  }
  newStrategyRemark.value = row.remark ?? ''
  prefillHint.value = ''
  paramFormKey.value += 1
  strategyDialogVisible.value = true
}

/**
 * 默认策略：本基金已配置的 → 最近一次回测的 → 注册表里的第一个。
 * **三个来源都必须落在注册表内**（V5.88 修复）：已下线的策略（如 GRID/VAL_PERCENTILE）虽仍出现在历史
 * 回测记录/旧配置里，但不在下拉选项中——直接取用会让 el-select 显示成原始类型码（510300 最新回测是
 * VAL_PERCENTILE，实测就显示成了"VAL_PERCENTILE"），且回测提交也会被后端拒绝。
 */
function defaultStrategyType(): string {
  const available = new Set(strategyTypeList.value.map((t) => t.type))
  const configured = strategies.value.find((s) => available.has(s.strategyType))
  if (configured) {
    return configured.strategyType
  }
  const lastBacktest = backtestRecords.value.find((r) => available.has(r.strategyType))
  if (lastBacktest) {
    return lastBacktest.strategyType
  }
  return strategyTypeList.value[0]?.type ?? ''
}

/**
 * 进页初始化：先取已配置策略 → 再拉策略类型与回测记录 → 定默认策略（不再硬编码 GRID）→
 * 该策略没有已保存配置时，回填本基金上次该策略的回测参数。
 */
async function initBacktestForm() {
  await loadStrategies()
  strategyTypeList.value = await strategyTypes()
  await loadBacktests()
  if (backtestForm.strategyType) {
    // 已配置策略进入：参数用配置值，回测区间回填该策略最近一次回测的起止日期（初始资金随之自动算出）
    const hit = await lastBacktestParams(backtestForm.strategyType)
    if (hit && !backtestForm.startDate) {
      backtestForm.startDate = hit.startDate
      backtestForm.endDate = hit.endDate
    }
    return
  }
  backtestForm.strategyType = defaultStrategyType()
  const configured = strategies.value.some((s) => s.strategyType === backtestForm.strategyType)
  if (!configured) {
    await applyBacktestParams(backtestForm.strategyType, 'form')
  }
}

async function handleSaveStrategy() {
  const remark = newStrategyRemark.value.trim() || undefined
  if (editingStrategyId.value) {
    // 编辑：类型不可改（弹框里已禁用），随提交体带上当前值（与已存一致，后端据此二次确认）；
    // 参数/备注可改；enabled 不传 = 保持原值
    await updateStrategy(editingStrategyId.value, {
      strategyType: newStrategyType.value,
      params: newStrategyParams.value,
      remark
    })
    ElMessage.success('策略已保存')
  } else {
    await addStrategy(code, { strategyType: newStrategyType.value, params: newStrategyParams.value, remark })
    ElMessage.success('策略已保存')
  }
  strategyDialogVisible.value = false
  loadStrategies()
}

/**
 * 「应用」回测记录（V5.65）：把本次回测所用策略参数一键落地为该基金的策略配置。
 * 同类型策略已存在 → 覆盖其参数（enabled/备注保持原值）；不存在 → 新增（默认启用）。
 * 成功后刷新策略配置列表；用户在确认框取消则静默返回。
 */
async function applyBacktest(row: BacktestRecord) {
  let params: Record<string, unknown>
  try {
    params = JSON.parse(row.params) as Record<string, unknown>
  } catch {
    ElMessage.error('回测参数解析失败，无法应用')
    return
  }
  const typeName = strategyNameOf(row.strategyType)
  const existing = strategies.value.find((s) => s.strategyType === row.strategyType)
  try {
    if (existing) {
      await ElMessageBox.confirm(
        `该基金已配置「${typeName}」策略。应用后将按本次回测参数**覆盖其参数**（启用状态与备注保持不变，其他类型策略不受影响）。继续？`,
        '应用回测策略',
        { type: 'warning', confirmButtonText: '覆盖参数', cancelButtonText: '取消' }
      )
      await updateStrategy(existing.id, { strategyType: row.strategyType, params })
      ElMessage.success(`已按回测参数更新「${typeName}」策略`)
    } else {
      await ElMessageBox.confirm(
        `将按本次回测参数为该基金新增「${typeName}」策略配置（默认启用）。继续？`,
        '应用回测策略',
        { type: 'info', confirmButtonText: '新增策略', cancelButtonText: '取消' }
      )
      await addStrategy(code, { strategyType: row.strategyType, params })
      ElMessage.success(`已新增「${typeName}」策略配置`)
    }
    loadStrategies()
  } catch {
    // 用户取消确认框：静默返回
  }
}

/**
 * 切换策略启用状态：开关用 v-model 绑到 row.enabled（写回的是**新值**），
 * 所以这里直接提交当前值，**不能再取反**——早期版本用 :model-value 单向绑定才需要取反，
 * 改成 v-model 后取反会把状态写反（实测踩过）。
 */
async function toggleStrategy(row: StrategyConfig) {
  await updateStrategy(row.id, { enabled: row.enabled })
  loadStrategies()
}

async function handleDeleteStrategy(row: StrategyConfig) {
  await ElMessageBox.confirm(`确认删除 ${row.strategyName} 配置？`, '删除确认', { type: 'warning' })
  await deleteStrategy(row.id)
  loadStrategies()
}

/** 回测记录（服务端分页，每页 10 条）；删除后若当前页越界自动回退到最后一页 */
async function loadBacktests() {
  const page = await pageBacktest(code, btPage.value, BT_SIZE)
  const maxPage = Math.max(1, Math.ceil(page.total / BT_SIZE))
  if (btPage.value > maxPage) {
    btPage.value = maxPage
    const retry = await pageBacktest(code, btPage.value, BT_SIZE)
    backtestRecords.value = retry.records
    btTotal.value = retry.total
    return
  }
  backtestRecords.value = page.records
  btTotal.value = page.total
}

/** 删除回测记录（V5.92）：确认后调删除接口并刷新列表；删除后记录不再出现在列表中 */
async function handleDeleteBacktest(row: BacktestRecord) {
  await ElMessageBox.confirm(
    `确认删除回测 #${row.id}（${strategyNameOf(row.strategyType)} ${row.startDate} ~ ${row.endDate}）？删除后不可恢复。`,
    '删除回测记录',
    { type: 'warning' }
  )
  const deleted = await deleteBacktest(row.id)
  if (!deleted) {
    ElMessage.warning('回测记录不存在或已被删除')
  } else {
    ElMessage.success('回测记录已删除')
  }
  loadBacktests()
}

async function handleBacktest() {
  if (!backtestForm.startDate || !backtestForm.endDate) {
    ElMessage.warning('请选择回测区间')
    return
  }
  // V5.76：初始资金由前端自动算出，价格缺失时算不出来——拦住而不是把 0/默认值发给后端
  if (!(backtestForm.initialCapital > 0)) {
    ElMessage.warning('初始资金尚未算出（开始日期价格缺失），请调整回测区间后重试')
    return
  }
  backtestRunning.value = true
  try {
    const res = await createBacktest({ fundCode: code, ...backtestForm })
    ElMessage.success('回测任务已提交，稍候刷新查看结果')
    backtestTimer = window.setInterval(async () => {
      const record = await backtestDetail(res.id)
      if (record.status !== 0) {
        window.clearInterval(backtestTimer)
        backtestTimer = undefined
        loadBacktests()
        if (record.status === 1) {
          ElMessage.success('回测完成，点击"结果"查看')
        } else {
          ElMessage.error(`回测失败：${record.errorMsg}`)
        }
      }
    }, 1500)
  } finally {
    backtestRunning.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadTrades()
  // 策略类型/默认策略/参数回填统一在 initBacktestForm 里按顺序完成（避免默认值落空；内含回测记录第 1 页）
  initBacktestForm()
})
</script>

<style scoped>
/* 分页器：右对齐贴表格尾部 */
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--q-space-2);
}

/* 回填提示：一行小字，说明参数来自哪一次回测 */
.prefill-hint {
  margin: var(--q-space-1) 0 var(--q-space-2);
  font-size: var(--q-font-xs);
  color: var(--q-text-secondary);
}
.strategy-name {
  font-weight: 600;
  margin-right: 6px;
}

.strategy-code {
  transform: scale(0.9);
}

/* 表头里的口径说明问号 */
.th-help {
  margin-left: 2px;
  vertical-align: -2px;
  color: var(--q-text-muted);
  cursor: help;
}

.page-header {
  margin-bottom: 8px;
}

.block {
  margin-bottom: 16px;
}

.name-tag {
  margin-left: 8px;
}

.price {
  margin-left: 16px;
  font-size: var(--q-font-lg);
  font-weight: 600;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.range-select {
  margin-left: 12px;
}

.toolbar {
  margin-bottom: 10px;
}

/* ===== 回测筛选字段网格（V5.78/V5.79）：与 StrategyParamForm 的参数网格同一节奏 =====
   同一 minmax 栅格 → 列数与列宽和下方参数区严格一致；标签置顶 → 同列控件等宽对齐；
   列宽下限 200px → 一行 5~6 个配置，不再空旷 */
.bt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  column-gap: var(--q-space-4);
  align-items: start;
  margin-bottom: var(--q-space-2);
}

.bt-grid :deep(.el-form-item) {
  display: block;
  margin-right: 0;
}

.bt-grid :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  justify-content: flex-start;
  margin-bottom: var(--q-space-1);
}

.bt-grid :deep(.el-select),
.bt-grid :deep(.el-date-editor),
.bt-grid :deep(.el-input-number) {
  width: 100%;
}

.bt-grid :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

/* 初始资金：自动算出的只读字段，浅灰底 + 虚线边表达「不可编辑」（与 TradeEntryDialog 的 .auto-field 同款式） */
.capital-field :deep(.el-input__wrapper) {
  background-color: var(--q-bg-subtle);
  box-shadow: none;
  border: 1px dashed var(--q-border);
}

.capital-field :deep(.el-input__wrapper) .el-input__inner {
  cursor: default;
}

/* 初始资金标签的问号说明图标（与参数网格的「?」同款式） */
.label-help {
  color: var(--q-text-muted);
  cursor: help;
  font-size: 14px;
  vertical-align: -3px;
  margin-left: 2px;
}

.label-help:hover {
  color: var(--q-color-primary);
}

/* 策略下拉 + 速览按钮同一行（发起回测与新增/编辑策略弹窗共用） */
.strategy-cell {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  width: 100%;
}
.strategy-cell .el-select {
  flex: 1;
}

/* 批量回测来源标识（批#N 小标签）与基金代码小字的间距 */
.batch-tag {
  margin-left: var(--q-space-1);
}

.fund-code {
  margin-left: var(--q-space-2);
}
</style>
