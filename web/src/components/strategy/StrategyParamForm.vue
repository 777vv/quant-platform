<template>
  <!-- 自适应网格：容器有多宽就排几列（每列至少 300px），避免每个参数独占一整行留出大片空白 -->
  <div class="param-grid">
    <template v-if="type === 'OSC_UP'">
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

      <el-form-item>
        <template #label><ParamLabel text="每档份额增减%" help="分档功能（V5.29）：填 0＝关闭，买卖都用固定份额（与历史行为一致）；正数＝越跌买越多、越涨卖越多（金字塔式）；负数＝越跌买越少、越涨卖越少（倒金字塔式）。第 n 档份额 = 固定份额 × [1 + (n−1)×此值]，n = 触发幅度 ÷ 阈值。例：买入份额 5000、阈值 5%、此值 50 → 跌 5% 买 5000、跌 10% 买 7500、跌 15% 买 10000。" /></template>
        <el-input-number v-model="form.sizingStepPct" :precision="1" :min="-99" :max="500" :controls="false" />
      </el-form-item>
      <el-form-item v-if="Number(form.sizingStepPct ?? 0) !== 0">
        <template #label><ParamLabel text="档位基准" help="档位从哪个区间算幅度。锚点窗口（默认）：跟着「上次实际交易」走，每次成交后档位重新起算——与触发口径完全一致，渐进下跌里多为第 1 档，只有急跌/跳空那种「一个窗口内跌得更狠」才会进第 2、3 档；K线窗口：始终按近 K线天数 根算幅度、不随成交重置，渐进下跌会一路累加到第 3、4 档，放大效应更明显（但与「锚点=实际成交」的口径不一致）。" /></template>
        <el-radio-group v-model="form.sizingBase">
          <el-radio-button value="anchor">锚点窗口</el-radio-button>
          <el-radio-button value="window">K线窗口</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="Number(form.sizingStepPct ?? 0) > 0">
        <template #label><ParamLabel text="单笔最大倍数" help="深档时的倍数上限（默认 3）：防止「越跌越多」在深跌里把单笔买到过大。份额最终仍受满仓上限夹取。" /></template>
        <el-input-number v-model="form.maxSizingMultiple" :precision="1" :min="1" :max="10" :controls="false" />
      </el-form-item>
      <div class="osc-summary">
        <div class="osc-title">策略逻辑速览</div>
        <ul>
          <li><b>对比区间</b>：从「K线天数那根」或「上次实际交易那根」中<b>更靠近今天</b>的一根开始，到今天为止。</li>
          <li><b>上涨减仓</b>：现价较区间<b>最低点</b>涨幅超过阈值 → 卖出「卖出份额」份（不会低于底仓）。</li>
          <li><b>下跌加仓</b>：现价较区间<b>最高点</b>跌幅超过阈值 → 买入「买入份额」份（不会超过满仓）。</li>
          <li><b>同时满足</b>时<b>买入优先</b>；夹取后可交易份额为 0（已满仓/已到底仓）不出信号。</li>
          <li><b>2 个交易日冷却</b>：距上次<b>实际交易</b>不足 2 个交易日时只提示不交易；信号没照做（无交易流水）不算数，次日照样重新提示。</li>
          <li v-if="Number(form.sizingStepPct ?? 0) !== 0"><b>分档份额</b>：{{ sizingSummary }}——深档的份额变化会写在建议说明里，便于核对。</li>
        </ul>
      </div>
    </template>

    <!-- 均线突破买入（V5.73）：趋势策略——收盘价上穿突破均线买入至满仓、下穿跌破均线卖出至底仓 -->
    <template v-else-if="type === 'MA_BREAK'">
      <el-form-item>
        <template #label><ParamLabel text="初始仓位份额" help="仅回测首日生效：开始回测时先按这个份额买入建仓（份），后续从「已持有初始仓位」起步；填 0 等于不建仓。取值 0 ~ 满仓份额。" /></template>
        <el-input-number v-model="form.initialShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="底仓份额" help="卖出下限：跌破卖出信号触发后的持仓份额不低于这个数（份）。须小于满仓份额。" /></template>
        <el-input-number v-model="form.baseShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="满仓份额" help="买入上限：突破买入信号触发后直接买入到这个份额（份）。请让它与你的可用资金匹配，回测时本金必须买得起它。" /></template>
        <el-input-number v-model="form.fullShare" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="均线突破（日）" help="买入信号：收盘价【上穿】这条均线时触发买入，直接买入至满仓份额。例如填 60 = 收盘价上穿 60 日均线买入。取值 2~500 个交易日；周期越大信号越少越迟。" /></template>
        <el-input-number v-model="form.breakoutMaDays" :min="2" :max="500" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="均线跌破（日）" help="信号均线：收盘价【下穿】这条均线时触发「跌破时操作」。例如填 30 = 收盘价跌破 30 日均线触发。取值 2~500 个交易日。" /></template>
        <el-input-number v-model="form.breakdownMaDays" :min="2" :max="500" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="突破时操作" help="上穿「均线突破」均线时执行：买入至满仓 / 卖出至底仓。与「跌破时操作」必须一买一卖相反（如突破买入 + 跌破卖出 = 趋势跟随；突破卖出 + 跌破买入 = 均值回归）。" /></template>
        <el-radio-group v-model="form.breakoutAction" @change="onBreakoutActionChange">
          <el-radio-button value="BUY">买入</el-radio-button>
          <el-radio-button value="SELL">卖出</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="跌破时操作" help="下穿「均线跌破」均线时执行（自动与突破方向相反）。" /></template>
        <el-radio-group v-model="form.breakdownAction">
          <el-radio-button value="BUY">买入</el-radio-button>
          <el-radio-button value="SELL">卖出</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="冷静天数" help="最近一次交易后的 N 个交易日内不再重复交易（0=不冷静）。冷却期内触发的信号会暂挂：冷却结束后复核均线状态，仍满足（如现价仍低于跌破均线）才执行，已恢复则保持仓位不动。" /></template>
        <el-input-number v-model="form.cooldownDays" :min="0" :max="500" :controls="false" />
      </el-form-item>
      <div class="osc-summary">
        <div class="osc-title">策略逻辑速览</div>
        <ul>
          <li><b>均线突破买入</b>：前一交易日收盘价在「均线突破」均线上或下方、<b>当日收盘价上穿</b>该均线 → 直接买入至<b>满仓份额</b>（按次日开盘价成交）。</li>
          <li><b>均线跌破卖出</b>：前一交易日在均线上、<b>当日收盘价下穿</b>「均线跌破」均线 → 直接卖出至<b>底仓份额</b>（按次日开盘价成交）。</li>
          <li><b>两个信号同一天都触发</b>时<b>买入优先</b>（与震荡向上策略同一规则）；已满仓时突破信号不出、已到底仓时跌破信号不出。</li>
          <li><b>冷静天数</b>：最近一次交易后 N 个交易日内不交易；冷却结束后复核均线状态——仍满足条件才补执行，已恢复则不动。</li>
          <li><b>均线周期越长</b>信号越少越迟（250 日均线一年只上下穿一两次）；周期太小（如 5 日）信号频繁、噪音多。</li>
          <li><b>均线周期越大</b>，回测所需的历史预热数据越多——请把回测起始日期往前留足（250 日均线约需 1 年以上历史）。</li>
        </ul>
      </div>
    </template>

    <!-- 网格族（V5.27 红利/纳指网格；V5.28 金字塔/倒金字塔网格）：四个类型共用同一套参数与规则，
         差异只在默认值取向——金字塔族多一个「每格增减」参数控制逐格份额阶梯 -->
    <template v-else-if="isGridType">
      <el-form-item>
        <template #label><ParamLabel text="网格模式" help="等差：每格价差固定（上沿−下沿）÷格数，适合窄幅震荡（红利）；等比：每格固定百分比，适合跨度大、长期抬升的品种（纳指这类跨境）。" /></template>
        <el-radio-group v-model="form.mode">
          <el-radio-button value="arithmetic">等差</el-radio-button>
          <el-radio-button value="geometric">等比</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="网格下沿" help="网格区间最低价。跌破它算「下穿一格」照常买入，之后按「跌破下沿」的方式处理。格线固定不漂移，请按品种近 1~2 年的实际波动区间设置。" /></template>
        <el-input-number v-model="form.lower" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="网格上沿" help="网格区间最高价。涨破它按「涨破上沿」的方式处理（区间上移 / 保留底仓 / 清到只剩底仓）。" /></template>
        <el-input-number v-model="form.upper" :precision="3" :min="0.001" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="格数" help="上下沿之间切几格（2~200）。格数越多每格越小、交易越频繁、费损越高；建议单格幅度不低于 2 倍日均波动。" /></template>
        <el-input-number v-model="form.grids" :min="2" :max="200" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="每格单位" help="按份额：每格固定买卖多少份；按金额：每格固定买卖多少钱，价格越低自动买到越多份（成本更平滑）。" /></template>
        <el-radio-group v-model="form.perGridMode">
          <el-radio-button value="share">份额</el-radio-button>
          <el-radio-button value="amount">金额</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.perGridMode === 'amount'">
        <template #label><ParamLabel text="每格金额" help="每跨一格买卖的金额（元）；实际份额 = 金额 ÷ 现价（向下取整到 0.01 份）。" /></template>
        <el-input-number v-model="form.amountPerGrid" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item v-else>
        <template #label><ParamLabel text="每格份额" help="每跨一格买卖的份额（份）。单根 K 线跳空穿多格时按格数成交多倍（受「单根最多成交格数」限幅）。" /></template>
        <el-input-number v-model="form.sharePerGrid" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item v-if="isPyramidType">
        <template #label><ParamLabel text="每格增减" help="金字塔阶梯：每深一格的成交单位比上一格增减多少（与「每格单位」同单位）。正数＝越跌买越多（金字塔，底部重仓摊成本）；负数＝越跌买越少（倒金字塔，底部轻仓）；填 0＝每格等量（普通网格）。买卖共用同一条阶梯，所以低位买得多、高位也卖得多。例：首格 500 份、每格 +250 → 500/750/1000/1250…" /></template>
        <el-input-number v-model="form.pyramidStep" :precision="2" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="底仓份额" help="卖出下限：建议卖出后的持仓不低于这个数（份）。红利建议留足底仓（收益主要来自长期持有），纳指可留少一些。" /></template>
        <el-input-number v-model="form.baseShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="满仓份额" help="买入上限：建议买入后的持仓不超过这个数（份）。请与可用资金匹配——这是防止单边下跌把子弹打光的硬约束。" /></template>
        <el-input-number v-model="form.fullShare" :min="1" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="初始仓位份额" help="仅回测首日生效：回测开始时先按这个份额建仓（份），后续从「已持有初始仓位」起步；填 0 等于不建仓。实盘不受影响（始终按实际持仓判断）。⚠️ 请让它大于底仓份额，否则一建仓就贴住卖出下限、网格只买不卖。" /></template>
        <el-input-number v-model="form.initialShare" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="涨破上沿" help="区间上移：涨破后把整个网格抬高到现价重新落回区间内（跟着趋势走台阶），适合长期创新高的品种（纳指）；保留底仓：按格卖出到上沿容量后保留底仓、区间不动，适合慢牛（红利）；清到只剩底仓：涨破即把网格份额一次性卖光。" /></template>
        <el-select v-model="form.breakoutMode">
          <el-option value="shift" label="区间上移（移动网格）" />
          <el-option value="hold" label="保留底仓、区间不动" />
          <el-option value="clear" label="清到只剩底仓" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="跌破下沿" help="买 1 格后观望：跌破下沿算下穿一格、照常买入，之后停在区间外观望；继续按格买入：把下沿之下也当成格子，每多跌一格再买一格（受满仓上限约束）。" /></template>
        <el-select v-model="form.breakdownMode">
          <el-option value="hold" label="买 1 格后观望" />
          <el-option value="buy" label="区间下方继续按格买入" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="单根最多成交格数" help="一根 K 线（或一次跳空）最多按几格成交，防止极端跳空一次买太多；填 0 表示不限。" /></template>
        <el-input-number v-model="form.maxGridsPerBar" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="趋势均线天数" help="填 0 关闭。开启后现价低于该均线时禁止买入（卖出不受限），避免跌势里一路接飞刀；均线根数不足时闸门不生效并在建议里说明。纳指建议 60，红利可关闭。" /></template>
        <el-input-number v-model="form.trendMaDays" :min="0" :max="250" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="溢价率买入上限%" help="填 0 关闭。现价相对净值的溢价率高于此值时禁止买入——跨境 ETF（纳指/标普/日经）溢价常达 8%~10%，高溢价买入等于先亏一笔溢价；红利 ETF 溢价常年接近 0，可关闭。建议 3。" /></template>
        <el-input-number v-model="form.premiumBuyMaxPct" :precision="2" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="溢价率容忍滞后(天)" help="QDII 基金净值公布常滞后 1~2 天，超过这个天数就把溢价率视为过期、本次不拦截（并在建议里提示），避免用陈旧数据误拦。" /></template>
        <el-input-number v-model="form.premiumStaleDays" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="回测假设溢价率%" help="回测没有溢价率历史（平台只存最新一天）：留空＝回测跳过溢价闸门（结果会比实盘乐观）；填值＝回测按该固定溢价率判断，例如填 5 模拟高溢价环境。" /></template>
        <el-input-number v-model="form.backtestPremiumPct" :precision="2" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="PE 买入上限" help="填 0 关闭。跟踪指数 PE 高于此值时禁止买入（估值贵了少买）。仅对平台有 PE 数据的指数生效（中证/上证系列，如中证红利 000922）；纳指无 PE 数据源，此项不生效。" /></template>
        <el-input-number v-model="form.peBuyMax" :precision="2" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="PE 买入下限" help="填 0 关闭。跟踪指数 PE 低于此值时，买入格数按下面的倍数放大（越便宜买越多）。" /></template>
        <el-input-number v-model="form.peBuyMin" :precision="2" :min="0" :controls="false" />
      </el-form-item>
      <el-form-item>
        <template #label><ParamLabel text="低估买入倍数" help="PE 低于买入下限时的买入格数倍数（1 = 不加倍），例如填 2 表示低估时买双倍格数。" /></template>
        <el-input-number v-model="form.peBoostMultiplier" :precision="1" :min="1" :controls="false" />
      </el-form-item>
      <div class="osc-summary">
        <div class="osc-title">网格逻辑速览</div>
        <ul>
          <li><b>格线固定</b>：由下沿/上沿/格数/模式一次算出，不随成交漂移；实盘与回测同一套口径，回测调好的参数可直接套实盘。</li>
          <li><b>格位锚点</b>：以<b>最近一次实际成交价</b>为基准（没有成交记录时用上一根收盘），跨几格就买卖几格；信号没照做（无交易流水）次日会重新提示。</li>
          <li><b>一天跨多格</b>：跳空跌穿 2 格就买 2 格份额（受「单根最多成交格数」与满仓上限夹取）。</li>
          <li><b>买入闸门</b>：趋势均线 → 溢价率 → 估值，任一拦截即不出买入建议；<b>卖出不受闸门限制</b>。</li>
          <li><b>收益来自差价</b>：每上穿一格卖出的份额，等于之前每下穿一格买入的份额，来回震荡反复兑现。</li>
          <li v-if="isPyramidType"><b>逐格阶梯</b>：{{ pyramidSummary }}——一天跨多格时按各格单位<b>逐格累加</b>；深跌/急涨会显著加大单次成交额，靠「满仓份额」兜底。</li>
        </ul>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, watch } from 'vue'
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

