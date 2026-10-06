package com.quant.fund.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 均线计算落表结果（V5.68）：手动刷新 / 回跑 / 定时任务共用。
 */
@Data
public class MaRunResultVO {

    /** 本次覆盖的基金数 */
    private Integer fundCount;

    /** 本次落表行数（含新增与更新） */
    private Integer rowCount;

    /** 本次覆盖的最早数据日 */
    private LocalDate dateFrom;

    /** 本次覆盖的最晚数据日 */
    private LocalDate dateTo;
}
