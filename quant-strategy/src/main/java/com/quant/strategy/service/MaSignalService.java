package com.quant.strategy.service;

import com.quant.common.result.PageResult;
import com.quant.strategy.dto.MaSignalItemVO;

import java.util.List;

/**
 * 均线信号服务（V5.70）：从 fund_ma_daily 判定短期均线上穿/下穿长期均线并记录信号。
 * 均线对为 5/10/20/30/60/90/120/250 日中的两两组合（28 对）。
 */
public interface MaSignalService {

    /**
     * 增量判定：取 fund_ma_daily 每只基金最近两个交易日数据，两两比较均线排序变化，
     * 排序翻转即为上穿/下穿。幂等（已存在的 信号日期+均线对 不重复入库）。
     *
     * @return 本次新生成的信号条数
     */
    int computeFromMaDaily();

    /** 均线信号分页（新→旧），可按基金代码过滤；行内已补基金名称与信号描述 */
    PageResult<MaSignalItemVO> page(String fundCode, int page, int size);

    /** 某基金的全部均线信号（新→旧，基金详情信号页用） */
    List<MaSignalItemVO> listByFund(String fundCode);
}
