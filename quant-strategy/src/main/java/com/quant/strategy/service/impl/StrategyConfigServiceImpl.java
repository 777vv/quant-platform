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
        String paramsJson = validate(request.getStrategyType(), request.getParams());
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

    /**
     * 修改策略参数/启停/备注/展示名。类型是 (fund_code, strategy_type) 唯一键的一半、**不可改**：
     * 请求体不传类型 = 沿用库里已存类型（前端编辑弹框的类型下拉是禁用的，契约上就没有这个字段）；
     * 传了但与已存不一致 → 明确报错（而不是静默忽略，避免调用方以为改成功了）。
     * params 校验一律按**已存类型**执行——V5.38 补踩过：update 复用新增的校验、强制要求请求体带类型，
     * 导致编辑弹框保存必报「策略类型不能为空」。
     */
    @Override
    public void update(Long id, StrategyConfigRequest request) {
        StrategyConfig old = require(id);
        if (request.getStrategyType() != null && !request.getStrategyType().isBlank()
                && !request.getStrategyType().equals(old.getStrategyType())) {
            throw new BizException("策略类型不允许修改（如需更换类型请删除后重新新增）");
        }
        String paramsJson = request.getParams() == null
                ? old.getParams()
                : validateParams(old.getStrategyType(), request.getParams());
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

    /** 新增校验：类型必填 + 参数经该策略校验 */
    private String validate(String strategyType, java.util.Map<String, Object> params) {
        if (strategyType == null || strategyType.isBlank()) {
            throw new BizException("策略类型不能为空");
        }
        return validateParams(strategyType, params);
    }

    /** 按指定类型校验参数并返回序列化 JSON（类型由调用方保证非空） */
    private String validateParams(String strategyType, java.util.Map<String, Object> params) {
        String paramsJson = JsonUtils.toJson(params == null ? java.util.Map.of() : params);
        registry.getRequired(strategyType).validateParams(JsonUtils.mapper().readTree(paramsJson));
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
