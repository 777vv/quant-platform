# 个人量化投资助手

> **该平台仅供个人学习和参考，不构成投资建议，投资决策与风险由本人承担。**

只给买卖建议、不做自动化交易、只覆盖指数基金（场内 ETF + 场外指数基金）的个人量化辅助平台。
单应用多模块（Spring Boot 4 / JDK 21）+ Vue3 前端打包嵌入 jar，单容器部署。

设计与验收文档见 `docs/`：需求（01）、技术设计（02）、任务清单与进度（03）、验收报告（04）、使用手册（05）。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，可自由使用、修改与分发；保留版权与许可声明即可。

## 技术栈

### 后端

| 分类 | 技术 | 版本 | 在本平台中的作用 |
| --- | --- | --- | --- |
| 语言与运行时 | JDK | 21 | 虚拟线程、文本块、record 等新特性 |
| 应用框架 | Spring Boot | 4.1.1 | Web（`spring-boot-starter-webmvc`）、配置、调度、SQL 初始化 |
| AI 接入 | Spring AI（OpenAI 兼容 starter） | 2.0.1 | 对话客户端、工具调用（7 类工具 + 次数限额）、会话记忆 Advisor |
| 鉴权 | Sa-Token | 1.46 | 登录会话（7 天）、注解式拦截、BCrypt、防爆破锁定 |
| ORM | MyBatis-Plus | 3.5.17 | 通用 Mapper、Lambda 查询、分页插件（boot4 starter） |
| 分布式协调 | Redisson | 4.7 | 定时任务分布式锁、会话记忆的 Redis 存储 |
| 邮件 | Spring Mail + Thymeleaf | Boot 内置 | 信号摘要 / 同步告警 / 测试邮件（异步 + 失败重试） |
| 数据校验 | Jakarta Validation | 3.x | 接口参数校验（如导入代码长度） |
| 观测 | SLF4J + Logback | Boot 内置 | app/error/eastmoney/ai-tool 四类日志文件、traceId 贯穿 |
| 构建 | Maven 多模块 | 3.8+ | common/system/fund/strategy/ai/web 六模块 + 前端资源打包 |
| 质量门禁 | PMD + p3c | PMD 6.55 / p3c 2.1.1 | `mvn verify` 阻断 Blocker/Critical 违规 |

### 前端

| 分类 | 技术 | 版本 | 在本平台中的作用 |
| --- | --- | --- | --- |
| 框架 | Vue | 3.5 | 组合式 API + `<script setup>` |
| 语言 | TypeScript | 5.x | 全量类型（接口 VO 与后端字段一一对应） |
| 构建 | Vite | 5 | 开发服务器（/api 代理 8080）与产物打包（拷入 jar 静态目录） |
| UI 组件 | Element Plus | 2.9 | 表格/表单/弹窗等，配 `element-override.css` 统一观感 |
| 状态 | Pinia | 2.x | 登录态等轻量状态 |
| 图表 | ECharts | 5.6 | K 线/净值/对比/收益曲线/用量趋势（统一 `ChartPanel` 封装） |
| HTTP | Axios | 1.7 | 统一响应体处理、satoken 头、401 跳登录 |
| Markdown | marked + DOMPurify | 最新 | AI 回答与【使用手册】渲染（先渲染后消毒） |
| 代码质量 | ESLint (flat) + Prettier + vue-tsc + check:style | — | 类型 0 错误、样式 token 门禁 |

### 存储与数据源

| 分类 | 技术 | 说明 |
| --- | --- | --- |
| 主库 | MySQL 8.4 | 19 张表（`schema.sql` 启动期幂等建表/迁移）；行情、净值、估值、流水、回测、AI 会话与用量都在本地 |
| 缓存 | Redis 7 | 会话记忆窗口、导入任务进度、看板快照标记 |
| 行情数据源 | 东方财富公开接口 | ETF 日 K、场外净值、基金档案与分红、全球指数（带限频、重试与分片域名容灾） |
| 估值数据源 | 中证指数官网 | 指数 PE 历史（`perf/index-perf`），覆盖中证/上证系列指数 |

