package com.quant.fund.controller;

import java.time.LocalDate;
import java.util.List;

import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.fund.dto.FundDetailVO;
import com.quant.fund.dto.DividendYieldVO;
import com.quant.fund.dto.FundScaleHistoryVO;
import com.quant.fund.dto.FundMarkVO;
import com.quant.fund.dto.SeriesPoint;
import com.quant.fund.dto.ValuationSeriesVO;
import com.quant.fund.dto.WatchItemVO;
import com.quant.fund.service.DividendYieldService;
import com.quant.fund.service.FundScaleHistoryService;
import com.quant.fund.service.FundMarkService;
import com.quant.fund.service.FundQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基金池查询接口（FR2）
 */
@RestController
@RequestMapping("/api/funds")
public class FundController {

    private final FundQueryService fundQueryService;

    /** 行情图交易标记（买入/卖出/分红） */
    private final FundMarkService fundMarkService;

    /** 分红股息率（单次 + TTM） */
    private final DividendYieldService dividendYieldService;

    /** 基金规模历史（行情图「基金规模」副图） */
    private final FundScaleHistoryService scaleHistoryService;

    public FundController(FundQueryService fundQueryService, FundMarkService fundMarkService,
                          DividendYieldService dividendYieldService,
                          FundScaleHistoryService scaleHistoryService) {
        this.fundQueryService = fundQueryService;
        this.fundMarkService = fundMarkService;
        this.dividendYieldService = dividendYieldService;
        this.scaleHistoryService = scaleHistoryService;
    }

    /** 自选基金列表（最新价/涨跌幅/估值百分位/最后同步时间） */
    @GetMapping("/watchlist")
    public R<List<WatchItemVO>> watchlist() {
        return R.ok(fundQueryService.watchlist());
    }

    /**
     * 自选基金分页列表（服务端分页 + 服务端筛选：关键词/标签）。
     *
     * @param keyword 关键词（基金代码或名称），为空表示不限
     * @param tag     标签名，为空表示不限
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     */
    @GetMapping("/watchlist/page")
    public R<PageResult<WatchItemVO>> watchlistPage(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return R.ok(fundQueryService.pageWatchlist(keyword, tag, page, size));
    }

    /**
     * 行情图交易标记（买入 b / 卖出 s / 分红 q）：买入卖出取本系统流水，分红取东财分红送配的除息日。
     * 只返回日期与文案，前端一次取回后本地渲染，不参与 K 线请求、缩放与框选也不会重新请求。
     */
    @GetMapping("/{code}/marks")
    public R<List<FundMarkVO>> marks(@PathVariable String code) {
        return R.ok(fundMarkService.list(code));
    }

    /**
     * 分红股息率：单次分红股息率 + TTM 滚动 12 个月股息率（口径见 DividendYieldVO）。
     *
     * @param code  基金代码
     * @param range 回看自然日数（默认 10 年）
     */
    @GetMapping("/{code}/dividend-yield")
    public R<DividendYieldVO> dividendYield(@PathVariable String code,
            @RequestParam(defaultValue = "3650") int range) {
        return R.ok(dividendYieldService.series(code, range));
    }

    /** 基金规模历史（每日档案刷新后逐日积累；按日期升序） */
    @GetMapping("/{code}/scale-history")
    public R<List<FundScaleHistoryVO>> scaleHistory(@PathVariable String code) {
        return R.ok(scaleHistoryService.history(code));
    }

    /** 基金详情（档案 + 最新行情） */
    @GetMapping("/{code}/detail")
    public R<FundDetailVO> detail(@PathVariable String code) {
        return R.ok(fundQueryService.detail(code));
    }

    /** ETF 前复权日K序列（range=回看天数；传 start/end 则按自定义区间精确过滤） */
    @GetMapping("/{code}/kline")
    public R<List<SeriesPoint>> kline(@PathVariable String code,
                                     @RequestParam(defaultValue = "365") int range,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return R.ok(fundQueryService.kline(code, range, start, end));
    }

    /** 场外净值序列（单位/累计/复权净值） */
    @GetMapping("/{code}/nav")
    public R<List<SeriesPoint>> nav(@PathVariable String code,
                                    @RequestParam(defaultValue = "365") int range,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return R.ok(fundQueryService.nav(code, range, start, end));
    }

    /** 基金估值序列（经跟踪指数关联，含当前 PE 百分位） */
    @GetMapping("/{code}/valuation")
    public R<ValuationSeriesVO> valuation(@PathVariable String code, @RequestParam(defaultValue = "3650") int range) {
        return R.ok(fundQueryService.valuation(code, range));
    }

    /** 移出自选（软删，历史数据保留，重新导入可恢复） */
    @DeleteMapping("/{code}")
    public R<Void> remove(@PathVariable String code) {
        fundQueryService.removeFromWatchlist(code);
        return R.ok();
    }
}