/** 网格族类型（共用同一套参数模板）：红利网格 / 纳指网格 / 金字塔网格 / 倒金字塔网格 */
const GRID_TYPES = ['DIV_GRID', 'NDX_GRID', 'PYRAMID_GRID', 'INV_PYRAMID_GRID']

/** 金字塔族（比普通网格多一个「每格增减」参数） */
const PYRAMID_TYPES = ['PYRAMID_GRID', 'INV_PYRAMID_GRID']

/** 当前类型是否属于网格族 */
const isGridType = computed(() => GRID_TYPES.includes(props.type))

/** 当前类型是否金字塔族 */
const isPyramidType = computed(() => PYRAMID_TYPES.includes(props.type))

/** 震荡向上分档说明（表单内展示档位与份额变化，与后端同一口径） */
const sizingSummary = computed(() => {
  const step = Number(form.sizingStepPct ?? 0)
  const buy = Number(form.buyShare ?? 0)
  const sell = Number(form.sellShare ?? 0)
  const limit = Number(form.fallAddPct ?? 0)
  if (!step) return '已关闭（固定份额）'
  const mode = step > 0 ? '越跌买越多、越涨卖越多' : '越跌买越少、越涨卖越少'
  const seq = [1, 2, 3].map((n) => {
    const m = Math.min(1 + (n - 1) * (step / 100), Number(form.maxSizingMultiple ?? 3))
    return `${n}档×${Number(m.toFixed(2))}`
  })
  return `${mode}：第 ${seq.join('、')} 档，即跌 ${limit}% 买 ${buy} 份 → 跌 ${limit * 2}% 买 ${Math.round(buy * (1 + step / 100))} 份（卖出同理镜像，卖出份额基准 ${sell} 份）`
})

