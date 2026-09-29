package com.quant.fund.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.MarketSignalVO;
import com.quant.fund.service.MarketSignalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场信号接口（V5.39）：自选池全部基金的涨跌榜 / 估值红绿灯 / 技术面与溢价 指标，
 * 供「市场信号」页一次性渲染。纯只读、库内计算，无副作用（不写 signal_record、不触发同步）。
 */
@RestController
@RequestMapping("/api/market-signals")
public class MarketSignalController {

    private final MarketSignalService marketSignalService;

    public MarketSignalController(MarketSignalService marketSignalService) {
        this.marketSignalService = marketSignalService;
    }

    /**
     * 市场信号总览。
     *
     * @param peWindow PE 分位窗口：3y=近3年 / 5y=近5年 / 10y=近10年（默认）/ all=全历史
     */
    @GetMapping("/overview")
    public R<MarketSignalVO> overview(@RequestParam(defaultValue = "10y") String peWindow) {
        return R.ok(marketSignalService.overview(peWindow));
    }
}
