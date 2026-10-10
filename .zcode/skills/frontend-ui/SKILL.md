---
name: frontend-ui
description: 本项目（策略数据研究平台）前端 UI 开发与样式改造的强制规范。当需要新增/修改 web/ 下的任何 Vue 页面、组件、样式，或调整配色、间距、图表、表格、卡片外观时，必须先读本规范并按其执行。包含设计 token 体系、颜色使用禁令、组件模式、图表调色板、检查脚本用法。触发词：前端、样式、UI、配色、Vue 组件、页面美化、ECharts 配置、Element Plus 覆盖。
---

# 前端 UI 规范（数据驾驶舱风格 · V2.0）

本项目前端是**数据密集型投资仪表盘**，视觉目标是「信息密度高但不拥挤、数字是一等公民、涨跌一眼可辨」。
所有前端代码（新增或修改）**必须**遵守本规范；规范由 `npm run check:style` 机械校验，违规即构建失败。

## 1. 铁律（违反即不合规）

1. **禁止在任何 `.vue` / `.css` / `.ts` 里写死色值**（十六进制、`rgb()`、`rgba()`）。
   - CSS 用 `var(--q-*)`；JS/图表用 `@/utils/palette` 导出的常量。
   - 唯一例外（白名单，仅这三个文件可含字面量色值）：`src/styles/tokens.css`、`src/styles/element-override.css`、`src/utils/palette.ts`。
2. **涨跌只用语义色** `--q-color-up`（红，涨）/ `--q-color-down`（绿，跌）/ `--q-color-flat`（零）。
   - A 股习惯：**红涨绿跌**，不要反过来，也不要借用 Element Plus 的 `success`/`danger` 表达涨跌。
   - JS 侧统一用 `changeColor(value)`（`utils/palette`）。
   - **反向也要守**：红/绿只表达涨跌，不得用来表达"成功/完成/通过"等无关语义（完成态用中性灰或品牌蓝）。
3. **Element Plus 组件外观一律通过 `styles/element-override.css` 统一覆盖**，不要在页面里逐个 `:deep()` 改内置组件的基础外观（如按钮圆角、表格行高）。页面级 `:deep()` 只允许用于**该页面特有的布局微调**。
4. **间距与字号只用 token**：间距用 `--q-space-1..8`（4/8/12/16/20/24/32），字号用 `--q-font-xs..3xl`。禁止写 `margin: 13px`、`font-size: 15px` 这类游离值。
5. **圆角只用** `--q-radius-sm|md|lg`（6/10/14）；**阴影只用** `--q-shadow-card|hover|float|primary`。
6. **数值必须等宽**：所有展示金额/价格/百分比/份额的元素加 `class="num"`（或 `font-variant-numeric: tabular-nums`），避免刷新时数字宽度跳动。
7. 提交前必须全绿：`npm run check:style` + `npm run type-check` + `npm run build`。

## 2. 设计 token（唯一来源：`src/styles/tokens.css`）

| 类别 | token | 值/用途 |
| --- | --- | --- |
| 品牌色 | `--q-color-primary` / `-hover` / `-active` / `-soft` / `-border` | #2563eb 靛蓝；soft 用于选中底、border 用于描边 |
| 涨跌 | `--q-color-up` `--q-color-down` `--q-color-flat`（另有 `-soft` 底） | #e5484d / #12a150 / #6b7280 |
| 文字 | `--q-text-primary` `-regular` `-secondary` `-muted` `-inverse` | 一级/常规/次要/占位/深底反白 |
| 背景 | `--q-bg-page` `--q-bg-page-gradient` `--q-bg-card` `--q-bg-subtle` `--q-bg-hover` | 页面/内容区渐变/卡片/浅底/悬停 |
| 主卡（渐变底） | `--q-bg-hero-card` `--q-text-on-hero` `--q-text-on-hero-sub` | KPI 主卡品牌蓝渐变、其上的反白文字（V2.1 新增，仅用于 `.stat-card--hero`） |
| 边框 | `--q-border` `--q-border-light` | 常规描边 / 更浅分隔线 |
| 侧栏 | `--q-sidebar-bg` `-bg-deep` `-text` `-text-active` `-hover-bg` `-active-bg` | 深色导航 |
| 圆角 | `--q-radius-sm/md/lg` | 6 / 10 / 14 |
| 间距 | `--q-space-1..8` | 4 / 8 / 12 / 16 / 20 / 24 / 32 |
| 字号 | `--q-font-xs/sm/base/lg/xl/2xl/3xl` | 12 / 13 / 14 / 16 / 20 / 24 / 28 |
| 阴影 | `--q-shadow-card/hover/float/primary` | 卡片 / 悬停抬升 / 浮层 / 品牌光晕 |
| 布局 | `--q-layout-sidebar-width` `-header-height` `-page-padding` | 200px / 56px / 16px |

