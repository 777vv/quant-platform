package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.dto.AllocationCheckVO;
import com.quant.fund.dto.AllocationConfigRequest;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.entity.AllocationConfig;
import com.quant.fund.mapper.AllocationConfigMapper;
import com.quant.fund.service.AllocationService;
import com.quant.fund.service.CashAccountingService;
import com.quant.fund.service.FundTagService;
import com.quant.fund.service.TradeService;
import org.springframework.stereotype.Service;

/**
 * 全局仓位配置实现（V5.36）：单行 upsert + 五类占比检查。
 *
 * <p>分类靠**基金标签名精确匹配**（现金 / A股 / 美股 / 亚太 / 欧洲），
 * 标签在【基金池 → 标签库】维护；一只基金打了多个类别标签会重复计入对应桶。
 * 现金桶额外加现金余额（记账模块的 转入−转出−净投入）。
 * 全部金额/比例保留 2 位；总资产 ≤ 0 时 evaluated=false（比例无从谈起，调用方不告警）。
 */
@Service
public class AllocationServiceImpl implements AllocationService {

    /** 单行配置固定主键 */
    private static final Long CONFIG_ID = 1L;

    /** 现金桶下标（须与 CATEGORIES 第一项一致）：现金桶额外并入记账模块的现金余额 */
    private static final int CASH_INDEX = 0;

    /** 类别定义：key → 中文名 + 对应标签名（标签名与 key 顺序一致） */
    private static final String[][] CATEGORIES = {
            {"cash", "现金", "现金"},
            {"a_share", "A股", "A股"},
            {"us", "美股", "美股"},
            {"asia", "亚太", "亚太"},
            {"eu", "欧洲", "欧洲"},
    };

    private final AllocationConfigMapper configMapper;

    private final TradeService tradeService;

    private final CashAccountingService cashAccountingService;

    private final FundTagService fundTagService;

    public AllocationServiceImpl(AllocationConfigMapper configMapper, TradeService tradeService,
                                 CashAccountingService cashAccountingService, FundTagService fundTagService) {
        this.configMapper = configMapper;
        this.tradeService = tradeService;
        this.cashAccountingService = cashAccountingService;
        this.fundTagService = fundTagService;
    }

    @Override
    public AllocationConfig config() {
        AllocationConfig config = configMapper.selectById(CONFIG_ID);
        if (config == null) {
            config = defaultShell();
        }
        return config;
    }