## 模块结构

```
quant-common    可拔插公共模块（响应体 R/异常/traceId/工具类/线程池/分布式锁）
quant-system    用户与登录鉴权（单用户，BCrypt，防爆破锁定）
quant-fund      基金池/数据导入/增量同步/持仓流水/收益统计/仪表盘聚合
quant-strategy  策略引擎（网格、估值百分位）/回测引擎/信号计算/邮件通知
quant-ai        AI 投资助手（Spring AI 2.x 经智谱 OpenAI 兼容端点 + 7 类工具）
quant-web       启动与打包（含前端静态资源托管）
web/            Vue3 前端源码
deploy/         部署辅助脚本（MySQL 每日备份）
docs/           需求 / 技术 / 任务清单三份文档
```

## 功能一览

| 模块　　　　　　 | 说明 |
| --- | --- |
| 仪表盘 | 指数看板按市场切换（A 股/港 股/美 股/亚 太/欧 洲，一次只看一个市场，桌面端一行 6 个不换行）；KPI 区为「品牌渐变总资产主卡 + 6 张副卡」（副卡带涨跌色条/数值底色块/迷你走势）；尚无持仓成交时以「开始使用」三步引导卡替代成排空态；17 个全球指数看板，按 A 股 / 港 股 / 美 股 / 亚 太 / 欧 洲 分组展示（含近 30 交易日迷你线）；**看板缓存优先**——页面只读库内快照（毫秒级），5 分钟任务负责写快照，右上角刷新按钮走 `POST /api/dashboard/indices/refresh` 强刷最新；另有 7 张资产卡片（总资产/当日/浮动/累计/本月/本年/近 7 日）、收益曲线（7 个时间维度 + 沪深300 基准）、速览区（最新信号、近 7 日收益柱图、持仓速览、涨跌榜、资产配置、同步状态） |
| 记账 | 页头「记一笔」与持仓行内「录流水」共用弹窗：买入/卖出/分红/资金转入/资金转出五种类型；**买入卖出只填成交金额与成交数量，成交价按 金额÷数量 自动派生（只读，4 位小数）**，分红按现金分红（税前）只填金额；**资金转出不可超过现金余额**（弹窗内显示可转出上限）；填写后即时预估交易后的持有份额、摊薄成本价与浮动/已实现盈亏 |
| 交易流水 | 独立菜单：全部流水可按基金/类型/日期区间筛选并分页查看（只读，更正与删除在基金详情页的流水 Tab 内完成） |
| 行情图表 | K 线/净值支持 MA、BOLL、MACD 指标（主图切换 + MACD 副图），可按区间下拉或**指定任意日期区间**加载，**在图上按住拖动可框选一段并自动加载该区间**（指标按预热段计算，不受影响），可勾选「股息率副图」（TTM 滚动 12 个月股息率 + 每次分红散点），下方「区间表现」表按**图上可见区间**统计涨跌幅/最大回撤/年化波动率，图上标注**交易标记 b 买入 / s 卖出 / q 分红除息**（买卖取本系统流水、分红取东财分红送配），双击图表全屏查看 |
| 基金对比 | 独立页面，最多 3 只归一化（首日=100）走势对比 + 区间涨跌幅/最大回撤/年化波动率，支持预设区间与自定义日期区间，双击全屏 |
| 数据导入 | 代码校验（仅指数基金，主动/债券等被拒）、近 10 年（或自成立）历史导入、异步进度 |
| 策略回测 | 网格交易 + 估值百分位策略、插件式扩展、回测指标（收益/年化/最大回撤/夏普/胜率）与明细 |
| 定时任务 | 15:30 ETF 日线、20:00 场外净值、次日 07:00 补拉、20:30 估值、21:00 信号+邮件、每 5 分钟行情（并写看板快照）、**交易日 17:00 同步非持仓自选基金**、**交易时段（9:30-11:30 / 13:00-15:00）每 10 分钟同步持仓基金**、22:00 同步汇总、22:05 异常告警 |
| AI 助手 | 全站右下角浮窗（悬浮球与面板均可拖动，位置不持久化）；**模型厂商/Base URL/模型/Token 按厂商各存一份，在【平台配置 → AI 模型配置】维护**（智谱/千问/DeepSeek/Kimi/MiniMax，可拉取模型列表并标注免费档，保存即生效**并切换为当前使用厂商**，切换厂商不覆盖别家配置），配置不再写在配置文件里，SSE 流式 + Markdown + 会话历史；**7 类工具**（行情/档案/历史与估值/持仓资产/信号/全球指数 + **平台使用手册**）实时取数；提示词注入防护。**平台怎么用也能问 AI**：手册正文维护在 `docs/05-使用手册.md`（构建期打包进 jar），AI 通过 `getPlatformManual` 按需检索，侧栏【使用手册】页读的是同一份边界分三档：**越界**（创作/闲聊/角色扮演/套取提示词）一句统一拒答；**无数据源**（具体政策新闻宏观、具体个股数据）改为「说明边界与原因 + 给 2 个替代问法」，不生硬拦回；**通用分析方法**（PE/ROE/股息率/估值分位怎么看）可讲，但不掺具体数据、不对个股下买卖结论。**用量与费用**见【AI用量统计】菜单：一次咨询一行流水（工具调用的多轮已累加）、按各厂商单价（输入/缓存命中/输出三段，单价随流水快照，改价不改历史）估算费用；**每日额度全局一份**（token 与费用双阈值，超限直接拒绝且不发起上游请求，自然日 00:00 自动恢复，0 = 不限制），AI 浮窗顶部有用量条 |
| 邮件通知 | 信号摘要、同步异常告警、测试邮件；**SMTP 服务器/端口/发件账号/授权码/收发件人与通知开关全部在【平台配置 → 邮件通知】维护**（存本平台数据库，保存即生效、无需重启），页面可编辑 + 发送测试邮件 |