## 3. 组件模式（照抄，不要自由发挥）

### 3.1 KPI 卡（资产/盈亏类数字）

**V2.1 起分两级：主卡 1 张 + 副卡若干张（仪表盘为 6 张，栅格 6 + 6×3 = 24）**，不再是一排等宽同质卡。

- **主卡 `.stat-card--hero`（承载"总资产"这类唯一主角）**：品牌渐变底 `--q-bg-hero-card` + 品牌光晕 `--q-shadow-primary`；文字用 `--q-text-on-hero`（主值）/ `--q-text-on-hero-sub`（说明）；主值 `.hero-value` 用 `--q-font-3xl`；右上角放"转入/转出"这类主操作，按钮加 `.q-on-hero` 修饰类（深底上的反白链接按钮，样式在 `element-override.css`）；底部 `.hero-foot` 放"持仓 N 只 · 自选 M 只"这类摘要，用 `margin-top: auto` 贴底。
- **副卡 `.stat-card`**：白底保持；左侧 3px 语义色条由 `.stat-card--up/down/flat` 决定（**中性/零值退回 1px，不画灰杠**）；副卡数值 `.stat-value` 在 24px 与 16px 之间按视口自适应（`clamp(var(--q-font-lg), 1.5vw, var(--q-font-2xl))`，窄屏 119px 卡宽下不溢出）；涨跌数值加 `.stat-value--up/down` 浅色底色块（红涨绿跌浅底，中性不加底）。
- 结构：`.stat-card` → `.stat-label`（13px 次要色，**不带单位**，单位由主卡「总资产（元）」交代）→ `.stat-value`（等宽）→ `.stat-sub`（12px 弱化说明）→ `.stat-spark`（30px 迷你走势槽位，`margin-top: auto` 贴底）。
- 迷你走势用 `<SparkLine>`，颜色传 `changeColor(该卡数值)`；四张走势卡都无数据时整行不渲染槽位（`showSparkRow`），避免底部 38px 空白。
- 一行内所有卡片等高：靠 `.stat-row :deep(.el-col){display:flex}` + `.stat-card{flex:1}`，**不要写死 min-height**。
- 颜色：数值用 `.text-up` / `.text-down` / `.text-flat`（涨跌类）或 `--q-text-primary`（中性金额）。
- 卡片内边距：`var(--q-space-3) var(--q-space-4)`；SPI 卡内容固定，卡片体加 `overflow: hidden`（EP 卡片体默认 `overflow: auto`，子元素轻微溢出就会出现内部滚动条）。

### 3.2 图表容器
- 一律用 `<ChartPanel :option="..." height="340px" />`，外层套 `el-card shadow="never"`，卡片头用 `.card-header`（左标题 + 右操作）。
- **框选交互**：需要"拖一段看这段"时给 `ChartPanel` 传 `:brush="true"` 并监听 `@brush-end`，不要在页面里自己配 toolbox/brush（组件已注入且默认激活拖动即框选）；回调拿到的是**类目轴数据索引**，用当前序列的日期轴换算成业务日期，回填筛选器后重新加载，保证"筛选器状态 == 图形状态"。
- **按区间加载但保留指标**：指标类图表（MA/BOLL/MACD）按自定义区间取数时，必须**往前多取预热段**（本项目取 150 自然日），在完整序列上算指标、再用 `dataZoom` 把显示窗口收敛到选区——只取选区会让窗口开头的指标失真或空缺。
- ⚠️ **图表放在 `el-tab-pane` / 折叠面板等会被隐藏的容器里时，必须保证创建后会重算尺寸**：`ChartPanel` 已内置 `ResizeObserver`（容器尺寸变化即 `resize()`），因此不要在隐藏容器里绕过 `ChartPanel` 直接 `echarts.init`——隐藏态创建时容器宽度为 0，ECharts 会退化成默认 100px 宽且不会自愈（曾导致基金详情"指数估值"图宽度只有 100px）。
- ECharts 配置：颜色**全部**来自 `@/utils/palette`；`grid` 用百分比（如 `left: '8%'`）；`symbol: 'none'`；`dataZoom` 默认带 `inside`。
- 双轴/多线：主序列 `PRIMARY`，基准 `BENCHMARK`，回撤 `UP`，PE `PE_LINE`，成交量 `VOLUME`。

