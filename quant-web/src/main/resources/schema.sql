-- =====================================================
-- 个人量化投资助手 建表脚本（技术文档 4.2，V1.1 口径）
-- 全部 IF NOT EXISTS，配合 spring.sql.init.mode=always 幂等启动
-- 变更说明：position 为 MySQL 关键字，表名调整为 fund_position
-- =====================================================

-- 1. 用户（单用户）
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL COMMENT '用户名',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt哈希',
  nickname      VARCHAR(64)  DEFAULT '' COMMENT '昵称',
  email         VARCHAR(128) DEFAULT '' COMMENT '通知收件邮箱',
  locked_until  DATETIME     DEFAULT NULL COMMENT '防爆破锁定截止时间',
  fail_count    INT          DEFAULT 0 COMMENT '连续登录失败次数',
  last_login_at DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 2.（已移除）邮件配置表：SMTP 参数走配置文件，不建表【V1.1 变更】

-- 3. 基金档案
CREATE TABLE IF NOT EXISTS fund_basic (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code      VARCHAR(12)  NOT NULL COMMENT '基金代码',
  fund_name      VARCHAR(128) NOT NULL COMMENT '基金名称',
  fund_type      TINYINT      NOT NULL COMMENT '1=场内ETF 2=场外指数基金',
  market         VARCHAR(8)   DEFAULT NULL COMMENT 'SH/SZ，ETF必填（东财secid市场前缀）',
  index_code     VARCHAR(16)  DEFAULT NULL COMMENT '跟踪指数代码，如 000300',
  index_name     VARCHAR(64)  DEFAULT NULL COMMENT '跟踪指数名称',
  inception_date DATE         DEFAULT NULL COMMENT '成立日期',
  fund_company   VARCHAR(128) DEFAULT NULL COMMENT '基金公司',
  fund_scale     DECIMAL(18,2) DEFAULT NULL COMMENT '净资产规模（亿元）',
  fund_scale_date DATE        DEFAULT NULL COMMENT '规模数据截止日（东财披露，通常为季末）',
  mgmt_fee_rate  DECIMAL(8,4)  DEFAULT NULL COMMENT '管理费率（%/年）',
  cust_fee_rate  DECIMAL(8,4)  DEFAULT NULL COMMENT '托管费率（%/年）',
  sales_fee_rate DECIMAL(8,4)  DEFAULT NULL COMMENT '销售服务费率（%/年）',
  premium_rate   DECIMAL(10,4) DEFAULT NULL COMMENT '溢价率（%）=（当日收盘价 − 当日单位净值）/ 当日单位净值，仅场内 ETF',
  premium_date   DATE         DEFAULT NULL COMMENT '溢价率对应的净值日（与收盘价同日，保证分子分母同一天）',
  profile_sync_date DATE      DEFAULT NULL COMMENT '档案（规模/费率/跟踪指数）最近刷新日；同日不重复拉取',
  dividend_sync_date DATE     DEFAULT NULL COMMENT '分红记录最近成功刷新日；失败不更新、下次同步自动重试',
  status         TINYINT      DEFAULT 1 COMMENT '1正常 0已删除(自选移除)',
  last_sync_date DATE         DEFAULT NULL COMMENT '行情/净值最后同步日期',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_fund_code (fund_code),
  KEY idx_type (fund_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基金档案表';

-- 4. ETF 日 K（前复权，全量覆盖式同步）
CREATE TABLE IF NOT EXISTS fund_etf_kline (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code  VARCHAR(12) NOT NULL COMMENT '基金代码',
  trade_date DATE        NOT NULL COMMENT '交易日',
  open       DECIMAL(18,4) NOT NULL COMMENT '开盘价(前复权)',
  high       DECIMAL(18,4) NOT NULL COMMENT '最高价(前复权)',
  low        DECIMAL(18,4) NOT NULL COMMENT '最低价(前复权)',
  close      DECIMAL(18,4) NOT NULL COMMENT '收盘价(前复权)',
  unadj_close DECIMAL(18,4) DEFAULT NULL COMMENT '收盘价(未复权)，用于分红股息率等需要真实价格的口径',
  volume     BIGINT        DEFAULT 0 COMMENT '成交量(手)',
  amount     DECIMAL(20,2) DEFAULT 0 COMMENT '成交额(元)',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_fund_date (fund_code, trade_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ETF日K线表(前复权)';

-- 5. 场外净值
CREATE TABLE IF NOT EXISTS fund_nav (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code    VARCHAR(12) NOT NULL COMMENT '基金代码',
  nav_date     DATE        NOT NULL COMMENT '净值日期',
  unit_nav     DECIMAL(18,4) DEFAULT NULL COMMENT '单位净值',
  acc_nav      DECIMAL(18,4) DEFAULT NULL COMMENT '累计净值',
  adj_nav      DECIMAL(18,4) DEFAULT NULL COMMENT '复权净值(本地计算)',
  daily_growth DECIMAL(10,4) DEFAULT NULL COMMENT '日增长率%',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_fund_date (fund_code, nav_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '场外基金净值表';

-- 6. 全球指数快照
CREATE TABLE IF NOT EXISTS index_quote (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  index_code  VARCHAR(32)  NOT NULL COMMENT '东财secid，如 100.DJIA',
  index_name  VARCHAR(64)  NOT NULL COMMENT '指数名称',
  region      VARCHAR(8)   NOT NULL COMMENT 'CN/HK/US/ASIA/EU',
  last_price  DECIMAL(18,4) DEFAULT NULL COMMENT '最新点位',
  change_amt  DECIMAL(18,4) DEFAULT NULL COMMENT '涨跌额',
  change_pct  DECIMAL(10,4) DEFAULT NULL COMMENT '涨跌幅%',
  quote_time  DATETIME     DEFAULT NULL COMMENT '行情时间',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_index_code (index_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '全球指数快照表';

-- 7. 指数估值历史（唯一事实源；基金估值经 fund_basic.index_code 关联查询【V1.1 确认】）
CREATE TABLE IF NOT EXISTS index_valuation (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  index_code VARCHAR(16) NOT NULL COMMENT '指数代码，如 000300',
  trade_date DATE        NOT NULL COMMENT '交易日',
  pe         DECIMAL(18,4) DEFAULT NULL COMMENT '市盈率',
  pb         DECIMAL(18,4) DEFAULT NULL COMMENT '市净率',
  source     VARCHAR(32)  DEFAULT 'EASTMONEY' COMMENT '数据来源',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_index_date (index_code, trade_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指数估值历史表';

-- 8. 交易流水（持仓唯一事实来源）
CREATE TABLE IF NOT EXISTS trade_flow (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code  VARCHAR(12) DEFAULT NULL COMMENT '基金代码；资金转入/转出(4/5)为NULL',
  trade_type TINYINT     NOT NULL COMMENT '1=买入/申购 2=卖出/赎回 3=分红 4=资金转入 5=资金转出',
  trade_date DATE        NOT NULL COMMENT '交易日期',
  price      DECIMAL(18,4) NOT NULL COMMENT 'ETF=成交价 场外=当日净值',
  share      DECIMAL(18,2) NOT NULL COMMENT '份额',
  amount     DECIMAL(18,2) NOT NULL COMMENT '金额',
  fee        DECIMAL(18,2) DEFAULT 0 COMMENT '手续费',
  note       VARCHAR(255) DEFAULT '' COMMENT '备注',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_fund_date (fund_code, trade_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '交易流水表';

-- 迁移：存量表 fund_code 放开非空约束（资金转入/转出不关联基金；MODIFY 重复执行幂等）
ALTER TABLE trade_flow MODIFY COLUMN fund_code VARCHAR(12) DEFAULT NULL COMMENT '基金代码；资金转入/转出(4/5)为NULL';

-- 11. 基金分红记录（东财分红送配页；行情图分红标识【q】的数据源）
CREATE TABLE IF NOT EXISTS fund_dividend (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code     VARCHAR(12) NOT NULL COMMENT '基金代码',
  record_date   DATE        DEFAULT NULL COMMENT '权益登记日',
  ex_date       DATE        NOT NULL COMMENT '除息日（图上标记取此日）',
  pay_date      DATE        DEFAULT NULL COMMENT '分红发放日',
  per10_amount  DECIMAL(12,4) DEFAULT NULL COMMENT '每10份派现金（元）',
  source        VARCHAR(32) DEFAULT NULL COMMENT '数据来源：EASTMONEY_FHSP=东财分红送配页',
  created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
  UNIQUE KEY uk_fund_exdate (fund_code, ex_date),
  KEY idx_fund (fund_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基金分红记录表';

-- 12. AI 模型配置（单行配置：厂商/端点/模型/Token，界面在【平台配置 → AI 模型配置】）
CREATE TABLE IF NOT EXISTS ai_model_config (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键（自增）',
  provider   VARCHAR(32)  NOT NULL COMMENT '厂商：ZHIPU/QWEN/DEEPSEEK/KIMI/MINIMAX（每个厂商一行）',
  base_url   VARCHAR(256) NOT NULL COMMENT 'OpenAI 兼容端点（按厂商各存，互不覆盖）',
  model      VARCHAR(128) NOT NULL COMMENT '模型名（按厂商各存）',
  api_key    VARCHAR(256) DEFAULT NULL COMMENT 'API Token（按厂商各存；本机数据库明文存储，接口返回时打码）',
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间',
  UNIQUE KEY uk_provider (provider)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 模型配置表（每个厂商一行，切换厂商不覆盖）';

-- 迁移：fund_etf_kline 补未复权收盘价（V3.4；历史股息率的分母需要真实价格，前复权价会把历史收益率算高）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_etf_kline' AND COLUMN_NAME = 'unadj_close');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_etf_kline ADD COLUMN unadj_close DECIMAL(18,4) DEFAULT NULL COMMENT ''收盘价(未复权)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 迁移：fund_basic 补规模/费率/溢价率/档案刷新日字段（V2.5）
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，直接 ADD 在第二次启动会报 1060；
-- 故先用 information_schema 判断列是否存在，再决定是否执行 ALTER，保证重复执行幂等。
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'fund_scale');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN fund_scale DECIMAL(18,2) DEFAULT NULL COMMENT ''净资产规模（亿元）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'fund_scale_date');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN fund_scale_date DATE DEFAULT NULL COMMENT ''规模数据截止日''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'mgmt_fee_rate');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN mgmt_fee_rate DECIMAL(8,4) DEFAULT NULL COMMENT ''管理费率（%/年）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'cust_fee_rate');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN cust_fee_rate DECIMAL(8,4) DEFAULT NULL COMMENT ''托管费率（%/年）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'sales_fee_rate');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN sales_fee_rate DECIMAL(8,4) DEFAULT NULL COMMENT ''销售服务费率（%/年）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'premium_rate');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN premium_rate DECIMAL(10,4) DEFAULT NULL COMMENT ''溢价率（%）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'premium_date');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN premium_date DATE DEFAULT NULL COMMENT ''溢价率对应净值日''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'profile_sync_date');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN profile_sync_date DATE DEFAULT NULL COMMENT ''档案最近刷新日''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_basic' AND COLUMN_NAME = 'dividend_sync_date');
SET @sql := IF(@c = 0, 'ALTER TABLE fund_basic ADD COLUMN dividend_sync_date DATE DEFAULT NULL COMMENT ''分红记录最近成功刷新日''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9. 持仓汇总（由流水重算的冗余表；position 为 MySQL 关键字，故命名 fund_position）
CREATE TABLE IF NOT EXISTS fund_position (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code       VARCHAR(12) NOT NULL COMMENT '基金代码',
  total_share     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '持有份额',
  total_cost      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '剩余持仓对应成本',
  avg_cost_price  DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '摊薄成本价',
  realized_pnl    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计已实现盈亏',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_fund_code (fund_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '持仓汇总表';

-- 10. 策略配置
CREATE TABLE IF NOT EXISTS strategy_config (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code     VARCHAR(12) NOT NULL COMMENT '基金代码',
  strategy_type VARCHAR(32) NOT NULL COMMENT 'GRID/VAL_PERCENTILE',
  strategy_name VARCHAR(64) DEFAULT '' COMMENT '策略名称',
  params        JSON        NOT NULL COMMENT '策略参数',
  enabled       TINYINT     DEFAULT 1 COMMENT '1启用 0停用',
  created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_fund_type (fund_code, strategy_type),
  KEY idx_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '策略配置表';

-- 11. 回测记录（结果永久保存）
CREATE TABLE IF NOT EXISTS backtest_record (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code           VARCHAR(12) NOT NULL COMMENT '基金代码',
  strategy_type       VARCHAR(32) NOT NULL COMMENT '策略类型',
  params              JSON       NOT NULL COMMENT '回测参数快照',
  start_date          DATE       NOT NULL COMMENT '回测开始日期',
  end_date            DATE       NOT NULL COMMENT '回测结束日期',
  initial_capital     DECIMAL(18,2) NOT NULL COMMENT '初始资金',
  final_assets        DECIMAL(18,2) DEFAULT NULL COMMENT '期末资产',
  total_return_pct    DECIMAL(10,4) DEFAULT NULL COMMENT '总收益率%',
  annualized_pct      DECIMAL(10,4) DEFAULT NULL COMMENT '年化收益率%',
  max_drawdown_pct    DECIMAL(10,4) DEFAULT NULL COMMENT '最大回撤%',
  dd_peak_date        DATE       DEFAULT NULL COMMENT '回撤峰值日',
  dd_trough_date      DATE       DEFAULT NULL COMMENT '回撤谷底日',
  dd_recover_date     DATE       DEFAULT NULL COMMENT '回撤修复日',
  sharpe              DECIMAL(10,4) DEFAULT NULL COMMENT '夏普比率',
  win_rate            DECIMAL(10,4) DEFAULT NULL COMMENT '胜率%',
  trade_count         INT        DEFAULT 0 COMMENT '交易笔数',
  avg_position_share  DECIMAL(18,2) DEFAULT NULL COMMENT '平均仓位份额：决策期逐日持仓份额均值（V5.11）',
  bench_total_return_pct DECIMAL(10,4) DEFAULT NULL COMMENT '持有总收益%：买入持有基准区间总收益率（V5.11）',
  bench_max_drawdown_pct DECIMAL(10,4) DEFAULT NULL COMMENT '持有最大回撤%：买入持有基准最大回撤（V5.11）',
  avg_position_value  DECIMAL(18,2) DEFAULT NULL COMMENT '平均持仓市值：决策期逐日持仓市值均值（V5.12）',
  avg_position_cost   DECIMAL(18,2) DEFAULT NULL COMMENT '平均持仓成本：决策期逐日摊薄成本×份额均值（V5.14）',
  position_return_pct DECIMAL(10,4) DEFAULT NULL COMMENT '持仓资产收益率%：策略收益÷平均持仓成本（V5.14 成本口径）',
  status              TINYINT    DEFAULT 0 COMMENT '0运行中 1成功 2失败',
  error_msg           VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  equity_curve        MEDIUMTEXT  DEFAULT NULL COMMENT '资金曲线JSON [{d,v}]',
  drawdown_curve      MEDIUMTEXT  DEFAULT NULL COMMENT '回撤曲线JSON [{d,dd}]',
  benchmark_curve     MEDIUMTEXT  DEFAULT NULL COMMENT '基准(买入持有)曲线JSON [{d,v}]',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_fund (fund_code, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回测记录表';


SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'avg_position_share');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN avg_position_share DECIMAL(18,2) DEFAULT NULL COMMENT ''平均仓位份额''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'bench_total_return_pct');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN bench_total_return_pct DECIMAL(10,4) DEFAULT NULL COMMENT ''持有总收益%''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'bench_max_drawdown_pct');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN bench_max_drawdown_pct DECIMAL(10,4) DEFAULT NULL COMMENT ''持有最大回撤%''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'avg_position_value');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN avg_position_value DECIMAL(18,2) DEFAULT NULL COMMENT ''平均持仓市值''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'position_return_pct');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN position_return_pct DECIMAL(10,4) DEFAULT NULL COMMENT ''持仓资产收益率%''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'backtest_record' AND COLUMN_NAME = 'avg_position_cost');
SET @sql := IF(@c = 0, 'ALTER TABLE backtest_record ADD COLUMN avg_position_cost DECIMAL(18,2) DEFAULT NULL COMMENT ''平均持仓成本''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 12. 回测交易明细
CREATE TABLE IF NOT EXISTS backtest_trade_detail (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  backtest_id    BIGINT      NOT NULL COMMENT '回测记录ID',
  trade_date     DATE       NOT NULL COMMENT '交易日',
  direction      VARCHAR(8) NOT NULL COMMENT 'BUY/SELL',
  price          DECIMAL(18,4) NOT NULL COMMENT '成交价',
  share          DECIMAL(18,2) NOT NULL COMMENT '份额',
  amount         DECIMAL(18,2) NOT NULL COMMENT '金额',
  fee            DECIMAL(18,2) DEFAULT 0 COMMENT '手续费',
  cash_after     DECIMAL(18,2) DEFAULT NULL COMMENT '成交后现金',
  position_after DECIMAL(18,2) DEFAULT NULL COMMENT '成交后持仓份额',
  reason         VARCHAR(255) DEFAULT '' COMMENT '信号理由',
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_backtest (backtest_id, trade_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '回测交易明细表';

-- 13. 信号记录（买卖建议）
CREATE TABLE IF NOT EXISTS signal_record (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code     VARCHAR(12) NOT NULL COMMENT '基金代码',
  strategy_type VARCHAR(32) NOT NULL COMMENT '策略类型',
  signal_date   DATE        NOT NULL COMMENT '信号日期',
  direction     VARCHAR(8)  NOT NULL COMMENT 'BUY/SELL/HOLD',
  price_at      DECIMAL(18,4) DEFAULT NULL COMMENT '信号时价格',
  suggest_desc  VARCHAR(255) DEFAULT '' COMMENT '建议描述',
  read_flag     TINYINT     DEFAULT 0 COMMENT '0未读 1已读',
  notified_flag TINYINT     DEFAULT 0 COMMENT '0未通知 1已通知',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_fund_strategy_date (fund_code, strategy_type, signal_date),
  KEY idx_date (signal_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '策略信号记录表';

-- 14. 同步日志
CREATE TABLE IF NOT EXISTS sync_log (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code    VARCHAR(12) DEFAULT NULL COMMENT 'NULL=全局任务',
  sync_type    VARCHAR(16) NOT NULL COMMENT 'HISTORY/ETF_DAILY/NAV/VALUATION/INDEX_QUOTE/SIGNAL',
  status       TINYINT     NOT NULL COMMENT '1成功 0失败',
  record_count INT         DEFAULT 0 COMMENT '新增/处理条数',
  error_msg    VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
  start_time   DATETIME    NOT NULL COMMENT '开始时间',
  end_time     DATETIME    DEFAULT NULL COMMENT '结束时间',
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_start_time (start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '同步日志表';

-- 15. AI 会话
CREATE TABLE IF NOT EXISTS ai_chat_session (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL COMMENT '会话UUID',
  title      VARCHAR(128) DEFAULT '' COMMENT '会话标题',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_session_id (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI会话表';

-- 16. AI 消息
CREATE TABLE IF NOT EXISTS ai_chat_message (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
  role       VARCHAR(16) NOT NULL COMMENT 'user/assistant',
  content    MEDIUMTEXT  NOT NULL COMMENT '消息内容',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_session (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI消息表';

-- 17. 基金标签库（预定义，用户维护）
CREATE TABLE IF NOT EXISTS fund_tag (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(32) NOT NULL COMMENT '标签名（唯一，如 红利/宽基/债券）',
  sort_no    INT         DEFAULT 0 COMMENT '排序号（小者靠前）',
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基金标签库';

-- 18. 基金与标签的关联（多对多；基金移出自选时保留，重新导入即恢复标签）
CREATE TABLE IF NOT EXISTS fund_tag_rel (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code  VARCHAR(12) NOT NULL COMMENT '基金代码',
  tag_id     BIGINT      NOT NULL COMMENT '标签ID（fund_tag.id）',
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_fund_tag (fund_code, tag_id),
  KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基金标签关联';

-- 19. AI 用量流水（V3.9）：每次咨询一行，工具调用的多轮请求已累加；
-- 用于统计 token 与费用、按自然日校验每日额度（额度与单价存 ai_model_config 单行）
CREATE TABLE IF NOT EXISTS ai_usage_log (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id        VARCHAR(64)   DEFAULT NULL COMMENT '会话ID（连通性自检等无会话时为空）',
  biz               VARCHAR(16)   NOT NULL COMMENT '用途：CHAT=对话 / HEALTH=自检 / GUARD=护栏拒答(未调模型)',
  provider          VARCHAR(32)   DEFAULT NULL COMMENT '厂商代码（写入时快照）',
  model             VARCHAR(128)  DEFAULT NULL COMMENT '模型名（写入时快照）',
  rounds            INT           DEFAULT 1 COMMENT '本次咨询的上游请求次数（工具调用会多轮）',
  prompt_tokens     INT           DEFAULT 0 COMMENT '输入 token（含系统提示词与记忆窗口）',
  cached_tokens     INT           DEFAULT 0 COMMENT '输入中命中提示缓存的 token（单独计价）',
  completion_tokens INT           DEFAULT 0 COMMENT '输出 token',
  total_tokens      INT           DEFAULT 0 COMMENT '总 token',
  estimated         TINYINT       DEFAULT 0 COMMENT '1=上游未回 usage，按字符估算',
  input_price       DECIMAL(12,4) DEFAULT 0 COMMENT '输入单价（元/百万token，写入时快照）',
  cache_input_price DECIMAL(12,4) DEFAULT 0 COMMENT '缓存命中输入单价（元/百万token，快照）',
  output_price      DECIMAL(12,4) DEFAULT 0 COMMENT '输出单价（元/百万token，写入时快照）',
  cost              DECIMAL(12,6) DEFAULT 0 COMMENT '估算费用（元）',
  question_chars    INT           DEFAULT 0 COMMENT '提问字数',
  answer_chars      INT           DEFAULT 0 COMMENT '回答字数',
  duration_ms       INT           DEFAULT NULL COMMENT '耗时（毫秒；流式为开始到结束）',
  status            TINYINT       DEFAULT 1 COMMENT '1成功 0失败',
  error_msg         VARCHAR(512)  DEFAULT NULL COMMENT '失败原因',
  created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间（按自然日聚合）',
  KEY idx_created (created_at),
  KEY idx_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI用量流水表';

-- 迁移：ai_model_config 补「每日额度 + 单价」字段（V3.9）
-- 额度：0 或 NULL 表示该维度不限制；单价单位＝元/百万 token，写入流水时快照，故改价不影响历史费用。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，沿用 information_schema 判断后再 ALTER 的幂等写法。
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'daily_token_limit');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN daily_token_limit INT DEFAULT 300000 COMMENT ''每日token上限（0=不限制）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'daily_cost_limit');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN daily_cost_limit DECIMAL(12,4) DEFAULT 3.0000 COMMENT ''每日费用上限（元，0=不限制）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'warn_percent');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN warn_percent INT DEFAULT 80 COMMENT ''预警百分比（用量达该比例时提示；0=不预警）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'input_price');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN input_price DECIMAL(12,4) DEFAULT 0 COMMENT ''输入单价（元/百万token，0=不计算费用）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'cache_input_price');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN cache_input_price DECIMAL(12,4) DEFAULT 0 COMMENT ''缓存命中输入单价（元/百万token；0=按输入单价计）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'output_price');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD COLUMN output_price DECIMAL(12,4) DEFAULT 0 COMMENT ''输出单价（元/百万token，0=不计算费用）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== V4.0 迁移：AI 模型配置由「单行」改为「每个厂商一行」+ 额度抽到全局单行表 =====
-- 背景：单行存储下"换厂商保存"必然覆盖上一家（用户反馈的问题）；改成按厂商各存一份后，
-- 连接信息与单价都是 per-provider，切换厂商只是换 active_provider 指针，不再丢配置。
-- 迁移：id 改为自增（原固定值 1 的那行保留，成为该厂商的一行）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'id' AND EXTRA LIKE '%auto_increment%');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT ''主键（自增）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 迁移：每个厂商只能一行（唯一键）
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND INDEX_NAME = 'uk_provider');
SET @sql := IF(@c = 0, 'ALTER TABLE ai_model_config ADD UNIQUE KEY uk_provider (provider)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 20. AI 运行时配置（单行：当前启用的厂商 + 全局每日额度）
-- 额度为什么是全局一份：它是"这个平台每天最多花多少"的运营政策，与用哪家厂商无关；
-- 若跟着厂商走，切到别家会静默改掉你的额度（单价才是 per-provider 的）。
CREATE TABLE IF NOT EXISTS ai_runtime_config (
  id                BIGINT PRIMARY KEY COMMENT '固定主键（单行，值恒为 1）',
  active_provider   VARCHAR(32)   DEFAULT NULL COMMENT '当前启用的厂商代码（对应 ai_model_config.provider）',
  daily_token_limit INT           DEFAULT 300000 COMMENT '每日 token 上限（0 或不限制；自然日口径）',
  daily_cost_limit  DECIMAL(12,4) DEFAULT 3.0000 COMMENT '每日费用上限（元；0 = 不限制）',
  warn_percent      INT           DEFAULT 80 COMMENT '预警百分比（用量达该比例时提示；0 = 不预警）',
  updated_at        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI运行时配置表（当前厂商 + 全局额度）';

-- 迁移：把原单行里的「当前厂商 + 额度」搬到 ai_runtime_config（已存在则跳过，重复启动幂等）
-- 用 INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE：后者在 INSERT...SELECT 里写 `id = id` 会
-- "Column 'id' in field list is ambiguous"（目标表与来源表都有 id），实测启动即失败。
INSERT IGNORE INTO ai_runtime_config (id, active_provider, daily_token_limit, daily_cost_limit, warn_percent)
SELECT 1, provider, daily_token_limit, daily_cost_limit, warn_percent FROM ai_model_config WHERE id = 1;

-- 迁移：额度列从 ai_model_config 移除（值已搬到 ai_runtime_config；留着会与全局额度形成两个真相）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'daily_token_limit');
SET @sql := IF(@c = 0, 'SELECT 1', 'ALTER TABLE ai_model_config DROP COLUMN daily_token_limit');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'daily_cost_limit');
SET @sql := IF(@c = 0, 'SELECT 1', 'ALTER TABLE ai_model_config DROP COLUMN daily_cost_limit');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_model_config' AND COLUMN_NAME = 'warn_percent');
SET @sql := IF(@c = 0, 'SELECT 1', 'ALTER TABLE ai_model_config DROP COLUMN warn_percent');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 21. 邮件通知配置（V4.9：从 yml 迁到库，平台配置页维护，保存即生效）
-- 授权码明文存本机库（与 ai_model_config.api_key 同一约定），接口回显打码、留空不覆盖。
CREATE TABLE IF NOT EXISTS sys_mail_config (
  id            BIGINT PRIMARY KEY COMMENT '固定主键（单行配置，值恒为 1）',
  enabled       TINYINT      DEFAULT 0 COMMENT '邮件通知总开关：1 启用（每日摘要/告警），0 只允许手动测试',
  host          VARCHAR(128) DEFAULT NULL COMMENT 'SMTP 服务器（如 smtp.qq.com；空=未启用邮件）',
  port          INT          DEFAULT 465 COMMENT 'SMTP 端口（SSL 465 / STARTTLS 587）',
  username      VARCHAR(128) DEFAULT NULL COMMENT 'SMTP 登录账号（通常即发件邮箱）',
  password      VARCHAR(128) DEFAULT NULL COMMENT 'SMTP 授权码（明文存本机库；接口打码）',
  from_addr     VARCHAR(128) DEFAULT NULL COMMENT '发件人地址（空=用 SMTP 账号）',
  to_addr       VARCHAR(128) DEFAULT NULL COMMENT '收件人地址（空=取用户资料的通知邮箱）',
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮件通知配置表';

-- 迁移：初始化单行（仅当表为空时插入——首次升级后到【平台配置 → 邮件通知】填一次 SMTP 即可；
-- 值不在本脚本里写死，避免仓库出现明文授权码）
INSERT IGNORE INTO sys_mail_config (id, enabled, host, port, username, password, from_addr, to_addr)
SELECT 1, 0, NULL, 465, NULL, NULL, NULL, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_mail_config WHERE id = 1);

-- V5.20 微信通知配置（V5.20：企业微信自建应用 + 微信插件 → 消息直达个人微信；单行配置）
-- Secret 明文存本机库（与邮件授权码同一约定），接口打码、留空不覆盖。
CREATE TABLE IF NOT EXISTS sys_wecom_config (
  id         BIGINT PRIMARY KEY COMMENT '固定主键（单行配置，值恒为 1）',
  enabled    TINYINT      DEFAULT 0 COMMENT '微信通知总开关：1 启用（交易信号推送），0 关闭',
  corpid     VARCHAR(64)  DEFAULT NULL COMMENT '企业 ID（企业微信 myqyapi 后台-我的企业）',
  agent_id   VARCHAR(32)  DEFAULT NULL COMMENT '自建应用的 AgentId',
  secret     VARCHAR(128) DEFAULT NULL COMMENT '自建应用的 Secret（明文存本机库；接口打码）',
  touser     VARCHAR(256) DEFAULT '@all' COMMENT '接收人（企业微信 userid，多个用 | 分隔；@all=全员）',
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '微信通知配置表';

INSERT IGNORE INTO sys_wecom_config (id, enabled, corpid, agent_id, secret, touser)
SELECT 1, 0, NULL, NULL, NULL, '@all'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_wecom_config WHERE id = 1);

-- V5.3 基金规模历史（每日档案刷新成功后落一行，幂等同日覆盖；供行情图「基金规模」副图使用。
-- 注意：数据源只披露当前规模，历史无法回补，曲线自本表上线日起逐日积累）
CREATE TABLE IF NOT EXISTS fund_scale_history (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  fund_code   VARCHAR(12) NOT NULL COMMENT '基金代码',
  stat_date   DATE        NOT NULL COMMENT '统计日期（档案刷新成功那天）',
  fund_scale  DECIMAL(18,2) DEFAULT NULL COMMENT '净资产规模（亿元）',
  scale_date  DATE        DEFAULT NULL COMMENT '规模数据截止日（东财披露，通常为季末）',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_fund_date (fund_code, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基金规模历史表';

-- V5.24 市场休市日名单（交易日判定）：把"今天是不是交易日"从"周一到周五"修正为
-- "工作日且不在休市名单"——定时任务（信号推送/盘中同步）据此在节假日不动作。
-- source=manual：交易所公告的法定节假日休市安排（**每年公布次年安排后需补录次年休市日**，见下）；
-- source=observed：平台盘面自判确认的休市日（连续两轮成功请求但无当天 bar）自动补录，可人工删除。
-- 周末不落库：判定时直接按星期几排除，避免名单里堆满无意义的周六日。
CREATE TABLE IF NOT EXISTS market_holiday (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  holiday_date DATE        NOT NULL COMMENT '休市日期（自然日）',
  holiday_name VARCHAR(64) DEFAULT NULL COMMENT '休市说明（如 中秋节、国庆节；自判写入判定依据）',
  source       VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT '来源：manual=人工/内置名单，observed=平台盘面自判',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_holiday_date (holiday_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '市场休市日名单（交易日判定用）';

-- 2026 年剩余休市日（依据证监会 2026 年节假日休市安排 / 三大交易所公告）：
-- 中秋节 9/25（周五）休市，9/28（周一）开市；国庆节 10/1（周四）~10/7（周三）休市，10/8（周四）开市。
-- 名单只登记"工作日里的休市日"，周末（9/26、9/27、10/3、10/4）不登记。
-- ⚠️ 次年（2027）安排在国务院/交易所公布后（通常 11-12 月）按同样格式追加：一行 INSERT IGNORE 即可。
INSERT IGNORE INTO market_holiday (holiday_date, holiday_name, source) VALUES
  ('2026-09-25', '中秋节', 'manual'),
  ('2026-10-01', '国庆节', 'manual'),
  ('2026-10-02', '国庆节', 'manual'),
  ('2026-10-05', '国庆节', 'manual'),
  ('2026-10-06', '国庆节', 'manual'),
  ('2026-10-07', '国庆节', 'manual');
