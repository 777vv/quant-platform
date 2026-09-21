package com.quant.strategy.service;

import java.util.List;

import com.quant.strategy.dto.StrategyConfigRequest;
import com.quant.strategy.entity.StrategyConfig;

/**
 * 策略配置服务
 */
public interface StrategyConfigService {

    /** 某基金已配置的策略（按类型升序） */
    List<StrategyConfig> listByFund(String fundCode);

    /** 全部策略配置（自选列表批量展示"已配置策略"列用，避免逐只基金请求） */
    List<StrategyConfig> listAll();

    /** 已启用的策略配置（每日信号计算依据） */
    List<StrategyConfig> listEnabled();

    void add(String fundCode, StrategyConfigRequest request);

    void update(Long id, StrategyConfigRequest request);

    void delete(Long id);
}