### 3.3 数据表
- 用 `el-table` + `size="small"`，**不要**再加 `border`（细线由全局覆盖提供）；数字列 `align="right"` 并加 `class="num"`。
- **列宽分布要均匀（用户反复反馈的高频问题，务必遵守）**：
  - Element Plus 只会把**剩余宽度分给弹性列（`min-width`）**，且是**在弹性列之间均分**。因此：
    - 若只有**一个**弹性列，该列会被撑到内容的数倍宽（实测"备注"298px、"基金"占满全部余量）——**这是错误示范**；
    - 若**全是固定宽度**，EP 不会自动铺满，表格右侧留一大块空白（实测 148px）——也不可取。
  - **正确做法**：把需要参与分摊的列都写成 `min-width`（值取"内容刚好放得下"的宽度），让剩余宽度**在多列之间均分**；最后一列如果内容很窄（如"操作"），也给 `min-width` 并 `align="right"`，不要让它成为唯一的兜底列。
  - 判据（提交前自检）：**任何一列的渲染宽度都不应超过其内容所需宽度的约 1.5 倍**；同一张表里最宽列与最窄列的比值不要拉开到 3 倍以上（"名称/基金"列可以略宽，但不能独大）。
- **「操作」列的宽度必须按按钮数给足，不许卡在临界值上（V5.93 用户反馈后实测定型，用户点名"表格样式排版你犯过很多错误"，务必按公式算）**：
  - 单元格里按钮是**行内元素**，放不下就**折行**——三个按钮变两行、单元格变高，观感很差（用户原话"换行了很不好看"）。
  - **实测尺寸（small 按钮）**：普通/plain 按钮 2 个字宽 **50px**（4 个字约 74px）；`link` 按钮窄得多，2 个字约 **26px**、4 个字约 52px；相邻按钮有 `margin-left: 12px`；单元格自身要吃掉 **内边距 16px + 边框 1px = 17px**（`td` 宽 190 时内容盒只有 173px）。
  - **公式**：`列宽 width ≥ Σ按钮宽 + (n−1)×12 + 17 + 余量(≈10)`。例：3 个普通 small 按钮 = 50×3 + 12×2 + 17 = 191 → **最少 204px**。
  - **反面实例（本项目的真实事故）**：回测记录列表 3 个按钮（应用/结果/删除），先写 148px（三个按钮直接两行）、改 190px 仍折行——190 − 17 = 173 内容盒，按钮需求恰为 174，**就差 1 像素**；最终定 **204px**。所以别算到刚好，一律留 10px 以上余量。
  - `link` 按钮的小操作列（如「编辑｜重置密码｜删除」三个 link）实测 190px 绰绰有余，**不必**按普通按钮公式放大。
- **状态标识并进名称列**：像「持仓」这类行状态用小 `el-tag` 跟在名称后面，不要单独占一列——列数一多就得横向滚动，反而把关键列挤出视野；列顺序按重要性从左到右（代码/名称/核心业务字段…操作列 `fixed="right"`）。
- **需要"置顶某类行"时在服务端分段取数**（如"持仓优先"= 持仓段一次性取出 + 其余段按偏移量取），不要依赖数据库方言的布尔排序（`FIELD()`/`CASE` 之类），否则换库即坏、也难审查。
- 派生字段（合计、比率）由后端算好放进 VO，前端只做展示与悬浮明细，避免同一算法在前后端各写一份。
- 表头文案用 2-4 个字（如「市值」「占比」「当日盈亏」），列宽固定数值列、名称列 `min-width` + `show-overflow-tooltip`。
- **长文本列（信号理由/说明/备注/失败原因）必须给 `show-overflow-tooltip`**：单元格里省略号截断、悬浮看全文；悬浮框的限宽换行由全局规则兜底（见 3.5），**不要为某一列单独写 popper 样式**。

