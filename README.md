# 策略数据研究平台

> **该平台仅供个人学习和参考，不构成投资建议，投资决策与风险由本人承担。**

只给买卖建议、不做自动化交易、只覆盖指数基金（场内 ETF + 场外指数基金）的策略数据研究平台。
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
quant-strategy  策略引擎（金字塔网格/倒金字塔网格/震荡向上/红利网格/纳指网格）/回测引擎/信号计算/邮件与微信通知
quant-ai        AI 投资助手（Spring AI 2.x 经智谱 OpenAI 兼容端点 + 7 类工具）
quant-web       启动与打包（含前端静态资源托管）
web/            Vue3 前端源码
deploy/         部署辅助脚本（MySQL 每日备份）
docs/           需求 / 技术 / 任务清单三份文档
```

## 功能一览

| 模块　　　　　　 | 说明 |
| --- | --- |
| 仪表盘 | 指数看板按市场切换（A 股/港 股/美 股/亚 太/欧 洲，一次只看一个市场，桌面端一行 6 个不换行）；KPI 区为「品牌渐变总资产主卡 + 6 张副卡」（副卡带涨跌色条/数值底色块/迷你走势）；尚无持仓成交时以「开始使用」三步引导卡替代成排空态；17 个全球指数看板，按 A 股 / 港 股 / 美 股 / 亚 太 / 欧 洲 分组展示（含近 30 交易日迷你线）；**看板缓存优先**——页面只读库内快照（毫秒级），5 分钟任务负责写快照，右上角刷新按钮走 `POST /api/dashboard/indices/refresh` 强刷最新；另有 7 张资产卡片（总资产/当日/浮动/累计/本月/本年/近 7 日）、收益曲线（7 个时间维度 + 沪深300 基准）、速览区（最新信号、近 7 日收益柱图、持仓速览、涨跌榜、资产配置、同步状态），其中**涨跌榜与持仓概览的基金名可点击进基金详情**、**资产配置图例换行铺开不分页**（V5.38） |
| 记账 | 【交易流水】页右上「记一笔」与基金详情页持仓行内「录流水」共用弹窗：买入/卖出/分红/资金转入/资金转出五种类型；**买入卖出只填成交金额与成交数量，成交价按 金额÷数量 自动派生（只读，4 位小数）**，分红按现金分红（税前）只填金额；**资金转出不可超过现金余额**（弹窗内显示可转出上限）；填写后即时预估交易后的持有份额、摊薄成本价与浮动/已实现盈亏 |
| 交易流水 | 独立菜单：全部流水可按基金/类型/日期区间筛选并分页查看（只读，更正与删除在基金详情页的流水 Tab 内完成） |
| 行情图表 | K 线/净值支持 MA、BOLL、MACD 指标（主图切换 + MACD 副图），可按区间下拉或**指定任意日期区间**加载，**在图上按住拖动可框选一段并自动加载该区间**（指标按预热段计算，不受影响），可勾选「股息率副图」（TTM 滚动 12 个月股息率 + 每次分红散点），下方「区间表现」表按**图上可见区间**统计涨跌幅/最大回撤/年化波动率，图上标注**交易标记 b 买入 / s 卖出 / q 分红除息**（买卖取本系统流水、分红取东财分红送配），双击图表全屏查看 |
| 基金对比 | 独立页面，最多 3 只归一化（首日=100）走势对比 + 区间涨跌幅/最大回撤/年化波动率，支持预设区间与自定义日期区间，双击全屏 |
| 数据导入 | 代码校验（仅指数基金，主动/债券等被拒）、近 15 年（或自成立）历史导入、异步进度 |
| 策略回测 | **金字塔网格 / 倒金字塔网格 / 震荡向上 / 红利网格 / 纳指网格**五种策略（插件式扩展：新增策略 = 新增一个实现类）、回测指标（收益/年化/最大回撤/夏普/胜率）与明细 |
| 信号查询 | 独立页面查看全部历史信号（每日 21:00 定时生成）：按基金代码/方向（买入/卖出/持有）/策略/日期区间筛选，服务端分页，显示信号价、建议说明与「未读/已邮件通知」状态；点击行标记已读并跳转基金详情（与仪表盘今日信号卡同源） |
| 市场信号 | 每天自己打开看的指标看板（V5.39，不发推送）：① 涨跌榜 7/15/30/60/250 日涨跌幅矩阵，表头注明各窗口的正确读法（A 股短期反转、中期动量、52 周位置）；② 估值红绿灯（PE 百分位 <30% 低估/超过 70% 高估，分位窗口近3/5/10年/全历史可切换，含股息率 TTM 与样本天数）；③ 技术面与溢价（MA20/60/200 上/下 + 多空排列、回撤深度/回撤分位、场内 ETF 溢价率超阈值整行标红，阈值默认 3% 前端可改即时生效）；纯库内计算只读接口，与策略信号/仓位告警语义隔离 |
| 登录日志 | 每次登录尝试记一行（成功与失败都记）：时间（到秒）、结果、用户名、失败原因、IP、归属地、客户端 UA；IP 按反向代理链取真实来源（X-Forwarded-For → X-Real-IP → RemoteAddr），内网标注「内网」，公网归属地由在线 IP 库异步解析回填（双源降级、3 秒超时、失败保留「待解析」不影响登录）；可按用户名与结果筛选、每页 10 条分页；与防爆破联动（锁定期间的尝试也留痕） |
| 定时任务 | 09:00 信号+邮件（盘前，基于上一交易日收盘）、**盘中同步两档（V5.42）：持仓 ETF 每 4 分钟 + 非持仓自选每 30 分钟（交易时段内生效，cron 错开 3 分钟；节假日数据源自判、当天跳过）**；**每个定时任务都打「开始执行 / 执行结束（耗时）」日志并用任务级 traceId 串联本轮全部日志**（V5.38，失败记 error + 完整堆栈）、15:05 档案规模刷新（并落规模历史）、15:30 ETF 日线（收盘价定稿）、20:00 场外净值、次日 07:00 补拉、20:30 估值、每 5 分钟行情（并写看板快照）、22:00 同步汇总、22:05 异常告警 |
| AI 助手 | 全站右下角浮窗（悬浮球与面板均可拖动、面板四角均可缩放，位置不持久化）；**模型厂商/Base URL/模型/Token 按厂商各存一份，在【平台配置 → AI 模型配置】维护**（智谱/千问/DeepSeek/Kimi/MiniMax，可拉取模型列表并标注免费档，保存即生效**并切换为当前使用厂商**，切换厂商不覆盖别家配置），配置不再写在配置文件里，SSE 流式 + Markdown + 会话历史；**7 类工具**（行情/档案/历史与估值/持仓资产/信号/全球指数 + **平台使用手册**）实时取数；提示词注入防护。**平台怎么用也能问 AI**：手册正文维护在 `docs/05-使用手册.md`（构建期打包进 jar），AI 通过 `getPlatformManual` 按需检索，侧栏【使用手册】页读的是同一份边界分三档：**越界**（创作/闲聊/角色扮演/套取提示词）一句统一拒答；**无数据源**（具体政策新闻宏观、具体个股数据）改为「说明边界与原因 + 给 2 个替代问法」，不生硬拦回；**通用分析方法**（PE/ROE/股息率/估值分位怎么看）可讲，但不掺具体数据、不对个股下买卖结论。**用量与费用**见【AI用量统计】菜单：一次咨询一行流水（工具调用的多轮已累加）、按各厂商单价（输入/缓存命中/输出三段，单价随流水快照，改价不改历史）估算费用；**每日额度全局一份**（token 与费用双阈值，超限直接拒绝且不发起上游请求，自然日 00:00 自动恢复，0 = 不限制），AI 浮窗顶部有用量条 |
| 邮件通知 + 微信通知 | 信号摘要邮件（QQ 邮箱 SMTP）、同步异常告警、测试邮件；**SMTP 与微信推送均在【平台配置】维护**（微信走企业微信自建应用 + 微信插件，消息直达个人微信）；邮件失败自动重试、微信失败只记日志两通道互不影响 |
| 数据备份 | 每日定时备份（MySQL 备份表 + 保留 N 天），支持手动恢复 |

## AI 协作约定（长期记忆）

- **`AGENTS.md`**（仓库根）：每次会话自动加载的**规则与索引**——项目速览、协作铁律、构建与门禁命令、接口约定、验证纪律与已知环境坑。
- **`.zcode/skills/quant-domain/SKILL.md`**：按需触发的**投研与数据口径常识**（资产与收益口径、复权与真实价、股息率、指数估值、记账规则、数据源降级，以及待拍板的口径项）。
- **`.zcode/skills/frontend-ui/SKILL.md`**：前端强制规范（设计 token、配色铁律、组件与图表模式、列宽与分页约定）。
- **`.zcode/skills/skill-authoring/SKILL.md`**：技能编写规范（该放哪一层、frontmatter 与命名、正文写法、落地清单与验证）——**创建或修改技能前先读它**。
- 职责边界：**规则进 `AGENTS.md`、领域口径进技能、细节以 `docs/02-技术文档.md` 为准**，同一件事不在两处维护。
- **Claude Code 兼容**：`CLAUDE.md` 是 `AGENTS.md` 的**完整镜像**、`.claude/skills/` 是 `.zcode/skills/` 的完整镜像——内容只改 `AGENTS.md` 与 `.zcode/skills/`，两个镜像由脚本生成、**勿手改**；`sh sync-memory.sh` 校验一致性、`--write` 重新生成（不一致即退出码 1 失败）。**只要动了一边，必须同步两边。**
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

### 用 1Panel 部署（容器编排）

服务器上装了 1Panel 时，按"服务器上有没有现成的 MySQL/Redis"选一条路线：

| 路线 | 适用 | 用到的文件 |
| --- | --- | --- |
| **A. 复用 1Panel 商店的 MySQL/Redis**（推荐） | 1Panel 里已经装了 MySQL/Redis：省内存，数据库备份/权限统一在面板里管 | `deploy/1panel/docker-compose.yml` + `.env` + `app.jar` |
| **B. 自包含四容器** | 服务器上没有现成数据库，想一把梭 | 仓库根目录 `docker-compose.yml`（app + mysql + redis + 每日备份） |

**路线 A 步骤**

1. 本机构建带界面的 jar：`sh build.sh -DskipFrontend=false`，产物 `quant-web/target/quant-web-1.0.0.jar`，复制一份改名 `app.jar`（前端静态资源已打进 jar，部署只需这一个文件）。
2. 1Panel → **数据库** → 创建数据库 `quant`（顺带建账号 `quant` 与口令）；Redis 若尚未安装，在应用商店装一个（默认带密码，记下口令）。
3. 1Panel → **容器**，记下 MySQL / Redis 的**容器名**（形如 `1Panel-mysql-XXXX`）。
4. 1Panel → **容器 → 编排 → 创建编排**：把 `deploy/1panel/docker-compose.yml` 的内容贴进去（或路径选择器选文件），面板会自动识别出变量清单，逐项填 `DB_HOST` / `DB_USER` / `DB_PASSWORD` / `REDIS_HOST`（有密码再填 `REDIS_PASSWORD`）/ `ADMIN_PASSWORD`。两个容易踩的点：
   - 连接地址**必须填容器名**（不是 IP、不是 localhost）；编排里必须声明 `1panel-network: external: true` 复用面板已有网络，否则容器之间互相解析不到。
   - 面板里**手动粘贴文件路径会清空同目录的 `.env`**（1Panel 已知问题），优先用路径选择器选文件、或把 `.env` 放好后再核对一遍。
5. 把 `app.jar` 放进编排项目目录（1Panel → **文件**，路径一般是 `/opt/1panel/docker/compose/<项目名>/`），确认并启动。
6. 看日志：1Panel → 容器 → 编排详情/日志；应用自己的日志在项目目录 `logs/app.log`。
7. 对外访问：1Panel → **网站 → 创建网站 → 反向代理**，代理地址填 `http://127.0.0.1:8080`（协议是 http，TLS 由面板的 OpenResty 终止），再申请证书并开启 HTTPS。模板默认把端口只绑在 `127.0.0.1`，不配反向代理就只有本机能访问。