## AI 协作约定（长期记忆）

- **`AGENTS.md`**（仓库根）：每次会话自动加载的**规则与索引**——项目速览、协作铁律、构建与门禁命令、接口约定、验证纪律与已知环境坑。
- **`.zcode/skills/quant-domain/SKILL.md`**：按需触发的**投研与数据口径常识**（资产与收益口径、复权与真实价、股息率、指数估值、记账规则、数据源降级，以及待拍板的口径项）。
- **`.zcode/skills/frontend-ui/SKILL.md`**：前端强制规范（设计 token、配色铁律、组件与图表模式、列宽与分页约定）。
- **`.zcode/skills/skill-authoring/SKILL.md`**：技能编写规范（该放哪一层、frontmatter 与命名、正文写法、落地清单与验证）——**创建或修改技能前先读它**。
- 职责边界：**规则进 `AGENTS.md`、领域口径进技能、细节以 `docs/02-技术文档.md` 为准**，同一件事不在两处维护。
- **Claude Code 兼容**：项目记忆单源——`CLAUDE.md` 只用 `@AGENTS.md` 导入、不复制正文；技能在两处目录各存一份镜像
  （`.zcode/skills/` 为准、`.claude/skills/` 为镜像），靠 `sh sync-memory.sh` 校验/生成（不一致即退出码 1 失败）。
  改了记忆或技能后请执行：`sh sync-memory.sh --write && sh sync-memory.sh`。

## 本地开发

要求：JDK 21、Maven 3.8+、Node 20+、本地 MySQL（库 `quant`，utf8mb4）与 Redis。

```bash
# 后端（默认 dev profile，连 localhost:3306/6379，首次启动自动建库建表）
mvn spring-boot:run -pl quant-web

# 前端（Vite 开发服务器，/api 代理到 8080）
cd web && npm install && npm run dev

# 默认管理员：admin / admin123（配置项 quant.init.*，首次启动自动创建，登录后请立即改密）
```