/**
 * 阶梯说明文案：与后端同一口径（单位按格位线性取值）——
 * 买入：单位 = 每格份额 + 每格增减 ×（格数 − 格位）；卖出镜像。故最小 = 每格份额+增减、最大 = 每格份额+增减×格数。
 */
const pyramidSummary = computed(() => {
  const base = Number(form.sharePerGrid ?? 0)
  const step = Number(form.pyramidStep ?? 0)
  const grids = Number(form.grids ?? 0)
  if (!base || !grids) return '请先填写每格份额与格数'
  if (!step) return `每格等量 ${base} 份`
  const min = Math.max(base + step, 0.01)
  const max = Math.max(base + step * grids, 0.01)
  const trend = step > 0 ? '越低买越多、越高卖越多' : '越低买越少、越高卖越少'
  return `${trend}：每格 ${step > 0 ? '+' : ''}${step} 份，从 ${min} 份线性变化到 ${max} 份`
})

const defaults: Record<string, Record<string, unknown>> = {
  // 震荡向上（V5.13 重构）：固定份额 + 区间极值触发
  OSC_UP: {
    initialShare: 10000,
    baseShare: 5000,
    fullShare: 20000,
    windowDays: 60,
    riseReducePct: 5,
    fallAddPct: 5,
    buyShare: 5000,
    sellShare: 5000,
    // 分档功能（V5.29）：默认关闭（0），需要时填正/负数开金字塔/倒金字塔式加减
    sizingStepPct: 0,
    sizingBase: 'anchor',
    maxSizingMultiple: 3
  },
  // 均线突破（V5.73，V5.75 方向可配）：突破/跌破方向一买一卖恒相反
  MA_BREAK: {
    initialShare: 10000,
    baseShare: 5000,
    fullShare: 20000,
    breakoutMaDays: 60,
    breakdownMaDays: 30,
    breakoutAction: 'BUY',
    breakdownAction: 'SELL',
    cooldownDays: 5
  },
  // 红利网格（V5.27）：低波动慢牛（实测 515080 年化波动 13.7%、区间 1.395~1.637）→ 等差、约 1.4% 一格、
  // 底仓占比高、涨破上沿只保留底仓不上移、默认不开趋势闸门；红利 ETF 溢价常年≈0 故溢价闸门关闭
  DIV_GRID: {
    mode: 'arithmetic',
    lower: 1.4,
    upper: 1.62,
    grids: 11,
    perGridMode: 'share',
    sharePerGrid: 1000,
    amountPerGrid: 1500,
    baseShare: 5000,
    fullShare: 30000,
    initialShare: 15000,
    breakoutMode: 'hold',
    breakdownMode: 'hold',
    maxGridsPerBar: 0,
    trendMaDays: 0,
    premiumBuyMaxPct: 0,
    premiumStaleDays: 3,
    peBuyMax: 0,
    peBuyMin: 0,
    peBoostMultiplier: 2
  },
  // 纳指网格（V5.27）：高波动强趋势跨境（实测 513300 年化波动 23.3%、区间 2.054~2.865、当前溢价 +8.93%）→
  // 等比、每格约 3.4%、底仓占比低、涨破上沿区间上移、MA60 趋势闸门 + 溢价 3% 买入上限
  NDX_GRID: {
    mode: 'geometric',
    lower: 2.0,
    upper: 2.8,
    grids: 10,
    perGridMode: 'share',
    sharePerGrid: 500,
    amountPerGrid: 1200,
    baseShare: 1000,
    fullShare: 12000,
    initialShare: 3000,
    breakoutMode: 'shift',
    breakdownMode: 'hold',
    maxGridsPerBar: 3,
    trendMaDays: 60,
    premiumBuyMaxPct: 3,
    premiumStaleDays: 3,
    peBuyMax: 0,
    peBuyMin: 0,
    peBoostMultiplier: 1
  },
  // 金字塔网格（V5.28）：越跌买越多——首格 500 份、每深一格 +250 份（500/750/1000…），底部重仓摊成本；
  // 取向同红利：等差密格 + 涨破上沿保留底仓 + 关闭趋势/溢价闸门
  PYRAMID_GRID: {
    mode: 'arithmetic',
    lower: 1.4,
    upper: 1.62,
    grids: 11,
    perGridMode: 'share',
    sharePerGrid: 500,
    amountPerGrid: 800,
    pyramidStep: 250,
    baseShare: 5000,
    fullShare: 30000,
    initialShare: 8000,
    breakoutMode: 'hold',
    breakdownMode: 'hold',
    maxGridsPerBar: 0,
    trendMaDays: 0,
    premiumBuyMaxPct: 0,
    premiumStaleDays: 3,
    peBuyMax: 0,
    peBuyMin: 0,
    peBoostMultiplier: 2
  },
  // 倒金字塔网格（V5.28）：越跌买越少——首格 1500 份、每深一格 −100 份（1500/1400/1300…，最深处 600 份不归零）；
  // 取向同纳指：等比宽格 + 涨破上沿区间上移 + MA60 与溢价 3% 闸门
  INV_PYRAMID_GRID: {
    mode: 'geometric',
    lower: 2.0,
    upper: 2.8,
    grids: 10,
    perGridMode: 'share',
    sharePerGrid: 1500,
    amountPerGrid: 3000,
    pyramidStep: -100,
    baseShare: 1000,
    fullShare: 12000,
    initialShare: 3000,
    breakoutMode: 'shift',
    breakdownMode: 'hold',
    maxGridsPerBar: 3,
    trendMaDays: 60,
    premiumBuyMaxPct: 3,
    premiumStaleDays: 3,
    peBuyMax: 0,
    peBuyMin: 0,
    peBoostMultiplier: 1
  }
}

