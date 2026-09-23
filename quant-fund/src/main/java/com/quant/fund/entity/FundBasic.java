package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 基金档案
 */
@Data
@TableName("fund_basic")
public class FundBasic {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码（唯一，6位数字） */
    private String fundCode;

    /** 基金简称 */
    private String fundName;

    /** 1=场内ETF 2=场外指数基金 */
    private Integer fundType;

    /** 交易所市场 SH=沪 SZ=深（ETF 必填，东财 secid 前缀依据） */
    private String market;

    /** 跟踪指数代码（如 000300），估值关联键 */
    private String indexCode;

    /** 跟踪指数名称（f10 页面解析） */
    private String indexName;

    /** 成立日期（f10 解析，可能为空） */
    private LocalDate inceptionDate;

    /** 基金公司 */
    private String fundCompany;

    /** 净资产规模（亿元），来自东财基金档案页；披露频率为季末，配套 fundScaleDate */
    private BigDecimal fundScale;

    /** 规模数据截止日（东财披露口径） */
    private LocalDate fundScaleDate;

    /** 管理费率（%/年） */
    private BigDecimal mgmtFeeRate;

    /** 托管费率（%/年） */
    private BigDecimal custFeeRate;

    /** 销售服务费率（%/年），场外 C 类份额常见 */
    private BigDecimal salesFeeRate;

    /** 溢价率（%）=（当日收盘价 − 当日单位净值）/ 当日单位净值；仅场内 ETF 有意义 */
    private BigDecimal premiumRate;

    /** 溢价率对应的净值日（与取价同日，保证分子分母同一天） */
    private LocalDate premiumDate;

    /** 档案（规模/费率/跟踪指数）最近刷新日：同日不重复拉取档案页 */
    private LocalDate profileSyncDate;

    /** 分红记录最近"成功"刷新日：只有抓取成功才更新；失败留空/留旧值，下次同步自动重试 */
    private LocalDate dividendSyncDate;

    /** 1=在自选池 0=已移除（软删，历史数据保留） */
    private Integer status;

    /** 本地最新行情/净值日期（增量同步起点=该日+1） */
    private LocalDate lastSyncDate;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