### 3.2.0 尺寸校正与后台标签页 ⚠️
- **后台标签页会冻结 `requestAnimationFrame` 与 `ResizeObserver`**（实测：标签在后台时容器高度变了、画布不跟）。凡是靠它们做尺寸校正的代码（图表 resize、进入动画等）都不可靠，**必须有定时器或普通事件兜底**。
- 本项目做法：`ChartPanel` 以"`height` 变化 → `setTimeout` 触发 `resize()`"为确定性路径，`ResizeObserver` 只作为宽度/隐藏 Tab 场景的补充。

### 3.2.1 图表上的事件标记（交易点等）
- 标记数据**一次性取回并缓存**（按主键缓存，如每只基金一次），严禁混进主数据请求或在缩放/框选时重复请求；接口只返回"日期 + 类型 + 一句话文案"。
- 标记渲染成独立 `scatter` 系列，**交给 ECharts 按可见窗口自动裁剪**，前端不要再算一遍可见集合；同一日同类型在后端或前端合并。
- 标记**不要进 tooltip**（`series.tooltip.show = false`）：避免用"日期对应的价格"污染原有悬停信息，识别靠字母气泡 + 图上的文字说明（如「标记：b 买入 / s 卖出 / q 分红除息」）。
- 颜色从 `@/utils/palette` 取（买=涨色、卖=跌色、事件=品牌蓝），字母颜色用 `TEXT_INVERSE`，不要在页面里写 `#ffffff`（`check:style` 会拦）。

### 3.2.2 可收起/展开的卡片（`CollapseCard`，V6.18）
- 长页面的模块卡（回测结果页的概况/资金曲线/回撤曲线/交易明细）用 `@/components/common/CollapseCard.vue` 包裹：**默认展开**，标题条整条可点（含键盘 Enter/空格）收起/展开，收起后只留标题条，减少滚动距离。
- 用法：`<CollapseCard title="交易明细" class="block"><el-table … /></CollapseCard>`——卡片本体是 `el-card`，`class`/样式照常透传；标题由 `title` prop 给，**不要**再在内容里写一份同名小标题（会重复）。
- **收起必须用 `v-show`（组件内部已如此）而不是 `v-if`**：卡内图表/表格的 DOM 与 ECharts 实例要保留，重新展开时 `ChartPanel` 内置的 ResizeObserver 会按恢复后的尺寸自动 resize；用 `v-if` 会重建画布并丢 dataZoom 等交互状态。
- 收起态左右箭头：展开为向下、收起为向右（`CaretBottom` 旋转 -90°），悬停变品牌蓝——这是"该点哪里"的唯一提示，别省。

### 3.3.1 指标/统计表的展示口径
- **"区间表现"这类统计表，口径 = 图上当前可见的那一段**（所见即所测）：页面把图表的可见窗口（`dataZoom` 的 start/end 百分比或框选索引）换算成日期区间与序列切片，再算涨跌幅/最大回撤/年化波动率，缩放或框选后自动重算，不再请求接口。指标函数放 `utils/metrics.ts` 共用，前后两处各写一份必然走散。
- `ChartPanel` 已上报 `zoom-change`（用户缩放/拖动 dataZoom）与 `brush-end`（框选），页面直接监听即可。

### 3.3.2 响应式栅格（`el-col`）⚠️ 易踩
- EP 的栅格是**靠断点 CSS 类 + 媒体查询**生效的，`sm/md/lg/xl` 的媒体查询在基础 `el-col-{n}` 之后 → **断点类会覆盖基础 `span`**。
- 因此"一行 6 个"不能只写 `:span="4" :sm="8"`：所有 ≥768px 的屏幕都会按 `sm=8`（3 个一行）渲染，窄屏之外根本没有 4 栅格的效果（本项目指数看板曾因此换行）。
- 正确写法：**把目标断点显式写全**，例如 `:span="4" :xs="12" :sm="8" :md="4"`（手机 2 个/平板 3 个/桌面 6 个）；只写 `xs`（其媒体查询是 `max-width`）时基础 `span` 在桌面仍有效。
- 自检：改完栅格后用浏览器量一次"每行几个"（按卡片 `getBoundingClientRect().top` 分组计数），不要凭肉眼在某个宽度下看一眼就算过。

