package com.quant.fund.service;

import java.util.List;

import com.quant.fund.entity.FundDividend;

/**
 * 基金分红服务：分红记录抓取入库（供行情图分红标识【q】使用）。
 *
 * <p>数据源为东财分红送配页，抓取只发生在同步任务里（档案类每日一次 + 手动同步强制），
 * **绝不放在看图请求路径上**——行情图只读库内已落好的记录，单次接口返回仅日期与金额。
 */
public interface DividendService {

    /** 某只基金的分红记录（按除息日升序） */
    List<FundDividend> listByFund(String fundCode);

    /**
     * 抓取并覆盖该基金的分红记录（按 fund_code + ex_date 幂等）。
     *
     * @param fundCode 基金代码
     * @return 写入的记录数
     */
    int refresh(String fundCode);
}