## 构建与部署

```bash
# 全量构建（把前端一起打包进 jar；默认 skipFrontend=true，需显式关闭）
cd web && npm install && npm run build      # 产出 web/dist 并拷入 quant-web/src/main/resources/static
cd .. && mvn clean package -DskipTests      # 产出 quant-web/target/quant-web-1.0.0.jar

# Docker 一键部署（应用 + MySQL + Redis + 每日备份，共 4 个容器）
cp .env.example .env    # 填写 DB_PASSWORD / DB_ROOT_PASSWORD / ZHIPU_API_KEY / MAIL_* 等
docker compose up -d --build
# 访问 http://localhost:8080
```

### 环境变量清单（`.env`）

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `DB_PASSWORD` | 是 | 业务库账号 `quant` 的口令 |
| `DB_ROOT_PASSWORD` | 是 | MySQL root 口令（同时用于备份服务） |
| `ZHIPU_API_KEY` | 否 | 智谱 API Key；留空则 AI 对话不可用，但应用正常启动（接口返回中文提示） |
| `BACKUP_RETENTION_DAYS` | 否 | 备份保留天数，默认 14 |
| `BACKUP_INTERVAL_SECONDS` | 否 | 备份间隔秒数，默认 86400（每日一次） |

> 邮箱相关配置（SMTP 服务器/端口/账号/授权码/收发件人/通知开关）**不再走环境变量或配置文件**，统一在【平台配置 → 邮件通知】里维护，保存在业务库 `sys_mail_config` 表中，保存即生效、无需重启——授权码因此也不会出现在仓库或部署变量里。

其他可调项写在 `quant-web/src/main/resources/application.yml`（含中文注释），常用：
`quant.ai.enabled` / `spring.ai.openai.chat.options.model`（AI 开关与模型）、`quant.sync-summary.*`（同步滞后宽限天数）。

### 数据备份与恢复

`mysql-backup` 容器每日执行 `mysqldump`（InnoDB 一致性快照、不锁表），gzip 压缩后写入 `backup-data` 卷并自动清理超期文件；
容器启动时会先备份一次，便于立即验证。

```bash
# 查看备份文件
docker compose exec mysql-backup sh -c 'ls -lh /backup'

# 恢复到业务库（示例：恢复最近一次备份）
docker compose exec mysql-backup sh -c 'ls -t /backup/quant-*.sql.gz | head -1' > /tmp/latest.txt
docker compose exec mysql-backup sh -c "gunzip -c \$(ls -t /backup/quant-*.sql.gz | head -1) | mysql -h mysql -u root -p\$MYSQL_PWD quant"

# 导出备份到宿主机
docker run --rm -v quantisation_backup-data:/backup -v "$PWD":/out alpine \
  sh -c 'cp /backup/$(ls -t /backup | head -1) /out/'
```

> 生产建议：把 `backup-data` 卷中的文件定期同步到宿主机或异地（备份与数据库同机仍属单点）。

## 代码规范与质量门禁

- 后端遵循《阿里巴巴 Java 开发手册》：PMD p3c 规则集接入 `mvn verify` 并阻断（`-DskipPmd=true` 可跳过）
- 前端视觉遵循项目级 skill **`.zcode/skills/frontend-ui/SKILL.md`**（数据驾驶舱风格）：色值/间距/字号只能用设计 token
- 前端命令：`npm run check:style`（硬编码色值门禁，违规即失败）、`npm run type-check`（vue-tsc，0 错误）、`npm run lint`、`npm run format`
- 提交前建议：`mvn verify` + `cd web && npm run type-check && npm run build`

> 使用层面的问题（功能怎么用、参数含义、报错怎么办）不再在此维护：见侧栏【使用手册】或 `docs/05-使用手册.md`，
> 也可以直接问 AI 助手（它读的是同一份手册）。
