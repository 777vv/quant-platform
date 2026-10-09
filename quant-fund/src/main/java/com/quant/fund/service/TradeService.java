package com.quant.fund.service;

import java.time.LocalDate;
import java.util.List;

import com.quant.common.result.PageResult;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.TradeFlowRequest;
import com.quant.fund.entity.TradeFlow;

/**
 * 交易流水与持仓服务
 */
public interface TradeService {

    /**
     * 分页查询交易流水（按交易日期、主键倒序），全部筛选条件可选。
     *
     * @param fundCode  基金代码；为空表示不限（账户级划转没有基金，只能通过类型筛选）
     * @param tradeType 交易类型（1 买入 / 2 卖出 / 3 分红 / 4 转入 / 5 转出），为空表示不限
     * @param startDate 交易日期下限（含），为空表示不限
     * @param endDate   交易日期上限（含），为空表示不限
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @return 分页结果（total 为满足条件的总条数）
     */
    PageResult<TradeFlow> page(String fundCode, String keyword, Integer tradeType,
            LocalDate startDate, LocalDate endDate, long page, long size);

    void add(TradeFlowRequest request);

    void update(Long id, TradeFlowRequest request);

    void delete(Long id);

    /** 流水变更后重算持仓（份额/摊薄成本/已实现盈亏） */
    void recalcPosition(String fundCode);

    List<HoldingVO> holdings();
}
