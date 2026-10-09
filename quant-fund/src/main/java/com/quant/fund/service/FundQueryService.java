package com.quant.fund.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.quant.common.result.PageResult;
import com.quant.fund.dto.FundDetailVO;
import com.quant.fund.dto.LastQuote;
import com.quant.fund.dto.SeriesPoint;
import com.quant.fund.dto.ValuationSeriesVO;
import com.quant.fund.dto.WatchItemVO;
import com.quant.fund.entity.FundBasic;

/**
 * 基金查询服务：自选列表/详情/K线/净值/估值
 */
public interface FundQueryService {

    List<WatchItemVO> watchlist();

    /**
     * 基金下拉选项（V6.01）：代码+名称+类型（整数），status=1 自选池按代码升序；
     * 服务端 15 分钟缓存，过期再查库——给筛选下拉/名称映射这类轻量场景用，别再拉重量级 watchlist。
     */
    List<com.quant.fund.dto.FundOptionVO> fundOptions();

    /**
     * 自选池分页查询（服务端分页 + 服务端筛选）。
     * 分页后关键词与标签筛选必须在服务端完成，否则只能筛到当前页的数据。
     *
     * @param keyword 关键词（基金代码或名称，忽略大小写），为空表示不限
     * @param tag     标签名（预定义标签库中的名称），为空表示不限
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @return 分页结果（按基金代码升序，顺序稳定以保证翻页不重复/不遗漏）
     */
    PageResult<WatchItemVO> pageWatchlist(String keyword, String tag, long page, long size);

    FundDetailVO detail(String fundCode);

    List<SeriesPoint> kline(String fundCode, int rangeDays);

    /**
     * ETF 前复权日K序列（自定义日期区间；start/end 任一为空则回退到 rangeDays 口径）。
     *
     * @param fundCode  基金代码
     * @param rangeDays 回看天数（start/end 为空时生效）
     * @param start     起始日（含），可为 null
     * @param end       结束日（含），可为 null
     */
    List<SeriesPoint> kline(String fundCode, int rangeDays, LocalDate start, LocalDate end);

    List<SeriesPoint> nav(String fundCode, int rangeDays);

    /**
     * 场外净值序列（自定义日期区间；start/end 任一为空则回退到 rangeDays 口径）。
     *
     * @param fundCode  基金代码
     * @param rangeDays 回看天数（start/end 为空时生效）
     * @param start     起始日（含），可为 null
     * @param end       结束日（含），可为 null
     */
    List<SeriesPoint> nav(String fundCode, int rangeDays, LocalDate start, LocalDate end);

    ValuationSeriesVO valuation(String fundCode, int rangeDays);

    void removeFromWatchlist(String fundCode);

    FundBasic getByCodeRequired(String fundCode);

    /** 最新行情（ETF 前复权收盘/场外单位净值），无数据返回 null */
    LastQuote lastQuote(FundBasic fund);

    /** 跟踪指数近 windowYears 年 PE 百分位（0-100），无数据返回 null */
    BigDecimal percentileOfIndexPe(String indexCode, int windowYears);
}
