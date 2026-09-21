package com.quant.fund.service;

import java.util.List;

import com.quant.fund.dto.FundMarkVO;

/**
 * 行情图交易标记服务（买入/卖出/分红）。
 *
 * <p>数据来源：买入卖出取本系统交易流水（用户自己的操作），分红取**东财分红送配页落库的除息日**
 * （基金层面的事件，含未持仓期，按用户确认口径）。
 * 接口只返回"日期 + 类型 + 一句话文案"，单只基金十年也就几十条，前端一次取回本地渲染，
 * 不参与 K 线数据请求，也不会在缩放/框选时重复请求。
 */
public interface FundMarkService {

    /**
     * 某只基金的全部交易标记（按日期升序；同一天同类型合并）。
     *
     * @param fundCode 基金代码
     */
    List<FundMarkVO> list(String fundCode);
}
