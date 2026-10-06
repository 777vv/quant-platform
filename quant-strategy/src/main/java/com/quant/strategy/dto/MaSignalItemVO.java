package com.quant.strategy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 均线信号行视图（V5.70）：【信号查询→均线信号】页签与基金详情信号列表用。
 */
@Data
public class MaSignalItemVO {

    /** 主键 */
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 基金名称（后端补齐，查不到时为空串） */
    private String fundName;

    /** 信号日期（交叉确认的交易日） */
    private LocalDate signalDate;

    /** 短期均线周期 */
    private Integer maShort;

    /** 长期均线周期 */
    private Integer maLong;

    /** 方向：UP=上穿（金叉） DOWN=下穿（死叉） */
    private String direction;

    /** 信号描述：如「5日均线上穿10日均线」 */
    private String signalDesc;

    /** 信号日收盘价/净值 */
    private BigDecimal priceAt;

    /** 信号日短期均线值 */
    private BigDecimal maShortVal;

    /** 信号日长期均线值 */
    private BigDecimal maLongVal;
}
