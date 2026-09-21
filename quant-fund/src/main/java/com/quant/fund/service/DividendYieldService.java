package com.quant.fund.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.quant.fund.dto.DividendYieldVO;
import com.quant.fund.entity.FundBasic;

/**
 * 分红股息率服务（单次分红口径 + TTM 滚动 12 个月口径）。
 *
 * <p>分母一律取**真实价格**：场内 ETF 用 `fund_etf_kline.unadj_close`（每日全量同步拉 fqt=0 写入），
 * 场外用 `fund_nav.unit_nav`（本身就是未复权单位净值）。真实价格缺失时返回 null 并由前端降级展示，
 * 不用前复权价顶替（会系统性高估历史股息率）。
 */
public interface DividendYieldService {

    /**
     * 某只基金的股息率序列。
     *
     * @param fundCode  基金代码
     * @param rangeDays 回看自然日数（同时决定 events 的时间窗）
     */
    DividendYieldVO series(String fundCode, int rangeDays);

    /**
     * 批量取"当前 TTM 股息率"（基金池列表列用，避免逐只查询）。
     *
     * <p>当前时点用最新价即可：前复权序列的最新一日就是真实成交价（复权只影响历史段）。
     *
     * @param funds 待计算的基金
     * @return fundCode → TTM 股息率（%）；无分红或无价格的不返回
     */
    Map<String, BigDecimal> currentTtmYields(List<FundBasic> funds);
}