### 3.4 空态与提示
- `el-empty :image-size="60"`，描述文案说明**为什么空**以及**下一步做什么**（如「暂无持仓流水，录入交易后展示收益曲线」）。
- 降级提示用 `el-alert type="info" :closable="false"`。
- **成排空态要用引导卡替代**（V2.1）：当页面会同时出现 3 个以上空卡时（如未开始记账的仪表盘），改为渲染一张 `.guide-card`「开始使用」三步上手卡（`.guide-steps` / `.guide-step` / `.guide-no` / `.guide-text`），每步 = 序号圆点 + 标题 + 说明 + 一个直达按钮；步骤完成态由真实数据推导（`done`），完成态用**中性灰**（`--q-bg-hover` + `--q-text-muted`）而不是绿色——绿色在本站是「下跌」语义，不得借来表达"完成"。

### 3.4.1 密钥类字段的回显约定
- **多厂商场景**：Token 是按厂商签发的，**切换厂商时必须清空 Token 输入框**，并在 placeholder 上区分"与已保存配置同厂商（留空不改）"和"跨厂商（必须重新填写）"；后端也**不允许跨厂商沿用已保存的 Key**，否则就是拿 A 家的 Key 请求 B 家端点，只会得到 401。
- 后端**只回传打码值**（如 `****dmLQ`）与 `hasKey` 标记，前端输入框用打码值做 placeholder（"已配置（****dmLQ），留空表示不修改"），**绝不在页面上回显明文**。
- 保存时前端把空串/打码值原样提交，由后端判断"是否修改 Token"；不要在前端做"填了才算改"的猜测。

### 3.5 表单与对话框
- 表单 `label-width="90px"`，`el-input-number :controls="false"`；金额/份额 2 位小数、价格 4 位小数。
- 对话框宽度：简单表单 480-520px；需要输入说明时用 `.field-hint`（12px 弱化）解释口径。
- **派生只读字段模式**：当某字段可由其他字段算出时（如成交价 = 成交金额 ÷ 成交数量），该字段用 `el-input :model-value="..." readonly` + `.auto-field`（浅底 + 虚线边）呈现，并在下方 `.field-hint` 写明算式与小数位，**不要**让用户手填（两处都能改就会出现三个数对不上的情况）。
- **录入页与更正页分离**：新增走统一录入组件（口径一致），更正历史数据用可全字段手改的独立表单——历史数据可能本就不满足新口径（如金额 ≠ 价×量），套用派生规则会把数据改坏。
- **多字段参数表单用「自适应网格」，不要竖向一列到底**：一个表单里有 6~8 个短字段（策略参数、回测配置这类）时，逐项竖排会在宽容器里"每个字段独占一整行、输入框固定 150px、右侧大片空白"，又长又空。做法：外层 `display: grid; grid-template-columns: repeat(auto-fill, minmax(下限, 1fr))`（列数随**容器**宽度自适应，比 `el-col` 的视口断点更合适，因为同一个组件会同时出现在宽卡片与窄弹窗里），**列宽下限按内容取**：数字/日期等短控件取 `200px`（一行 5~6 个——V5.79 用户拍板的密度，340px 时一行只有 3~4 个会显得空旷）、标签或说明很长的取 `340px`；控件 `width: 100%` 铺满格子，跨行的说明块（`el-alert`/提示）加 `grid-column: 1 / -1`。实测收益：回测区 6 个参数 292px 高 → 86px（6 行 → 4+2 两行）、弹窗 7 个参数 → 2+2+2+1 四行。
- **网格单元一律「标签置顶」，不要标签与控件左右排**（V5.78 用户反馈：左右排时标签文字长短不齐——同列里"初始仓位份额"6 字挤、"底仓份额"4 字松，控件左边缘参差、宽度有长有短，整片看着乱）。做法：`:deep(.el-form-item){display:block}` + `:deep(.el-form-item__label){display:flex;width:100%;height:auto;justify-content:flex-start;margin-bottom:var(--q-space-1)}`；铺满后的数字框内容改左对齐（居中在大宽框里没锚点）；单选按钮组同样铺满并均分（`:deep(.el-radio-group){display:flex;width:100%}` + `:deep(.el-radio-button){flex:1}`），与输入框同宽保持整列节奏。同一卡片里的**筛选字段与参数网格用同一套栅格**（相同 minmax 与 gap），两区块列列对齐。
- **筛选工具条里的控件要"给宽度、不伸缩"（V6.24 实测踩坑）**：EP 的**日期区间选择器**（`el-date-picker type="daterange"`）根节点自带 `el-input__wrapper` 类，而 wrapper 有 `flex-grow: 1`——放进 `display:flex` 的筛选工具条后它会被撑到"内容最大宽"：页面声明 `width: 250px`、**实际渲染 307px**（两侧日期输入框各被拉到 120px），看起来"莫名其妙很宽"。已在 `element-override.css` 统一收回伸缩权（`.el-date-editor.el-range-editor.el-input__wrapper { flex: none }`），页面照常声明宽度即可；**daterange 的最小可用宽度约 220px**（两个 10 位日期各 75.6px + 分隔符 25px + 日历图标 14px + 内边距 20px ≈ 224px），再窄日期会被截断——本项在基金对比页按 220px 定型。
- **字段说明用问号 + 限宽浮层**：参数含义长（一句话 50~80 字）时，挂在标签后的问号上。**浮层限宽换行已由全局兜底（V6.19）**——`element-override.css` 里有 `.el-popper.el-tooltip:not(.is-pure):not([class*='__popper']) { max-width: 420px; white-space: normal; word-break: break-word; line-height: 1.6 }`，覆盖 `el-tooltip`（含问号帮助）、表格 `show-overflow-tooltip`、以及任何 `content` 属性和插槽写法；**新增提示不要自己写换行样式**。只有需要**特殊宽度/排版**的浮层才用 `popper-class` + 单独一段**非 scoped**样式（浮层被 teleport 到 `body`，scoped 命中不到；类名带前缀）——既有特例：`.param-help-text` 316px、`.profit-calendar-help-popper` 336px。
  - ⚠️ **选择器必须排除下拉/选择器面板**：EP 的 `select` / `dropdown` **内部就是用 tooltip 渲染的**，其浮层同时带 `el-tooltip` 与 `is-pure` / `el-select__popper` 等类——只写 `.el-popper.el-tooltip` 会把宽触发器的下拉也压到 420px（V6.19 实测踩到）。判据：提示气泡不带 `is-pure`、也不带 `*__popper` 面板类；也**不要**写裸 `.el-popper`（那是所有浮层的基类）。
  - 反面实例（V6.19 实测）：不加限宽时，63 字的「信号理由」浮层 619px、80 字的口径说明 **1107px**，全是 32px 高的单行、横跨整个视口——用户反馈"全部在一行，很难看"。

