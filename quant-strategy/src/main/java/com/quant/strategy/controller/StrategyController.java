package com.quant.strategy.controller;

import java.util.List;
import java.util.Map;

import com.quant.common.result.R;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.dto.StrategyConfigRequest;
import com.quant.strategy.entity.StrategyConfig;
import com.quant.strategy.service.StrategyConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 策略配置接口（FR2）
 */
@RestController
@RequestMapping("/api")
public class StrategyController {

    private final StrategyConfigService configService;

    private final StrategyRegistry registry;

    public StrategyController(StrategyConfigService configService, StrategyRegistry registry) {
        this.configService = configService;
        this.registry = registry;
    }

    /** 可用策略类型（前端动态表单依据） */
    @GetMapping("/strategies/types")
    public R<List<Map<String, String>>> types() {
        return R.ok(registry.all().values().stream()
                .map(strategy -> Map.of("type", strategy.type(), "name", strategy.name()))
                .toList());
    }

    /** 某基金已配置的策略列表 */
    @GetMapping("/funds/{code}/strategies")
    public R<List<StrategyConfig>> listByFund(@PathVariable String code) {
        return R.ok(configService.listByFund(code));
    }

    /**
     * 全部策略配置（自选列表批量展示"已配置策略"列用，避免逐只基金请求）。
     */
    @GetMapping("/strategies/configs")
    public R<List<StrategyConfig>> listAll() {
        return R.ok(configService.listAll());
    }

    /** 新增策略配置（每基金每类型唯一，参数经策略校验） */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_STRATEGY)
    @PostMapping("/funds/{code}/strategies")
    public R<Void> add(@PathVariable String code, @RequestBody StrategyConfigRequest request) {
        configService.add(code, request);
        return R.ok();
    }

    /** 修改策略参数/启停状态 */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_STRATEGY)
    @PutMapping("/strategies/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody StrategyConfigRequest request) {
        configService.update(id, request);
        return R.ok();
    }

    /** 删除策略配置 */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_STRATEGY)
    @DeleteMapping("/strategies/{id}")
    public R<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return R.ok();
    }
}
