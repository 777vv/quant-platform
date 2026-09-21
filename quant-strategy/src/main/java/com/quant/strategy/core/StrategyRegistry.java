package com.quant.strategy.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.quant.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 策略注册中心：Spring 注入收集全部 Strategy 实现
 */
@Component
public class StrategyRegistry {

    private final Map<String, Strategy> strategyMap = new HashMap<>();

    public StrategyRegistry(Collection<Strategy> strategies) {
        for (Strategy strategy : strategies) {
            strategyMap.put(strategy.type(), strategy);
        }
    }

    public Strategy getRequired(String type) {
        Strategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new BizException("未知策略类型: " + type);
        }
        return strategy;
    }

    public Map<String, Strategy> all() {
        return strategyMap;
    }
}