    @Override
    public void save(AllocationConfigRequest request) {
        validate(request);
        AllocationConfig old = configMapper.selectById(CONFIG_ID);
        AllocationConfig config = new AllocationConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(request.enabled() == null || request.enabled() ? 1 : 0);
        config.setCashMin(request.cashMin());
        config.setCashMax(request.cashMax());
        config.setAShareMin(request.aShareMin());
        config.setAShareMax(request.aShareMax());
        config.setUsMin(request.usMin());
        config.setUsMax(request.usMax());
        config.setAsiaMin(request.asiaMin());
        config.setAsiaMax(request.asiaMax());
        config.setEuMin(request.euMin());
        config.setEuMax(request.euMax());
        config.setUpdatedAt(LocalDateTime.now());
        if (old == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
    }

    @Override
    public AllocationCheckVO check() {
        AllocationConfig config = config();
        BigDecimal cash = nz(cashAccountingService.cashBalance());
        List<HoldingVO> holdings = tradeService.holdings();

        // 分母是全部资产：现金余额 + 全部持仓市值（含已打类别标签的基金，也含清仓后市值 0 的行）
        BigDecimal holdingsValue = BigDecimal.ZERO;
        for (HoldingVO holding : holdings) {
            holdingsValue = holdingsValue.add(nvl(holding.marketValue()));
        }
        BigDecimal totalAssets = cash.add(holdingsValue);
        // 市值先按基金汇总一次（一份市值可以同时计入多个类别桶——如果用户给同一只基金打了多个类别标签）
        BigDecimal[] buckets = new BigDecimal[CATEGORIES.length];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = BigDecimal.ZERO;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            // 每类一次查询：该标签下的全部基金代码（标签不存在 → 空列表 = 桶为 0）
            java.util.Set<String> codes = new java.util.HashSet<>(
                    fundTagService.fundCodesOfTag(CATEGORIES[i][2]));
            for (HoldingVO holding : holdings) {
                if (codes.contains(holding.fundCode())) {
                    buckets[i] = buckets[i].add(nvl(holding.marketValue()));
                }
            }
        }

        // 现金桶额外并入现金余额（口径：现金 = 现金余额 + 打了「现金」标签的基金，如债基）
        buckets[CASH_INDEX] = buckets[CASH_INDEX].add(cash);

        List<AllocationCheckVO.Row> rows = new ArrayList<>(CATEGORIES.length);
        boolean evaluated = totalAssets.compareTo(BigDecimal.ZERO) > 0;
        for (int i = 0; i < CATEGORIES.length; i++) {
            BigDecimal min = minOf(config, i);
            BigDecimal max = maxOf(config, i);
            BigDecimal pct = evaluated
                    ? buckets[i].multiply(BigDecimal.valueOf(100)).divide(totalAssets, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            boolean ok = pct.compareTo(min) >= 0 && pct.compareTo(max) <= 0;
            rows.add(new AllocationCheckVO.Row(CATEGORIES[i][0], CATEGORIES[i][1], buckets[i], pct, min, max, ok));
        }
        return new AllocationCheckVO(evaluated, totalAssets.setScale(2, RoundingMode.HALF_UP),
                cash.setScale(2, RoundingMode.HALF_UP), rows);
    }

    /** null 归一为零 */
    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 保存校验：每组 0 ≤ min ≤ max ≤ 100 */
    private void validate(AllocationConfigRequest r) {
        requireRange("现金", r.cashMin(), r.cashMax());
        requireRange("A股", r.aShareMin(), r.aShareMax());
        requireRange("美股", r.usMin(), r.usMax());
        requireRange("亚太", r.asiaMin(), r.asiaMax());
        requireRange("欧洲", r.euMin(), r.euMax());
    }

    private void requireRange(String label, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            throw new com.quant.common.exception.BizException(label + "比例的下限与上限都要填");
        }
        if (min.compareTo(BigDecimal.ZERO) < 0 || min.compareTo(BigDecimal.valueOf(100)) > 0
                || max.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new com.quant.common.exception.BizException(label + "比例须在 0~100 之间");
        }
        if (min.compareTo(max) > 0) {
            throw new com.quant.common.exception.BizException(label + "比例的下限不能大于上限（当前 " + min + " > " + max + "）");
        }
    }

    private BigDecimal minOf(AllocationConfig config, int categoryIndex) {
        return switch (categoryIndex) {
            case 0 -> nvl(config.getCashMin());
            case 1 -> nvl(config.getAShareMin());
            case 2 -> nvl(config.getUsMin());
            case 3 -> nvl(config.getAsiaMin());
            default -> nvl(config.getEuMin());
        };
    }

    private BigDecimal maxOf(AllocationConfig config, int categoryIndex) {
        return switch (categoryIndex) {
            case 0 -> nvl(config.getCashMax());
            case 1 -> nvl(config.getAShareMax());
            case 2 -> nvl(config.getUsMax());
            case 3 -> nvl(config.getAsiaMax());
            default -> nvl(config.getEuMax());
        };
    }

    /** 缺行时的空壳：启用、全类别 0~100（永不告警，等用户按需收紧） */
    private AllocationConfig defaultShell() {
        AllocationConfig config = new AllocationConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(1);
        config.setCashMin(BigDecimal.ZERO);
        config.setCashMax(BigDecimal.valueOf(100));
        config.setAShareMin(BigDecimal.ZERO);
        config.setAShareMax(BigDecimal.valueOf(100));
        config.setUsMin(BigDecimal.ZERO);
        config.setUsMax(BigDecimal.valueOf(100));
        config.setAsiaMin(BigDecimal.ZERO);
        config.setAsiaMax(BigDecimal.valueOf(100));
        config.setEuMin(BigDecimal.ZERO);
        config.setEuMax(BigDecimal.valueOf(100));
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }

    /** null 归一为零 */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