### 3.6.0 页面宽度（避免宽屏留白）
- 设置类页面给 `max-width` 时**必须同时 `margin: 0 auto`**：否则在宽屏上左对齐、右侧空出一大块，观感就是"缺了一块"。
- `max-width` 取"略小于常见内容区宽度"（本项目 1400px，内容区约 1500px）——既几乎铺满，又不会让表单输入框拉成超长条。

### 3.6.1 设置页/后台页排版（卡片网格）
- 结构：**同一行放"同类型、体量接近"的卡片并让它们等高**（给卡片加 `height: 100%`），**体量差异大或需要横向空间的卡（表单字段多、地址长）单独通栏**——不要把所有卡硬塞进两列，否则一列高、一列空，页面就是"丑"的直接来源。
- 卡片内部的表单：字段多时用 `el-row/el-col` 排成**两列**（每 `el-col` 内放一个 `el-form-item`），比单列长表单更耐看也更省纵向空间。
- 纵向节奏由**页面根容器的 `gap` 统一给**（`display: flex; flex-direction: column; gap: var(--q-space-3)`），不要在每张卡上各加 `margin-top`。
- 卡片标题一律用统一的 `.card-header`（品牌色 3px 竖条 + 右侧次要信息/状态标签），同一页不要出现两种标题样式。

### 3.7 列表分页（V2.2）
- 列表页一律**服务端分页**：`el-pagination`（`total/sizes/prev/pager/next/jumper`、`background`）放在表格下方右对齐，每页条数 10/20/50/100。
- **分页与筛选必须配套**：前端筛选只能筛到当前页，凡加分页就要把关键词/标签等筛选条件一并移到服务端（如 `/funds/watchlist/page`）。
- 翻页要保持行勾选时，表格加 `row-key` 且勾选列加 `reserve-selection`，勾选上限判断要按**业务主键**（如基金代码）而不是行对象。
- 删除末页最后一条后要回退一页重查，否则会停在空页。

