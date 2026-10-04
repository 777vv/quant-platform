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
          <el-tooltip v-if="detail.fundScale != null" :content="`净资产规模，截止 ${detail.fundScaleDate ?? '未知'}`" placement="top">
            <span class="num">{{ detail.fundScale.toFixed(2) }}</span>
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
        <el-radio-group v-if="detail.fundType === 2" v-model="navMode" size="small" class="block">
          <el-radio-button value="unitNav">单位净值</el-radio-button>
          <el-radio-button value="accNav">累计净值</el-radio-button>
          <el-radio-button value="adjNav">复权净值</el-radio-button>
        </el-radio-group>
        <div class="chart-toolbar">
          <el-select
            v-model="rangeDays"
            size="small"
            style="width: 110px"
            :disabled="!!chartDateRange"
            @change="onPresetRangeChange"
          >
            <el-option :value="90" label="近3个月" />
            <el-option :value="365" label="近1年" />
            <el-option :value="1095" label="近3年" />
            <el-option :value="1825" label="近5年" />
            <el-option :value="3650" label="近10年" />
          </el-select>
          <span class="chart-toolbar-label">或指定日期</span>
          <el-date-picker
            v-model="chartDateRange"
            type="daterange"
            size="small"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
            style="width: 250px"
            @change="loadChart"
          />
          <span class="chart-toolbar-label">主图指标</span>
          <el-radio-group v-model="mainIndicator" size="small" @change="loadChart">
            <el-radio-button value="MA">MA</el-radio-button>
            <el-radio-button value="BOLL">BOLL</el-radio-button>
            <el-radio-button value="NONE">无</el-radio-button>
          </el-radio-group>
          <el-checkbox v-model="showMacd" size="small" @change="loadChart">MACD 副图</el-checkbox>
          <el-checkbox v-model="showYield" size="small" @change="loadChart">股息率副图</el-checkbox>
          <el-checkbox v-model="showScale" size="small" @change="loadChart">规模副图</el-checkbox>
          <el-checkbox v-model="showValuation" size="small" @change="onValuationToggle">估值副图</el-checkbox>
          <span v-if="showScale && scaleData && scaleData.length === 0" class="muted">
            规模历史自 V5.3 上线日起逐日积累（数据源只披露当前规模，无法回补）
          </span>
          <span v-else-if="showScale && scaleData && scaleData.length > 0 && scaleData.length < 10" class="muted">
            规模历史已积累 {{ scaleData.length }} 天（自 {{ scaleData[0].date }} 起，每个交易日 +1），数据较少时副图仅右端可见
          </span>
          <span v-if="showYield && yieldData && !yieldData.priceAvailable" class="muted">
            历史股息率待补齐：需要未复权价（已排入下次同步）
          </span>
          <!-- 估值副图提示：有数据给「指数 + 当前 PE + 分位」摘要（原「指数估值」页签的告警条内容），无数据说明原因 -->
          <span v-if="showValuation && valuationSummary" class="muted">
            {{ valuationSummary }}
            <el-tag :type="valuationTagType" size="small" class="name-tag">{{ valuationTagText }}</el-tag>
          </span>
          <span v-else-if="showValuation && valuationData !== null && !valuationSummary" class="muted">
            {{ valuationEmptyText }}
          </span>
          <span class="muted">
            双击全屏 · 图上拖动可框选区间 · 标记：<b class="mark-b">b</b> 买入
            <b class="mark-s">s</b> 卖出 <b class="mark-q">q</b> 分红除息
          </span>
        </div>
        <div v-if="chartOption" @dblclick="openFullscreen">
          <ChartPanel
            :option="chartOption as EChartsOption"
            :height="chartHeight"
            :brush="true"
            @brush-end="onBrushRange"
            @zoom-change="onZoomChange"
          />
        </div>

        <!-- 区间表现：统计口径 = 图上**当前可见**的那一段，缩放/框选/切区间都会自动重算 -->
        <div class="range-stats">
          <div class="range-stats-head">
            <span class="section-title">区间表现</span>
            <span class="muted">按图上当前可见区间统计 · 缩放或框选会自动更新</span>
          </div>
          <el-table v-if="rangeStats" :data="[rangeStats]" size="small">
            <el-table-column prop="from" label="起始日" min-width="120" />
            <el-table-column prop="to" label="截止日" min-width="120" />
            <el-table-column prop="days" label="交易日" min-width="100" align="right">
              <template #default="{ row }"><span class="num">{{ row.days }}</span></template>
            </el-table-column>
            <el-table-column label="区间涨跌幅" min-width="130" align="right">
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.returnPct)">
                  {{ row.returnPct === null ? '--' : (row.returnPct > 0 ? '+' : '') + row.returnPct.toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="年化收益率" min-width="130" align="right">
              <template #default="{ row }">
                <span class="num" :class="changeColorClass(row.annualizedReturnPct)">
                  {{ row.annualizedReturnPct === null ? '--' : (row.annualizedReturnPct > 0 ? '+' : '') + row.annualizedReturnPct.toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="最大回撤" min-width="130" align="right">
              <template #default="{ row }">
                <span class="num text-down">{{ row.maxDrawdownPct === null ? '--' : row.maxDrawdownPct.toFixed(2) + '%' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="年化波动率" min-width="130" align="right">
              <template #default="{ row }">
                <span class="num">{{ row.volatilityPct === null ? '--' : row.volatilityPct.toFixed(2) + '%' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="当前区间数据不足，无法统计" :image-size="60" />
        </div>

        <!-- 全屏查看层（Esc 或右上角关闭退出） -->
        <Teleport to="body">
          <div v-if="fullscreen" class="chart-fullscreen">
            <div class="chart-fullscreen-head">
              <span class="section-title">{{ detail.fundName }} · 行情走势</span>
              <el-button link @click="fullscreen = false">关闭（Esc）</el-button>
            </div>
            <ChartPanel
              v-if="chartOption"
              :option="chartOption as EChartsOption"
              height="calc(100vh - 110px)"
              :brush="true"
              @brush-end="onBrushRange"
            />
          </div>
        </Teleport>
      </el-tab-pane>

      <!-- 「指数估值」页签已并入行情走势的「估值副图」（V5.57）：PE 走势与行情同一横轴对照查看，
           当前 PE 与分位摘要移到副图开关旁的工具栏提示；未识别跟踪指数/债券指数不适用 PE 的空态文案保留在提示位 -->

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
          <el-form inline>
            <el-form-item label="策略">
              <el-select v-model="backtestForm.strategyType" style="width: 150px" @change="handleBacktestTypeChange">
                <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始日期">
              <el-date-picker v-model="backtestForm.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="结束日期">
              <el-date-picker v-model="backtestForm.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="初始资金">
              <el-input-number v-model="backtestForm.initialCapital" :min="1000" :controls="false" style="width: 140px" />
            </el-form-item>
          </el-form>
          <!-- :key 强制重挂载：参数表单只在首次挂载时读 modelValue，不换 key 时外部回填不会显示 -->
          <StrategyParamForm
            :key="`bt-${backtestForm.strategyType}-${paramFormKey}`"
            v-model="backtestForm.params"
            :type="backtestForm.strategyType"
          />
          <div v-if="prefillHint" class="prefill-hint">{{ prefillHint }}</div>
          <el-button v-if="userStore.can(PERM.ACTION_STRATEGY)" type="primary" :loading="backtestRunning" @click="handleBacktest">开始回测</el-button>
        </el-card>
        <!-- 列宽口径：数字列 min-width 均分富余宽度；失败原因等长文本列用省略号 + 悬浮全显 -->
        <el-table :data="backtestRecords" border size="small">
          <el-table-column prop="id" label="#" width="56" />
          <el-table-column label="策略" min-width="92">
            <template #default="{ row }">{{ strategyNameOf(row.strategyType) }}</template>
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
          </el-table-column>
          <el-table-column label="策略详情" min-width="76">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openStrategyDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="失败原因" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMsg || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/backtest/${row.id}`)">结果</el-button>
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
      <el-form label-width="150px" class="strategy-form">
        <el-form-item label="策略类型">
          <!-- 编辑模式下禁切类型：类型是 (基金,类型) 唯一键的一半，换类型等于换策略，应删除重建 -->
          <el-select v-model="newStrategyType" style="width: 100%" :disabled="!!editingStrategyId">
            <el-option v-for="t in strategyTypeList" :key="t.type" :value="t.type" :label="t.name" />
          </el-select>
        </el-form-item>
        <StrategyParamForm
          :key="`new-${newStrategyType}-${paramFormKey}`"
          v-model="newStrategyParams"
          :type="newStrategyType"
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
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import {
  CANDLE_UP,
  CANDLE_DOWN,
  VOLUME,
  PE_LINE,
  PRIMARY,
  BENCHMARK,
  MA_COLORS,
  BOLL_MID,
  BOLL_BAND,
  MACD_DIF,
  MACD_DEA
} from '@/utils/palette'
import { ma, boll, macd } from '@/utils/indicators'
import { changeColorClass } from '@/utils/format'
import { annualizedReturnPct, annualizedVolatilityPct, maxDrawdownPct, rangeReturnPct } from '@/utils/metrics'
import { TEXT_INVERSE, TRADE_BUY, TRADE_DIVIDEND, TRADE_SELL } from '@/utils/palette'
import { useEscToClose } from '@/utils/escClose'

/** 均线组（行业默认参数） */
const MA_WINDOWS = [5, 10, 20, 60]

/** 布林带参数（20 日 ± 2 倍标准差，行业默认） */
const BOLL_PERIOD = 20
const BOLL_K = 2
import ChartPanel from '@/components/charts/ChartPanel.vue'
import FundTagEditDialog from '@/components/fund/FundTagEditDialog.vue'
import StrategyParamForm from '@/components/strategy/StrategyParamForm.vue'
import TradeEntryDialog from '@/components/trade/TradeEntryDialog.vue'
import {
  addStrategy,
  backtestDetail,
  createBacktest,
  deleteStrategy,
  fundStrategies,
  pageBacktest,
  strategyTypes,
  updateStrategy
} from '@/api/strategy'
import type { BacktestRecord, StrategyConfig, StrategyTypeVO } from '@/api/strategy'
import {
  deleteTrade,
  fundDetail,
  fundDividendYield,
  fundKline,
  fundMarks,
  fundNav,
  fundScaleHistory,
  fundTags,
  fundValuation,
  pageTrades,
  updateTrade
} from '@/api/fund'
import type {
  DividendYieldVO,
  FundDetailVO,
  FundMarkVO,
  FundScalePoint,
  FundTagVO,
  SeriesPoint,
  TradeFlow,
  TradeFlowRequest,
  ValuationSeriesVO
} from '@/api/fund'

const userStore = useUserStore()

const route = useRoute()
const code = route.params.code as string

const detail = ref<FundDetailVO | null>(null)
const activeTab = ref('chart')
/** 行情区间天数（近3月/1年/3年/5年/10年） */
const rangeDays = ref(365)
/** 场外基金的净值口径切换 */
const navMode = ref<'unitNav' | 'accNav' | 'adjNav'>('unitNav')
const chartOption = ref<EChartsOption | null>(null)
/** 自定义日期区间（yyyy-MM-dd）；设置后忽略区间下拉，支持图上框选回填 */
const chartDateRange = ref<[string, string] | null>(null)
/** 当前图表序列的日期轴（框选时把索引换算成日期用） */
const chartDates = ref<string[]>([])
/** 当前图表序列的数值（与图形口径一致：ETF 用收盘价、场外用当前所选净值口径） */
const chartValues = ref<number[]>([])
/** 图上可见窗口（百分比，0=序列首 100=序列尾）：随预设区间/框选/手动缩放变化 */
const visibleWindow = ref({ startPercent: 0, endPercent: 100 })

/**
 * 区间表现：按图上**当前可见**的窗口统计（所见即所测）。
 * 直接依赖日期/数值序列与可见窗口，缩放或框选后自动重算，无需再请求接口。
 */
const rangeStats = computed(() => {
  const dates = chartDates.value
  const values = chartValues.value
  if (dates.length < 2 || values.length !== dates.length) {
    return null
  }
  const startIndex = Math.max(0, Math.round((visibleWindow.value.startPercent / 100) * (dates.length - 1)))
  const endIndex = Math.min(dates.length - 1, Math.round((visibleWindow.value.endPercent / 100) * (dates.length - 1)))
  if (endIndex - startIndex < 1) {
    return null
  }
  const slice = values.slice(startIndex, endIndex + 1)
  return {
    from: dates[startIndex],
    to: dates[endIndex],
    days: slice.length,
    returnPct: rangeReturnPct(slice),
    annualizedReturnPct: annualizedReturnPct(slice, dates[startIndex], dates[endIndex]),
    maxDrawdownPct: maxDrawdownPct(slice),
    volatilityPct: annualizedVolatilityPct(slice)
  }
})

/** 用户缩放/拖动 dataZoom：更新可见窗口，区间表现随之刷新 */
function onZoomChange(range: { startPercent: number; endPercent: number }) {
  visibleWindow.value = range
}
/** 指标预热天数（自然日）：给 MA60/BOLL/MACD 留足前置数据 */
const INDICATOR_WARMUP_DAYS = 150

/**
 * 行情图交易标记（买入 b / 卖出 s / 分红 q）。
 * 每只基金只取一次并缓存：缩放、框选、换指标、切区间都复用这份数据，不重复请求；
 * 接口失败静默降级为"无标记"，不影响看 K 线。
 */
const tradeMarks = ref<FundMarkVO[]>([])

async function loadMarks() {
  tradeMarks.value = await fundMarks(code).catch(() => [])
}

/** 主图指标：MA（均线组 5/10/20/60）/ BOLL（20 日 ±2σ）/ NONE */
const mainIndicator = ref<'MA' | 'BOLL' | 'NONE'>('MA')

/** 是否显示 MACD 副图（DIF/DEA + 柱，柱按红涨绿跌着色） */
const showMacd = ref(false)

/** 股息率副图开关与数据（单次分红股息率 + TTM 滚动 12 个月） */
const showYield = ref(false)
const yieldData = ref<DividendYieldVO | null>(null)

/** 规模副图开关与数据（fund_scale_history 每日快照，自上线日起积累） */
const showScale = ref(false)
const scaleData = ref<FundScalePoint[] | null>(null)

/** 全屏查看状态（双击图表进入，Esc 或关闭按钮退出） */
const fullscreen = ref(false)

/** 图表高度随副图数量自适应 */
const chartHeight = computed(() => `${420 + subChartCount.value * 100}px`)

/** 除成交量外的副图数量（MACD / 股息率 / 规模 / 估值），用于图表高度与网格划分 */
const subChartCount = computed(
  () => (showMacd.value ? 1 : 0) + (showYield.value ? 1 : 0) + (showScale.value ? 1 : 0) + (valuationOn.value ? 1 : 0)
)

/** 估值副图是否实际渲染：开关打开且该指数确有 PE 数据（无数据时不占网格，只在工具栏出提示文案） */
const valuationOn = computed(() => showValuation.value && !!valuationData.value?.hasData)

/**
 * 副图纵向布局：主图 +（成交量）+（MACD）+（股息率）+（规模）+（估值）。
 * 图表数量随开关变化，写死百分比会在开关组合下互相重叠，故按数量算：
 * 每张副图占固定高度，主图吃掉剩余空间，副图之间留 gap，底部预留 dataZoom 滑块的位置。
 *
 * @param includeVolume 是否包含成交量网格（净值图没有成交量，传 false 时其余网格从主图下方直接排起）
 * @returns grids 每张图的 [top%, height%]（顺序：主图, 成交量?, MACD?, 股息率?, 规模?, 估值?）
 */
function subChartGrids(includeVolume = true): { top: number; height: number }[] {
  const extra = subChartCount.value
  const volHeight = includeVolume ? (extra === 0 ? 15 : 12) : 0
  const extraHeight = extra <= 1 ? 14 : 11
  const gap = 3
  const topStart = 9
  const bottomReserve = 12
  const total = topStart + bottomReserve + volHeight + extra * extraHeight + (1 + extra) * gap
  const mainHeight = Math.max(24, 100 - total)
  const grids = [{ top: topStart, height: mainHeight }]
  let cursor = topStart + mainHeight + gap
  if (includeVolume) {
    grids.push({ top: cursor, height: volHeight })
    cursor += volHeight + gap
  }
  for (let i = 0; i < extra; i++) {
    grids.push({ top: cursor, height: extraHeight })
    cursor += extraHeight + gap
  }
  return grids
}

/**
 * 追加「股息率副图」（独立百分比轴）：TTM 滚动 12 个月阶梯线 + 每次分红的散点。
 * 场内 K 线与场外净值图共用（网格下标由调用方按当前副图数量推出）。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴（用于把稀疏的阶跃点铺满）
 * @param gridIndex 该副图所在的网格下标
 */
function appendYieldSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({
    gridIndex,
    scale: true,
    axisLabel: { formatter: '{value}%', show: true },
    splitLine: { show: false }
  })
  const ttm = fillLatestByDate(
    dates,
    (yieldData.value?.ttm ?? []).map((p) => ({ date: p.date, value: p.yieldPct }))
  )
  const indexByDate = new Map(dates.map((date, i) => [date, i]))
  const events = (yieldData.value?.events ?? [])
    .filter((event) => event.yieldPct != null && indexByDate.has(event.date))
    .map((event) => ({ value: [indexByDate.get(event.date), event.yieldPct] }))
  series.push({
    type: 'line',
    name: '股息率TTM',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: ttm,
    step: 'end',
    connectNulls: false,
    showSymbol: false,
    // 灰色基准线：紫色 PE_LINE 留给「估值副图」的 PE 线，避免同屏两个紫色序列在图例里混淆（V5.57）
    lineStyle: { width: 1.6, color: BENCHMARK },
    itemStyle: { color: BENCHMARK }
  })
  if (events.length > 0) {
    series.push({
      type: 'scatter',
      name: '单次分红股息率',
      xAxisIndex: gridIndex,
      yAxisIndex: gridIndex,
      symbolSize: 7,
      data: events,
      itemStyle: { color: PRIMARY }
    })
  }
}

/**
 * 追加「基金规模副图」（独立单位：亿元）：规模快照的阶梯线。
 * 快照来自 fund_scale_history（每日档案刷新成功后落一行），
 * 每个交易日取"该日之前最近一次快照"的值，阶梯线直观呈现规模变化。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴
 * @param gridIndex 该副图所在的网格下标
 */
function appendScaleSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({
    gridIndex,
    scale: true,
    axisLabel: { formatter: '{value}亿', show: true },
    splitLine: { show: false }
  })
  series.push({
    type: 'line',
    name: '基金规模',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: fillLatestByDate(
      dates,
      (scaleData.value ?? []).map((p) => ({ date: p.date, value: Number(p.scale) }))
    ),
    step: 'end',
    connectNulls: false,
    // 数据点本身显示圆标：积累初期只有少数几天，仅画细线几乎看不见（V5.18 用户反馈"副图没加载出来"实为此因）
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 1.6, color: MACD_DEA },
    itemStyle: { color: MACD_DEA }
  })
}

/**
 * 把稀疏的 {date, value} 点铺到主图日期轴：每个交易日取"该日之前最近一点"的值（首点之前为空）。
 * 规模快照 / 股息率 TTM / 指数 PE 都是各自独立的日期序列，与行情图日期轴对齐都用这一把刷子
 * （三处逻辑原本各写一份，V5.57 收敛为一个函数，避免走散）。
 */
function fillLatestByDate(dates: string[], points: { date: string; value: number | null }[]): (number | null)[] {
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  let cursor = 0
  let current: number | null = null
  return dates.map((date) => {
    while (cursor < sorted.length && sorted[cursor].date <= date) {
      current = sorted[cursor].value
      cursor += 1
    }
    return current
  })
}

/**
 * 追加「估值副图」（独立坐标轴）：跟踪指数 PE 走势。
 * 指数 PE 与基金收盘价/净值是两套序列，按"该交易日之前最近的 PE"对齐到行情图日期轴，
 * 与主图共用同一横轴，估值与行情的相对高低、拐点先后一目了然（V5.57，原「指数估值」页签并入）。
 *
 * @param series    图表 series 数组（就地追加）
 * @param xAxes     x 轴数组（就地追加）
 * @param yAxes     y 轴数组（就地追加）
 * @param dates     主图日期轴
 * @param gridIndex 该副图所在的网格下标
 */
function appendValuationSubChart(
  series: Record<string, unknown>[],
  xAxes: Record<string, unknown>[],
  yAxes: Record<string, unknown>[],
  dates: string[],
  gridIndex: number
) {
  xAxes.push({ type: 'category', gridIndex, data: dates, axisLabel: { show: false } })
  yAxes.push({ gridIndex, scale: true, splitLine: { show: false } })
  series.push({
    type: 'line',
    name: 'PE',
    xAxisIndex: gridIndex,
    yAxisIndex: gridIndex,
    data: fillLatestByDate(
      dates,
      (valuationData.value?.series ?? []).map((p) => ({ date: p.date, value: p.pe ?? null }))
    ),
    connectNulls: false,
    showSymbol: false,
    lineStyle: { width: 1.6, color: PE_LINE },
    itemStyle: { color: PE_LINE },
    areaStyle: { opacity: 0.08, color: PE_LINE }
  })
}

/** 把 {top,height} 数字转成 ECharts 需要的百分号字符串 */
function toGridOption(bands: { top: number; height: number }[]): Record<string, unknown>[] {
  return bands.map((band) => ({
    left: '10%',
    right: '3%',
    top: `${band.top.toFixed(1)}%`,
    height: `${band.height.toFixed(1)}%`
  }))
}

/** 双击图表进入全屏查看 */
function openFullscreen() {
  fullscreen.value = true
}

// 全屏层按 Esc 退出（普通 div 无法接收 keydown，需全局监听）
useEscToClose(fullscreen)

/** 本基金已贴标签（来自预定义标签库） */
const tags = ref<FundTagVO[]>([])
const tagEditVisible = ref(false)

/** 拉取本基金标签 */
async function loadTags() {
  const result = await fundTags(code)
  tags.value = result.tags
}
/** 估值副图开关与数据：跟踪指数 PE 全历史一次拉取后缓存，区间切换/缩放只做本地按日期对齐，不再重复请求 */
const showValuation = ref(false)
const valuationData = ref<ValuationSeriesVO | null>(null)

/** 开/关估值副图：首次打开时拉一次指数 PE 全历史，再按当前区间重建图表 */
async function onValuationToggle() {
  if (showValuation.value && valuationData.value === null) {
    valuationData.value = await fundValuation(code).catch(() => null)
  }
  loadChart()
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

/**
 * 估值空态文案：区分三种情况，避免"跟踪指数已识别但该指数没有 PE"被误报成"未识别跟踪指数"。
 * 例如国债 ETF 跟踪的上证 5 年期国债指数属于债券指数，本就不适用 PE 估值。
 */
/** 运作费率明细（悬浮提示）：管理费 + 托管费 + 销售服务费 */
const detailFeeBreakdown = computed(() => {
  const part = (label: string, value: number | null | undefined) => `${label} ${(value ?? 0).toFixed(2)}%`
  return `${part('管理费', detail.value?.mgmtFeeRate)} + ${part('托管费', detail.value?.custFeeRate)} + `
    + `${part('销售服务费', detail.value?.salesFeeRate)}（年化）`
})

const valuationEmptyText = computed(() => {
  if (detail.value?.indexCode) {
    return '该指数暂无估值数据（数据源仅覆盖中证/上证系列指数）'
  }
  if (detail.value?.indexName) {
    return `跟踪指数「${detail.value.indexName}」不适用 PE 估值（债券/货币类指数无市盈率）`
  }
  return '未识别跟踪指数，估值不可用'
})

const valuationTagType = computed(() => {
  const p = valuationData.value?.currentPercentile
  if (p === null || p === undefined) return 'info'
  if (p <= 20) return 'success'
  if (p >= 80) return 'danger'
  return 'warning'
})

const valuationTagText = computed(() => {
  const p = valuationData.value?.currentPercentile
  if (p === null || p === undefined) return ''
  if (p <= 20) return '低估'
  if (p >= 80) return '高估'
  return '合理'
})

/** 估值副图工具栏摘要（原「指数估值」页签告警条的内容）：跟踪指数 + 当前 PE + 近10年分位 */
const valuationSummary = computed(() => {
  const v = valuationData.value
  if (!v?.hasData) return ''
  const pct = v.currentPercentile === null || v.currentPercentile === undefined ? '--' : `${v.currentPercentile}%`
  return `跟踪指数 ${v.indexName} · 当前 PE ${v.latestPe?.toFixed(2) ?? '--'} · 近10年 ${pct} 分位`
})

function tradeTypeText(type: number): string {
  return type === 1 ? '买入' : type === 2 ? '卖出' : '分红'
}

async function loadDetail() {
  detail.value = await fundDetail(code)
  await loadTags()
}

/**
 * 加载行情图。
 * 未指定日期时按区间下拉取数；指定了自定义日期区间（含框选）时：
 * ① 往前多取 INDICATOR_WARMUP_DAYS 天作为**指标预热段**——MA60/BOLL/MACD 需要足量前置数据，
 *    只取选区会让窗口开头的指标失真甚至为空；
 * ② 指标在完整序列上计算，再用 dataZoom 把显示窗口收敛到选区，做到"看某一段"又不丢指标精度。
 */
async function loadChart() {
  if (!detail.value) return
  const range = chartDateRange.value
  const start = range ? shiftDays(range[0], -INDICATOR_WARMUP_DAYS) : undefined
  const end = range ? range[1] : undefined
  // 股息率副图打开时才拉（并按区间缓存）：关掉副图就不产生任何额外请求
  if (showYield.value) {
    const span = range ? Math.max(1, daysBetween(shiftDays(range[0], -1), range[1])) : rangeDays.value
    yieldData.value = await fundDividendYield(code, span).catch(() => null)
  }
  // 规模副图打开时才拉：全量返回（自上线日起，量小）
  if (showScale.value) {
    scaleData.value = await fundScaleHistory(code).catch(() => [])
  }
  const points: SeriesPoint[] =
    detail.value.fundType === 1
      ? await fundKline(code, rangeDays.value, start, end)
      : await fundNav(code, rangeDays.value, start, end)
  const base = detail.value.fundType === 1 ? klineOption(points) : navOption(points, navMode.value)
  chartDates.value = points.map((point) => point.date)
  chartValues.value = points.map((point) =>
    detail.value?.fundType === 1 ? Number(point.close) : Number(point[navMode.value] ?? point.unitNav)
  )
  const withMarks = appendTradeMarks(range ? withWindow(base, chartDates.value, range) : base, points)
  chartOption.value = withMarks
  // 初始可见窗口取 option 上的 dataZoom（预设区间默认显示最近 40%、自定义区间为选区）
  const zooms = (withMarks.dataZoom as Array<{ start?: number; end?: number }> | undefined) ?? []
  visibleWindow.value = { startPercent: zooms[0]?.start ?? 0, endPercent: zooms[0]?.end ?? 100 }
}

/** 切换区间下拉时清掉自定义日期（两者互斥） */
function onPresetRangeChange() {
  chartDateRange.value = null
  loadChart()
}

/** 在图上框选一段：把选中的起止日期回填到日期选择器并重新加载该区间 */
function onBrushRange(selected: { startIndex: number; endIndex: number }) {
  const dates = chartDates.value
  if (dates.length === 0) return
  const from = dates[Math.round(selected.startIndex)]
  const to = dates[Math.round(selected.endIndex)]
  if (!from || !to || from >= to) return
  chartDateRange.value = [from, to]
  rangeDays.value = rangeDays.value
  loadChart()
}

/** 两个 yyyy-MM-dd 之间相差的天数 */
function daysBetween(from: string, to: string): number {
  const [y1, m1, d1] = from.split('-').map(Number)
  const [y2, m2, d2] = to.split('-').map(Number)
  return Math.round((new Date(y2, m2 - 1, d2).getTime() - new Date(y1, m1 - 1, d1).getTime()) / 86400000)
}

/** 日期字符串加减天数（按本地时区构造，避免 UTC 偏移导致差一天） */
function shiftDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number)
  const base = new Date(y, m - 1, d + days)
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}-${String(base.getDate()).padStart(2, '0')}`
}

/**
 * 在行情图上叠加交易标记串（b 买入 / s 卖出 / q 分红除息）。
 *
 * <p>实现要点（性能相关）：
 * <ul>
 *   <li>标记全部一次性放进 scatter 系列，**由 ECharts 按可见窗口自动裁剪**——
 *       缩放/框选只重绘、不重新请求，也不需要前端再算一次可见集合；</li>
 *   <li>后端已按"同一天同类型"合并，十年长周期也只有几十个点，渲染开销可忽略；</li>
 *   <li>标记不参与 tooltip（避免污染原有的 K 线悬停信息），字符本身即标识，
 *       具体明细看下方"交易流水"表。</li>
 * </ul>
 *
 * @param option 已经构建好的图表配置
 * @param points 当前序列（K 线用 low 定位、净值用当日值定位）
 */
function appendTradeMarks(option: EChartsOption, points: SeriesPoint[]): EChartsOption {
  if (tradeMarks.value.length === 0 || points.length === 0) {
    return option
  }
  const dates = points.map((point) => point.date)
  const indexByDate = new Map(dates.map((date, index) => [date, index]))
  const anchors = points.map((point) =>
    detail.value?.fundType === 1 ? Number(point.low ?? point.close) : Number(point[navMode.value] ?? point.unitNav)
  )
  const data: Record<string, unknown>[] = []
  for (const mark of tradeMarks.value) {
    // 记账日期可能不在该序列（非交易日、或早于取数区间）：顺延到最近的交易日，避免整条标记丢失
    let index = indexByDate.get(mark.date)
    if (index === undefined) {
      const next = dates.findIndex((date) => date >= mark.date)
      index = next < 0 ? undefined : next
    }
    if (index === undefined || !Number.isFinite(anchors[index])) {
      continue
    }
    const letter = mark.kind === 'BUY' ? 'b' : mark.kind === 'SELL' ? 's' : 'q'
    const color = mark.kind === 'BUY' ? TRADE_BUY : mark.kind === 'SELL' ? TRADE_SELL : TRADE_DIVIDEND
    data.push({
      value: [index, anchors[index]],
      name: letter,
      label: { formatter: letter },
      itemStyle: { color },
      markText: mark.text
    })
  }
  if (data.length === 0) {
    return option
  }
  const series = ((option.series as Record<string, unknown>[]) ?? []).slice()
  series.push({
    name: '交易标记',
    type: 'scatter',
    xAxisIndex: 0,
    yAxisIndex: 0,
    symbol: 'circle',
    symbolSize: 16,
    // 往下错开一点，避免与当日 K 线实体重叠
    symbolOffset: [0, 12],
    label: { show: true, position: 'inside', color: TEXT_INVERSE, fontSize: 11, fontWeight: 600 },
    // 标记不进 tooltip：原有 K 线/净值悬停信息保持干净
    tooltip: { show: false },
    z: 12,
    data
  })
  return { ...option, series } as EChartsOption
}

/** 把 dataZoom 的显示窗口收敛到 [from, to]（序列里可能没有正好等于端点的交易日，按区间取最近边界） */
function withWindow(option: EChartsOption, dates: string[], range: [string, string]): EChartsOption {
  const startIndex = dates.findIndex((date) => date >= range[0])
  let endIndex = -1
  for (let i = dates.length - 1; i >= 0; i--) {
    if (dates[i] <= range[1]) {
      endIndex = i
      break
    }
  }
  if (startIndex < 0 || endIndex < 0 || dates.length < 2) {
    return option
  }
  const percent = (index: number) => Number(((index / (dates.length - 1)) * 100).toFixed(2))
  const zooms = (option.dataZoom as Record<string, unknown>[] | undefined) ?? []
  return {
    ...option,
    dataZoom: zooms.map((zoom) => ({ ...zoom, start: percent(startIndex), end: percent(Math.max(endIndex, startIndex + 1)) }))
  }
}

/**
 * ETF 前复权 K 线图：主图（蜡烛 + MA/BOLL 二选一）+ 成交量 + 可选 MACD 副图。
 * 网格按副图数量动态划分：带 MACD 时主图收窄、成交量上移、MACD 占底部。
 */
function klineOption(points: SeriesPoint[]): EChartsOption {
  const k = points.map((p) => [p.open, p.close, p.low, p.high])
  const dates = points.map((p) => p.date)
  const closes = points.map((p) => Number(p.close))
  const volumes = points.map((p) => p.volume)
  const macdOn = showMacd.value
  const yieldOn = showYield.value
  const scaleOn = showScale.value
  const grids: Record<string, unknown>[] = toGridOption(subChartGrids())
  const xAxes: Record<string, unknown>[] = [
    { type: 'category', data: dates, boundaryGap: true },
    { type: 'category', gridIndex: 1, data: dates, axisLabel: { show: false } }
  ]
  const yAxes: Record<string, unknown>[] = [
    { scale: true },
    { gridIndex: 1, axisLabel: { show: false }, splitLine: { show: false } }
  ]
  const series: Record<string, unknown>[] = [
    {
      type: 'candlestick',
      name: 'K线',
      data: k,
      itemStyle: { color: CANDLE_UP, color0: CANDLE_DOWN, borderColor: CANDLE_UP, borderColor0: CANDLE_DOWN }
    },
    {
      type: 'bar',
      name: '成交量',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: volumes,
      itemStyle: { color: VOLUME }
    }
  ]

  // 主图指标（互斥切换，与雪球式主图指标一致）
  if (mainIndicator.value === 'MA') {
    MA_WINDOWS.forEach((window, index) => {
      series.push({
        type: 'line',
        name: `MA${window}`,
        data: ma(closes, window),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.2, color: MA_COLORS[index % MA_COLORS.length] },
        itemStyle: { color: MA_COLORS[index % MA_COLORS.length] }
      })
    })
  } else if (mainIndicator.value === 'BOLL') {
    const bands = boll(closes, BOLL_PERIOD, BOLL_K)
    series.push(
      {
        type: 'line',
        name: `BOLL(${BOLL_PERIOD})`,
        data: bands.mid,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: BOLL_MID },
        itemStyle: { color: BOLL_MID }
      },
      {
        type: 'line',
        name: '上轨',
        data: bands.upper,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      },
      {
        type: 'line',
        name: '下轨',
        data: bands.lower,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      }
    )
  }

  // MACD 副图（柱按红涨绿跌）
  if (macdOn) {
    xAxes.push({ type: 'category', gridIndex: 2, data: dates, axisLabel: { show: false } })
    yAxes.push({ gridIndex: 2, axisLabel: { show: false }, splitLine: { show: false } })
    const result = macd(closes)
    series.push(
      {
        type: 'bar',
        name: 'MACD',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.bar.map((value) => ({
          value,
          itemStyle: { color: value !== null && value >= 0 ? CANDLE_UP : CANDLE_DOWN }
        }))
      },
      {
        type: 'line',
        name: 'DIF',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.dif,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DIF },
        itemStyle: { color: MACD_DIF }
      },
      {
        type: 'line',
        name: 'DEA',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: result.dea,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DEA },
        itemStyle: { color: MACD_DEA }
      }
    )
  }

  if (yieldOn) {
    appendYieldSubChart(series, xAxes, yAxes, dates, macdOn ? 3 : 2)
  }
  if (scaleOn) {
    appendScaleSubChart(series, xAxes, yAxes, dates, (macdOn ? 1 : 0) + (yieldOn ? 1 : 0) + 2)
  }
  if (valuationOn.value) {
    appendValuationSubChart(series, xAxes, yAxes, dates, (macdOn ? 1 : 0) + (yieldOn ? 1 : 0) + (scaleOn ? 1 : 0) + 2)
  }

  const axisIndexes = Array.from({ length: 2 + subChartCount.value }, (_, i) => i)
  return {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, left: 'center', itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom: [
      { type: 'inside', xAxisIndex: axisIndexes, start: 60, end: 100 },
      { type: 'slider', xAxisIndex: axisIndexes, height: 16, bottom: 4 }
    ],
    series
  } as EChartsOption
}

/**
 * 场外净值图：与 ETF 口径一致地支持主图指标与 MACD 副图——
 * 指标基于**当前所选净值序列**计算（单位/累计/复权三选一），切换口径即重算。
 */
function navOption(points: SeriesPoint[], mode: 'unitNav' | 'accNav' | 'adjNav'): EChartsOption {
  const dates = points.map((p) => p.date)
  const values = points.map((p) => Number(p[mode]))
  const macdOn = showMacd.value
  const yieldOn = showYield.value
  const scaleOn = showScale.value
  // 净值图没有成交量副图：网格直接从主图下方排起（MACD 打开时网格 1 归 MACD）
  const grids: Record<string, unknown>[] = toGridOption(subChartGrids(false))
  const xAxes: Record<string, unknown>[] = [
    { type: 'category', data: dates },
    { type: 'category', gridIndex: 1, data: dates, axisLabel: { show: false } }
  ]
  const yAxes: Record<string, unknown>[] = [
    { type: 'value', scale: true },
    { gridIndex: 1, axisLabel: { show: false }, splitLine: { show: false } }
  ]
  const series: Record<string, unknown>[] = [
    {
      type: 'line',
      name: navModeLabel(mode),
      data: values,
      showSymbol: false,
      itemStyle: { color: PRIMARY },
      lineStyle: { color: PRIMARY, width: 1.6 }
    }
  ]

  // 主图指标：MA 组 / BOLL（与 ETF 一致）
  if (mainIndicator.value === 'MA') {
    MA_WINDOWS.forEach((window, index) => {
      series.push({
        type: 'line',
        name: `MA${window}`,
        data: ma(values, window),
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.2, color: MA_COLORS[index % MA_COLORS.length] },
        itemStyle: { color: MA_COLORS[index % MA_COLORS.length] }
      })
    })
  } else if (mainIndicator.value === 'BOLL') {
    const bands = boll(values, BOLL_PERIOD, BOLL_K)
    series.push(
      {
        type: 'line',
        name: `BOLL(${BOLL_PERIOD})`,
        data: bands.mid,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.3, color: BOLL_MID },
        itemStyle: { color: BOLL_MID }
      },
      {
        type: 'line',
        name: '上轨',
        data: bands.upper,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      },
      {
        type: 'line',
        name: '下轨',
        data: bands.lower,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1, type: 'dashed', color: BOLL_BAND },
        itemStyle: { color: BOLL_BAND }
      }
    )
  }

  // MACD 副图（与 K 线图不同：净值图没有成交量，网格 1 直接给 MACD 用；关闭时移除该占位网格）
  let nextGrid = 1
  if (macdOn) {
    const result = macd(values)
    series.push(
      {
        type: 'bar',
        name: 'MACD',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.bar.map((value) => ({
          value,
          itemStyle: { color: value !== null && value >= 0 ? CANDLE_UP : CANDLE_DOWN }
        }))
      },
      {
        type: 'line',
        name: 'DIF',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.dif,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DIF },
        itemStyle: { color: MACD_DIF }
      },
      {
        type: 'line',
        name: 'DEA',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: result.dea,
        showSymbol: false,
        lineStyle: { width: 1.1, color: MACD_DEA },
        itemStyle: { color: MACD_DEA }
      }
    )
    nextGrid = 2
  }

  // 其余副图用游标依次分配网格：写死下标在开关组合下会空格或越界（V5.57 随估值副图一并修正）
  if (yieldOn) {
    appendYieldSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }
  if (scaleOn) {
    appendScaleSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }
  if (valuationOn.value) {
    appendValuationSubChart(series, xAxes, yAxes, dates, nextGrid)
    nextGrid += 1
  }

  // 轴数随开关组合变化：MACD 关闭时网格 1 不存在，轴从 1 根起算（K 线图恒为 2 + 副图数）
  const axisIndexes = Array.from({ length: (macdOn ? 2 : 1) + subChartCount.value }, (_, i) => i)
  return {
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { top: 0, left: 'center', itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes,
    dataZoom: [
      { type: 'inside', xAxisIndex: axisIndexes, start: 70, end: 100 },
      { type: 'slider', xAxisIndex: axisIndexes, height: 16, bottom: 4 }
    ],
    series
  } as EChartsOption
}

