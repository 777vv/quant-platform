package com.quant.fund.service;

import com.quant.fund.dto.FundMaVO;
import com.quant.fund.dto.MaRunResultVO;

import java.util.List;

/**
 * 基金日均线服务（V5.68）：计算并落表 5/10/20/30/60/90/120/250 个交易日均价（SMA），
 * 供市场信号【均价】页签展示"现价 ÷ 均线"比值。数据来源与市场信号/基金对比同源：
 * ETF = 前复权收盘价；场外 = 复权净值（缺失回退单位净值）。
 */
public interface FundMaService {

    /**
     * 刷新：为每只自选基金按其**最新行情数据日**计算均线并落表（幂等，重复跑覆盖当天）。
     * 仅交易日可调用（非交易日抛业务异常，前端拦截提示）。
     */
    MaRunResultVO refresh();

    /**
     * 回跑：取自选池全部价格数据日的并集，回退 N 个交易日，逐日计算全量基金的均线并落表
     * （幂等，已存在的日期覆盖更新）。用于首次建表补历史或数据修复。
     *
     * @param tradingDays 回跑的交易日数（5~500，超界收敛）
     */
    MaRunResultVO backfill(int tradingDays);

    /** 【均价】页签数据：每只基金最新一条快照（含现价与各周期比值，比值 3 位小数） */
    List<FundMaVO> latest();
}