### 3.6 页面骨架
- **浮动组件的拖拽**：面板类浮窗（如 AI 助手）给标题栏绑定 `mousedown` 拖动；**折叠态的那个圆形入口也必须能拖**（只给面板标题栏绑拖拽，用户拖球没反应会被当成"不能拖"）。拖动与点击要按位移阈值区分（本项 < 5px 视为点击），并把位置写入共享状态，让面板展开时贴着球所在角落；默认位置固定在**右下角**（边距 24px）。
- **AI 助手浮球的外观（V6.20 起，V6.21 改人像）**：球体 = `--q-ai-ball-bg`（左上高光的品牌蓝径向渐变）+ `--q-ai-ball-inner-ring` 内侧 1px 亮边做出玻璃球质感；`::before` 叠一层 `--q-ai-ball-sheen` 高光，`::after` 是 2.6s 向外扩散的**呼吸光环**（`--q-ai-ball-halo` → `--q-ai-ball-halo-fade`，纯 `box-shadow`、不占布局、`pointer-events:none` 所以不影响拖拽与点击）；悬停放大 1.06 + 加深光晕。
- **AI 助手头像与面板（V6.21，标记与色值都是单一来源；V6.22 改圆脸表情）**：头像 `components/ai/AiAvatar.vue` 走"**球体即脸、只画表情**"的画法——两只椭圆眼 + 微笑弧 + 两片低透明度腮红（`opacity: .45`，不抢眼睛）+ 右上角一枚小火花点出"AI 生成"语义；`size` / `animated` 两个 prop，**浮球、面板标题角标、助手消息角标三处共用同一枚**（不要一处自绘、一处用 EP 图标，也别照搬任何现成产品的标识形状）。动画三条：整枚 3.4s 上下起伏（呼吸）、眼睛 4.6s 眨动（`transform-box: fill-box` 按眼睛自身中心 `scaleY`）、火花 2.6s 明暗闪动，全部要在 `prefers-reduced-motion: reduce` 下关闭。面板质感的口径：标题栏用 `--q-ai-head-bg` 品牌渐变 + `--q-ai-head-badge` 半透明头像底座，工具栏按钮要有**当前状态高亮**（历史会话展开时 `is-on`）；首屏问候＝圆头像底座 + 标题 + 一句定位说明，建议问题用**药丸 chips**（可悬停可键盘聚焦，不要用裸文字链接）；消息气泡我方=品牌浅底+品牌描边、助手=白底+浅描边，靠近角标的一角收小；输入框聚焦给品牌柔光（`--q-color-primary-soft` 3px）；长列表用细滚动条。半透明色值只允许写在 `tokens.css` 的 `--q-ai-*` 一组里。
- 页面骨架见下方小节。**深色/玻璃底页面（登录页等）**：容器加 `.q-on-dark`，输入框/表单标签/卡片配色由 `element-override.css` 统一提供（`--q-login-*` 一组 token），不得在页面里逐个 `:deep()` 改内置组件；装饰图形（K 线剪影、网格、光晕）用内联 SVG + 固定数组生成，`fill/stroke` 取 token，且容器加 `aria-hidden="true"`。
- 装饰性图形的图案要**手工固定**（数组常量），不要随机生成，否则每次渲染形态都在变。

- 最外层 `class="page"`（纵向 flex，gap 12px）或 `class="dashboard"`（仪表盘专用，gap 12px）。
- 工具栏 `class="page-toolbar"`（两端对齐）。

## 4. 图表调色板（`src/utils/palette.ts`）

`UP` `DOWN` `FLAT`（涨跌）· `PRIMARY` `BENCHMARK` `PE_LINE` `VOLUME`（序列）· `AXIS_LINE` `AXIS_LABEL` `SPLIT_LINE`（坐标轴）· `TEXT_PRIMARY` `TEXT_SECONDARY`（图例）· `CANDLE_UP` `CANDLE_DOWN`（K 线）· `PIE_PALETTE`（饼图按序取色）· `COMPARE_COLORS`（多基金对比，5 色）· `MA_COLORS`（6 条均线）· `SIDEBAR_*`（el-menu props）· `changeColor(v)` 取涨跌色。

> **多序列"身份色"的选色口径（V6.22）**：一条线代表一个实体（基金/资产）而不是状态时，**必须避开 `UP`/`DOWN` 红绿**（红涨绿跌在本站是语义色，拿去当基金身份会被误读成涨跌），并且**色相要拉开**（蓝/琥珀/青绿/紫/玫红这样跨色相），相邻槽位一眼能分；颜色与槽位绑定（`slotColor(index)`），某条序列取数失败被跳过时**颜色不跟着挪位**，图和表（色点）用同一个来源。