/** 净值口径的中文名（图例展示） */
function navModeLabel(mode: 'unitNav' | 'accNav' | 'adjNav'): string {
  return mode === 'unitNav' ? '单位净值' : mode === 'accNav' ? '累计净值' : '复权净值'
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

watch(navMode, () => loadChart())

// ===== 策略配置与回测 =====
const strategies = ref<StrategyConfig[]>([])
const strategyTypeList = ref<StrategyTypeVO[]>([])

/** 策略类型 → 展示名（来自注册表；未知类型回退类型码） */
function strategyNameOf(type: string): string {
  return strategyTypeList.value.find((t) => t.type === type)?.name ?? type
}

/** 策略配置详情弹框 */
const strategyDetailVisible = ref(false)
const strategyDetailRow = ref<BacktestRecord | null>(null)
const strategyDetailItems = ref<{ label: string; value: string }[]>([])

/** 各策略参数的中文名（与 StrategyParamForm 的界面名一致） */
const PARAM_LABELS: Record<string, Record<string, string>> = {
  // 已下线策略：仅保留标签让历史回测/旧配置还能读懂（V5.28 从平台移除，不再可选、不可回测）
  GRID: {
    mode: '网格模式(已下线)', upper: '网格上沿(已下线)', lower: '网格下沿(已下线)', grids: '格数(已下线)',
    sharePerGrid: '每格份额(已下线)', basePosition: '底仓份额(已下线)', anchorPrice: '锚点价(已下线)'
  },
  VAL_PERCENTILE: {
    lowPct: '低估阈值%(已下线)', highPct: '高估阈值%(已下线)', steps: '分档数(已下线)',
    windowYears: '回看窗口(年)(已下线)', sharePerStep: '每档份额(已下线)', basePosition: '底仓份额(已下线)'
  },
  OSC_UP: {
    initialShare: '初始仓位份额', baseShare: '底仓份额', fullShare: '满仓份额', windowDays: 'K线天数',
    riseReducePct: '上涨减仓%', fallAddPct: '下跌加仓%',
    buyShare: '买入份额', sellShare: '卖出份额',
    sizingStepPct: '每档份额增减%', sizingBase: '档位基准', maxSizingMultiple: '单笔最大倍数'
  },
  DIV_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  NDX_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  PYRAMID_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额', pyramidStep: '每格增减',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  },
  INV_PYRAMID_GRID: {
    mode: '网格模式', lower: '网格下沿', upper: '网格上沿', grids: '格数',
    perGridMode: '每格单位', sharePerGrid: '每格份额', amountPerGrid: '每格金额', pyramidStep: '每格增减',
    baseShare: '底仓份额', fullShare: '满仓份额', initialShare: '初始仓位份额',
    breakoutMode: '涨破上沿', breakdownMode: '跌破下沿', maxGridsPerBar: '单根最多成交格数',
    trendMaDays: '趋势均线天数', premiumBuyMaxPct: '溢价率买入上限%',
    premiumStaleDays: '溢价率容忍滞后(天)', backtestPremiumPct: '回测假设溢价率%',
    peBuyMax: 'PE 买入上限', peBuyMin: 'PE 买入下限', peBoostMultiplier: '低估买入倍数'
  }
}

/**
 * 按标签表的键序排列参数（标签表里有的排前面、保持阅读顺序，未知键排在后面）。
 */
function orderByLabels(parsed: Record<string, unknown>, labels: Record<string, string>): [string, unknown][] {
  const known = Object.keys(labels)
  return Object.entries(parsed).sort((a, b) => {
    const ia = known.indexOf(a[0])
    const ib = known.indexOf(b[0])
    return (ia < 0 ? Number.MAX_SAFE_INTEGER : ia) - (ib < 0 ? Number.MAX_SAFE_INTEGER : ib)
  })
}

/** 枚举型参数的展示文案（列表摘要与详情弹框共用）：值 → 中文，未命中回退原值 */
const ENUM_LABELS: Record<string, Record<string, string>> = {
  mode: { arithmetic: '等差', geometric: '等比' },
  perGridMode: { share: '按份额', amount: '按金额' },
  breakoutMode: { shift: '区间上移', hold: '保留底仓不动', clear: '清到只剩底仓' },
  breakdownMode: { hold: '买 1 格后观望', buy: '区间下方继续按格买入' },
  sizingBase: { anchor: '锚点窗口', window: 'K线窗口' }
}

/** 参数值展示：枚举值翻中文，其余原样 */
function displayValue(key: string, value: unknown): string {
  const text = String(value)
  return ENUM_LABELS[key]?.[text] ?? text
}



/** 打开策略配置详情弹框：解析 params JSON → 带中文标签的键值列表（未知键回退原始键名） */
function openStrategyDetail(row: BacktestRecord) {
  strategyDetailRow.value = row
  const labels = PARAM_LABELS[row.strategyType] ?? {}
  let parsed: Record<string, unknown> = {}
  try {
    parsed = JSON.parse(row.params) as Record<string, unknown>
  } catch {
    parsed = {}
  }
  strategyDetailItems.value = orderByLabels(parsed, labels)
    .map(([key, value]: [string, unknown]) => ({
      label: labels[key] ?? key,
      value: displayValue(key, value)
    }))
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
 * 取本基金「最近一次该策略的回测参数」（回测列表按 id 倒序，取第一条同类型记录）。
 * 没有历史返回 null，由参数表单填默认值。
 */
async function lastBacktestParams(type: string): Promise<{ params: Record<string, unknown>; date: string } | null> {
  if (!type) {
    return null
  }
  try {
    const result = await pageBacktest(code, 1, 20)
    const hit = result.records.find((r) => r.strategyType === type)
    if (!hit) {
      return null
    }
    return { params: JSON.parse(hit.params) as Record<string, unknown>, date: hit.endDate }
  } catch {
    return null
  }
}

/**
 * 按策略回填参数（取不到则清空 → 表单用默认值），强制表单重挂载并给出提示。
 *
 * @param type   策略类型
 * @param target form=发起回测表单，dialog=新增策略弹框
 */
async function applyBacktestParams(type: string, target: 'form' | 'dialog') {
  const hit = await lastBacktestParams(type)
  if (target === 'form') {
    backtestForm.params = hit ? { ...hit.params } : {}
  } else {
    newStrategyParams.value = hit ? { ...hit.params } : {}
  }
  paramFormKey.value += 1
  prefillHint.value = hit
    ? `已回填「${strategyNameOf(type)}」上次回测（截至 ${hit.date}）的参数，可直接开始回测或按需修改`
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

/** 默认策略：本基金已配置的 → 最近一次回测的 → 注册表里的第一个 */
function defaultStrategyType(): string {
  if (strategies.value.length > 0) {
    return strategies.value[0].strategyType
  }
  const lastType = backtestRecords.value[0]?.strategyType
  if (lastType) {
    return lastType
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

async function handleBacktest() {
  if (!backtestForm.startDate || !backtestForm.endDate) {
    ElMessage.warning('请选择回测区间')
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
  loadMarks()
  loadDetail().then(() => loadChart())
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

/* 图例里的标记字：与图上气泡同色，说明 b/s/q 的含义 */
.mark-b {
  color: var(--q-color-up);
}

.mark-s {
  color: var(--q-color-down);
}

.mark-q {
  color: var(--q-color-primary);
}

.range-stats {
  margin-top: var(--q-space-4);
}

.range-stats-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--q-space-2);
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

.chart-toolbar {
  display: flex;
  align-items: center;
  gap: var(--q-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--q-space-3);
}

.chart-toolbar-label {
  font-size: var(--q-font-xs);
  color: var(--q-text-muted);
}

/* 全屏查看层：覆盖视口，保留标题与关闭入口（Esc 可退出） */
.chart-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: var(--q-space-3);
  padding: var(--q-space-4);
  background: var(--q-bg-card);
}

.chart-fullscreen-head {
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
</style>