**把本地数据搬上服务器**（服务器上是空库：记账/持仓/回测/信号/平台配置都在本地库里）

```bash
# 本机导出（InnoDB 一致性快照，不锁表）
mysqldump -uroot -proot --single-transaction --default-character-set=utf8mb4 quant > quant-dump.sql

# 上传到服务器后导入（容器名换成你自己的 MySQL 容器）
docker exec -i 1Panel-mysql-XXXX mysql -uroot -p'<root口令>' quant < quant-dump.sql
```

> 邮件、微信、AI 模型的配置也都在库里（`sys_mail_config` / `sys_wecom_config` / AI 模型配置），跟着库一起搬；不搬就在【平台配置】里重填。

**升级**：本机构建新 jar → 覆盖 `app.jar` → 编排页「重建容器」。建表脚本 `schema.sql` 每次启动幂等执行，新增表/字段自动补齐，不需要手工迁移。

**安全**：`.env` 与 `app.jar` 不要提交进仓库（`.env` 已在 `.gitignore` 里）；务必在 `.env` 设 `ADMIN_PASSWORD`（否则首次启动建的是默认 `admin/admin123`）；不要在 1Panel 防火墙或云安全组放行 3306/6379，公网只放 80/443。

**排障**

| 现象 | 原因与处理 |
| --- | --- |
| 拉不动镜像（`TLS handshake timeout` / 超时） | 国内直连 Docker Hub 常失败：1Panel → 容器 → 配置 → **镜像加速**里加国内加速地址；或在能联网的机器上 `docker save eclipse-temurin:21-jre -o temurin.tar` 后到 1Panel → **镜像 → 导入** 上传 tar |
| 启动即退出，日志 `Communications link failure` / `Unknown database` | `DB_HOST`/`DB_NAME`/口令填错；库要先在 1Panel「数据库」里建好 |
| `Name or service not known`（解析不到容器名） | 编排没声明 `1panel-network: external: true`，或容器名抄错（1Panel → 容器 列表里复制） |
| Redis 报 `NOAUTH` 或 `ERR Client sent AUTH, but no password is set` | Redis 口令与 `SPRING_DATA_REDIS_PASSWORD` 不匹配：商店装的 Redis 默认有密码，要取消模板里那行注释并填 `REDIS_PASSWORD`；没密码就保持注释 |
| 09:00 信号任务时间不对 | 容器已设 `TZ=Asia/Shanghai`，且 cron 在 V5.24 起钉死 `Asia/Shanghai`；用 `docker exec quant-app date` 核对是北京时间 |
| 8080 被占用 | 改 `.env` 的 `APP_PORT`（模板只绑 `127.0.0.1`，反代目标同步改） |
| 同步任务大量失败 | 服务器在境外时可能连不上东方财富接口，换境内节点 |

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
