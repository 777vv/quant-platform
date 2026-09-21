package com.quant.fund.service;

import java.util.List;

import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.dto.IndexBoardVO;
import com.quant.fund.dto.IndexQuoteVO;

/**
 * 仪表盘聚合服务（FR1，M4-06/07/08）：组装指数看板与速览区数据
 */
public interface DashboardService {

    /**
     * 全球指数看板：最新行情 + 盘中走势采样点（迷你线）。
     */
    List<IndexQuoteVO> indices();

    /**
     * 指数看板（含数据源降级标记）：degraded=true 表示本次行情来自库内快照（外部拉取失败）或存在迷你线缺失。
     */
    IndexBoardVO indexBoard();

    /**
     * 强制刷新指数行情与迷你线（页面"刷新"按钮）：同步执行外部拉取后由 indexBoard 返回新数据。
     */
    void forceRefreshIndices();

    /**
     * 速览区：持仓概览（前 5）/ 自选 7 日涨跌榜 / 配置占比 / 同步状态。
     */
    DashboardOverviewVO overview();
}