const form = reactive<Record<string, unknown>>({ ...(defaults[props.type] ?? {}), ...props.modelValue })

// 挂载即回传默认参数（V5.77）：父组件的回测表单需要完整参数（如满仓份额）来联动计算初始资金，
// 若等用户手改参数才回传，父组件会一直拿到空 params
onMounted(() => {
  emit('update:modelValue', { ...form })
})

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

/** 突破/跌破方向恒相反（V5.75）：改一边自动翻转另一边，保证一买一卖 */
function onBreakoutActionChange(value: string) {
  form.breakdownAction = value === 'BUY' ? 'SELL' : 'BUY'
}
</script>

<style scoped>
/* 参数网格：列数按容器宽度自适应。列宽下限 200px（数字/日期类短控件足够）——
   一行可排 5~6 个参数（回测宽卡 6~7 列、策略弹窗 4 列、窄屏 1 列），避免大面积留白 */
.param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  column-gap: var(--q-space-4);
  align-items: start;
}

/* 标签置顶（V5.78）：每格「标签一行 + 控件一行」——标签长短不再挤占控件空间，
   同列控件左边缘与宽度完全一致，解决「标签长短不齐 → 输入框有长有短」的参差感 */
.param-grid :deep(.el-form-item) {
  display: block;
  margin-right: 0;
}

.param-grid :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  justify-content: flex-start;
  margin-bottom: var(--q-space-1);
}

/* 数字/单选/下拉等控件铺满所在格子，同一列内严格等宽 */
.param-grid :deep(.el-input-number),
.param-grid :deep(.el-select) {
  width: 100%;
}

/* 铺满后的数字框内容左对齐（居中在大宽框里会显得找不着锚点） */
.param-grid :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

/* 单选按钮组同样铺满格子并均分（如 买入｜卖出），与输入框同宽保持整列节奏一致 */
.param-grid :deep(.el-radio-group) {
  display: flex;
  width: 100%;
}

.param-grid :deep(.el-radio-button) {
  flex: 1;
}

.param-grid :deep(.el-radio-button__inner) {
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
