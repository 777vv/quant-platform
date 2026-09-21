package com.quant.fund.service;

import com.quant.fund.dto.FundCheckVO;
import com.quant.fund.dto.TaskProgressVO;

/**
 * 基金数据导入服务（FR3）
 */
public interface ImportService {

    /** 代码校验：识别 ETF/场外指数基金并返回档案 */
    FundCheckVO check(String fundCode);

    /** 发起历史导入（异步），返回进度 taskId */
    String importFund(String fundCode);

    TaskProgressVO progress(String taskId);
}
