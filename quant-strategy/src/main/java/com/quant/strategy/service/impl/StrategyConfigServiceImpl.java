package com.quant.strategy.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.dto.StrategyConfigRequest;
import com.quant.strategy.entity.StrategyConfig;
import com.quant.strategy.mapper.StrategyConfigMapper;
import com.quant.strategy.service.StrategyConfigService;
import org.springframework.stereotype.Service;

/**
 * 策略配置服务实现：每基金每类型唯一；params 保存前经策略校验
 */
@Service
public class StrategyConfigServiceImpl implements StrategyConfigService {

    private final StrategyConfigMapper configMapper;

    private final StrategyRegistry registry;

    public StrategyConfigServiceImpl(StrategyConfigMapper configMapper, StrategyRegistry registry) {
        this.configMapper = configMapper;
        this.registry = registry;
    }

    @Override
    public List<StrategyConfig> listByFund(String fundCode) {
        return configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getFundCode, fundCode).orderByAsc(StrategyConfig::getStrategyType));
    }

    @Override
    public List<StrategyConfig> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .orderByAsc(StrategyConfig::getFundCode).orderByAsc(StrategyConfig::getStrategyType));
    }

    @Override
    public List<StrategyConfig> listEnabled() {
        return configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getEnabled, 1));
    }

    @Override
    public void add(String fundCode, StrategyConfigRequest request) {
        String paramsJson = validate(request);
        if (configMapper.selectCount(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getFundCode, fundCode)
                .eq(StrategyConfig::getStrategyType, request.getStrategyType())) > 0) {
            throw new BizException("该基金已配置此策略类型（每类型限一个）");
        }
        StrategyConfig config = new StrategyConfig();
        config.setFundCode(fundCode);
        config.setStrategyType(request.getStrategyType());
        config.setStrategyName(request.getStrategyName() == null || request.getStrategyName().isBlank()
                ? registry.getRequired(request.getStrategyType()).name() : request.getStrategyName());
        config.setParams(paramsJson);
        config.setRemark(request.getRemark() == null || request.getRemark().isBlank() ? null : request.getRemark().trim());
        config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        configMapper.insert(config);
    }

    @Override
    public void update(Long id, StrategyConfigRequest request) {
        StrategyConfig old = require(id);
        String paramsJson = request.getParams() == null ? old.getParams() : validate(request);
        StrategyConfig config = new StrategyConfig();
        config.setId(id);
        config.setStrategyName(request.getStrategyName() == null || request.getStrategyName().isBlank()
                ? old.getStrategyName() : request.getStrategyName());
        config.setParams(paramsJson);
        // 备注与策略名同语义：null/空白 = 保持原值（编辑时不填备注不应清掉已存的）
        config.setRemark(request.getRemark() == null || request.getRemark().isBlank()
                ? old.getRemark() : request.getRemark().trim());
        config.setEnabled(request.getEnabled() == null ? old.getEnabled() : request.getEnabled());
        configMapper.updateById(config);
    }

    @Override
    public void delete(Long id) {
        require(id);
        configMapper.deleteById(id);
    }

    private String validate(StrategyConfigRequest request) {
        if (request.getStrategyType() == null || request.getStrategyType().isBlank()) {
            throw new BizException("策略类型不能为空");
        }
        String paramsJson = JsonUtils.toJson(request.getParams() == null ? java.util.Map.of() : request.getParams());
        registry.getRequired(request.getStrategyType()).validateParams(JsonUtils.mapper().readTree(paramsJson));
        return paramsJson;
    }

    private StrategyConfig require(Long id) {
        StrategyConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BizException("策略配置不存在");
        }
        return config;
    }
}