> `palette.ts` 与 `tokens.css` 的色值必须保持镜像：改一处必须同步另一处（改色只改这两个文件）。

## 5. 检查与命令

| 命令 | 作用 |
| --- | --- |
| `npm run check:style` | **样式规范门禁**：扫描全量源码，发现白名单外的硬编码色值即失败并列出文件行号 |
| `npm run type-check` | vue-tsc 类型检查，必须 0 错误 |
| `npm run build` | 构建并把产物拷入后端 static（后端打包前必须执行） |
| `npm run lint` / `npm run format` | ESLint / Prettier |

### 3.7.1 跨组件实例的缓存必须放独立 `.ts` 模块 ⚠️ 易踩（V5.44 实测）

想在"切菜单离开页面、回来时不重新请求"（如列表 5 分钟缓存）时，缓存变量**不能写在页面 `<script setup>` 顶层**：

```ts
// ✗ 页面 <script setup> 顶层 —— 看着像模块级，其实每次组件实例化都重新执行 setup 函数体，缓存每次被重置
let cache: Row[] | null = null

// ✓ 放在独立 .ts 模块（如 src/api/xxx.ts）—— ES 模块在同一页面生命周期内只求值一次，才是真单例
//   src/api/marketSignals.ts
const CACHE_TTL_MS = 5 * 60 * 1000
let cache: { key: string; at: number; data: T } | null = null
export async function fetchCached(key: string, force = false): Promise<{ data: T; at: number }> { ... }
```

要点：① 缓存按查询参数（如 PE 窗口）分键，切换参数时各自复用；② 给一个 `force` 入口（页面放「刷新」按钮），否则缓存期内想立刻看新数据没辙；③ 界面上显示「数据时间」，让用户知道这批数据是几点取的；④ 浏览器 F5 会重建模块 = 视为主动要新数据，无需额外处理。

## 6. 常见反例（不要这样做）

```vue
<!-- ✗ 写死色值 -->
<div :style="{ color: value > 0 ? '#f56c6c' : '#67c23a' }">

<!-- ✓ 用调色板 + 工具类 -->
<span :class="changeColorClass(value)">{{ value }}</span>

<!-- ✗ 游离间距/字号 -->
.stat { margin-top: 6px; font-size: 22px }

<!-- ✓ token -->
.stat { margin-top: var(--q-space-2); font-size: var(--q-font-2xl) }

<!-- ✗ 借用 success/danger 表达涨跌 -->
<el-tag :type="delta > 0 ? 'danger' : 'success'">

<!-- ✓ 涨跌用语义色，标签语义用 EP 类型（如下单方向可继续用 danger/success） -->

<!-- ✗ 长文案浮层不管宽度：EP 会把它渲染成横跨屏幕的一整行（实测 1107px） -->
<el-tooltip content="很长的一段口径说明……"><el-icon><QuestionFilled /></el-icon></el-tooltip>
<!-- ✓ 全局已兜底（element-override.css 的 .el-popper.el-tooltip:not(.is-pure):not([class*='__popper'])，
        限宽 420px 并换行），直接用即可；只有需要特殊宽度/排版时才配 popper-class + 非 scoped 样式 -->

<!-- ✗ 用涨跌色表达"完成"（绿色=跌，语义冲突） -->
.guide-step--done .guide-no { background: var(--q-color-down-soft); color: var(--q-color-down); }

<!-- ✓ 完成态用中性灰 -->
.guide-step--done .guide-no { background: var(--q-bg-hover); color: var(--q-text-muted); }
```

## 7. 变更流程

1. 改配色/圆角/间距体系 → 只改 `tokens.css`（必要时同步 `palette.ts`）。
2. 改 Element Plus 组件外观 → 只改 `element-override.css`。
3. 新增页面 → 套用第 3 节的组件模式，不引入新的色值/间距字面量。
4. 每次改完跑 `check:style` + `type-check`；改完视觉后在后端模块执行 `sh build.sh`，再用浏览器核对实际渲染效果。
5. **引入新的组件模式（新的卡片形态、引导/空态形态、色块语义）时必须回写本规范**，否则下一个页面会各自发挥、体系再次走散。
