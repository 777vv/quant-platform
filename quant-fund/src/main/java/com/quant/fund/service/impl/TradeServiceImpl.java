package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.LastQuote;
import com.quant.fund.dto.TradeFlowRequest;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundPosition;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.enums.TradeTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundPositionMapper;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.fund.service.CashAccountingService;
import com.quant.fund.service.FundQueryService;
import com.quant.fund.service.TradeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易流水与持仓服务实现。
 * 成本口径：摊薄成本；SELL 按卖出时点的摊薄成本结转；DIVIDEND 视为现金分红直接计入已实现并冲减成本。
 */
@Service
public class TradeServiceImpl implements TradeService {

    private final TradeFlowMapper tradeFlowMapper;

    private final CashAccountingService cashAccountingService;

    private final FundPositionMapper positionMapper;

    private final FundBasicMapper fundBasicMapper;

    private final FundQueryService fundQueryService;

    public TradeServiceImpl(TradeFlowMapper tradeFlowMapper, FundPositionMapper positionMapper,
                            FundBasicMapper fundBasicMapper, FundQueryService fundQueryService,
                            CashAccountingService cashAccountingService) {
        this.tradeFlowMapper = tradeFlowMapper;
        this.positionMapper = positionMapper;
        this.fundBasicMapper = fundBasicMapper;
        this.fundQueryService = fundQueryService;
        this.cashAccountingService = cashAccountingService;
    }

    @Override
    public PageResult<TradeFlow> page(String fundCode, String keyword, Integer tradeType, LocalDate startDate,
            LocalDate endDate, long page, long size) {
        // 关键词模糊查询（V6.02 用户口径）：按"基金代码或基金名称"模糊匹配——先把命中的基金代码捞出来，
        // 再按 fund_code IN 过滤流水（trade_flow 只存代码，名称在 fund_basic）；
        // 与精确 fundCode 参数并存：基金详情页要的是"这一只"的精确流水。
        List<String> keywordCodes = null;
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            keywordCodes = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                            .and(w -> w.like(FundBasic::getFundCode, kw).or().like(FundBasic::getFundName, kw)))
                    .stream().map(FundBasic::getFundCode).toList();
            if (keywordCodes.isEmpty()) {
                // 没命中任何基金：直接返回空页（避免 IN () 这种非法 SQL）
                return PageResult.of(0, List.of());
            }
        }
        IPage<TradeFlow> result = tradeFlowMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TradeFlow>()
                        .eq(fundCode != null && !fundCode.isBlank(), TradeFlow::getFundCode, fundCode)
                        .in(keywordCodes != null, TradeFlow::getFundCode, keywordCodes)
                        .eq(tradeType != null, TradeFlow::getTradeType, tradeType)
                        .ge(startDate != null, TradeFlow::getTradeDate, startDate)
                        .le(endDate != null, TradeFlow::getTradeDate, endDate)
                        .orderByDesc(TradeFlow::getTradeDate).orderByDesc(TradeFlow::getId));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(TradeFlowRequest request) {
        TradeTypeEnum type = TradeTypeEnum.of(request.getTradeType());
        validateAndNormalize(request, type);
        validateTransferOutLimit(request, null);
        tradeFlowMapper.insert(toEntity(null, request));
        if (!isCashType(type)) {
            recalcPosition(request.getFundCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TradeFlowRequest request) {
        TradeFlow old = tradeFlowMapper.selectById(id);
        if (old == null) {
            throw new BizException("流水不存在");
        }
        TradeTypeEnum type = TradeTypeEnum.of(request.getTradeType());
        validateAndNormalize(request, type);
        validateTransferOutLimit(request, old);
        tradeFlowMapper.updateById(toEntity(id, request));
        // 仅基金交易影响持仓：老/新归属基金都需要重算（类型或基金改动的场景）
        if (old.getFundCode() != null) {
            recalcPosition(old.getFundCode());
        }
        if (!isCashType(type) && request.getFundCode() != null && !request.getFundCode().equals(old.getFundCode())) {
            recalcPosition(request.getFundCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TradeFlow old = tradeFlowMapper.selectById(id);
        if (old == null) {
            throw new BizException("流水不存在");
        }
        tradeFlowMapper.deleteById(id);
        if (old.getFundCode() != null) {
            recalcPosition(old.getFundCode());
        }
    }

    /**
     * 转出额度校验：**转出金额不得超过当前现金余额**（现金 = 净转入 − 净投入）。
     *
     * <p>编辑既有流水时必须把这笔记录本身排除在"已发生"之外，
     * 否则把一笔已存在的转出改小/改大都可能被自己挡住：
     * 原来是转出则可用额度要加回原金额，原来是转入则要扣掉原金额（等同撤销那一笔）。
     *
     * @param request 本次提交的流水
     * @param old     库中原记录（新增时为 null）
     */
    private void validateTransferOutLimit(TradeFlowRequest request, TradeFlow old) {
        if (TradeTypeEnum.of(request.getTradeType()) != TradeTypeEnum.TRANSFER_OUT) {
            return;
        }
        BigDecimal available = cashAccountingService.transferableLimit();
        if (old != null && old.getAmount() != null) {
            TradeTypeEnum oldType = TradeTypeEnum.of(old.getTradeType());
            if (oldType == TradeTypeEnum.TRANSFER_OUT) {
                available = available.add(old.getAmount());
            } else if (oldType == TradeTypeEnum.TRANSFER_IN) {
                available = available.subtract(old.getAmount());
            }
        }
        if (request.getAmount().compareTo(available) > 0) {
            throw new BizException("转出金额不可超过当前现金余额（可转出 "
                    + available.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP) + " 元）");
        }
    }

    /** 是否账户级资金划转（转入/转出，不关联基金、不触发持仓重算） */
    private boolean isCashType(TradeTypeEnum type) {
        return type == TradeTypeEnum.TRANSFER_IN || type == TradeTypeEnum.TRANSFER_OUT;
    }

    /**
     * 按交易类型做入参校验并规范化。三类规则：
     * <ul>
     *   <li>资金划转（转入/转出）：不关联基金，仅要求金额 &gt; 0，价/份额强制归零；</li>
     *   <li>分红：关联基金，**只要求金额 &gt; 0**（现金分红，税前），价/份额强制归零——
     *       分红没有"成交价/份额"概念，前端也不会提交这两个字段；</li>
     *   <li>基金交易（买入/卖出）：要求基金代码、价格、份额、金额齐全且均 &gt; 0。</li>
     * </ul>
     * ⚠️ 历史缺陷：早期实现把分红并入"基金交易"分支校验，要求价格 &gt; 0，而前端按设计提交 0，
     * 导致分红录入必然被拒（"价格须大于 0"），现已按类型分支修正。
     */
    private void validateAndNormalize(TradeFlowRequest request, TradeTypeEnum type) {
        if (isCashType(type)) {
            request.setFundCode(null);
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("划转金额须大于 0");
            }
            request.setPrice(BigDecimal.ZERO);
            request.setShare(BigDecimal.ZERO);
            return;
        }
        if (request.getFundCode() == null || request.getFundCode().isBlank()) {
            throw new BizException("基金代码不能为空");
        }
        validateFund(request.getFundCode());
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(type == TradeTypeEnum.DIVIDEND ? "分红金额须大于 0" : "金额须大于 0");
        }
        if (type == TradeTypeEnum.DIVIDEND) {
            // 现金分红：不消费价格与份额，写入 0 以免脏数据影响持仓重算
            request.setPrice(BigDecimal.ZERO);
            request.setShare(BigDecimal.ZERO);
            return;
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("价格须大于 0");
        }
        if (request.getShare() == null || request.getShare().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("份额须大于 0");
        }
    }

    @Override
    public void recalcPosition(String fundCode) {
        List<TradeFlow> flows = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                .eq(TradeFlow::getFundCode, fundCode)
                .orderByAsc(TradeFlow::getTradeDate).orderByAsc(TradeFlow::getId));
        BigDecimal share = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        for (TradeFlow flow : flows) {
            BigDecimal amount = nvl(flow.getAmount());
            BigDecimal fee = nvl(flow.getFee());
            BigDecimal flowShare = nvl(flow.getShare());
            TradeTypeEnum type = TradeTypeEnum.of(flow.getTradeType());
            switch (type) {
                case BUY -> {
                    share = share.add(flowShare);
                    cost = cost.add(amount).add(fee);
                }
                case SELL -> {
                    BigDecimal avg = share.compareTo(BigDecimal.ZERO) > 0
                            ? cost.divide(share, 6, java.math.RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    BigDecimal costOf = avg.multiply(flowShare).min(cost);
                    realized = realized.add(amount).subtract(fee).subtract(costOf);
                    share = share.subtract(flowShare).max(BigDecimal.ZERO);
                    cost = cost.subtract(costOf);
                    if (share.compareTo(BigDecimal.ZERO) == 0) {
                        cost = BigDecimal.ZERO;
                    }
                }
                case DIVIDEND -> {
                    BigDecimal income = amount.subtract(fee);
                    realized = realized.add(income);
                    cost = cost.subtract(income).max(BigDecimal.ZERO);
                }
                default -> {
                    // 不会发生
                }
            }
        }
        FundPosition position = positionMapper.selectOne(
                new LambdaQueryWrapper<FundPosition>().eq(FundPosition::getFundCode, fundCode));
        if (position == null) {
            position = new FundPosition();
            position.setFundCode(fundCode);
        }
        position.setTotalShare(share.setScale(2, java.math.RoundingMode.HALF_UP));
        position.setTotalCost(cost.setScale(2, java.math.RoundingMode.HALF_UP));
        position.setAvgCostPrice(share.compareTo(BigDecimal.ZERO) > 0
                ? cost.divide(share, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        position.setRealizedPnl(realized.setScale(2, java.math.RoundingMode.HALF_UP));
        if (position.getId() == null) {
            positionMapper.insert(position);
        } else {
            positionMapper.updateById(position);
        }
    }

    @Override
    public List<HoldingVO> holdings() {
        List<FundPosition> positions = positionMapper.selectList(
                new LambdaQueryWrapper<FundPosition>().orderByAsc(FundPosition::getFundCode));
        List<HoldingVO> items = new ArrayList<>();
        for (FundPosition position : positions) {
            FundBasic fund = fundBasicMapper.selectOne(new LambdaQueryWrapper<FundBasic>()
                    .eq(FundBasic::getFundCode, position.getFundCode()));
            if (fund == null || fund.getStatus() != 1) {
                continue;
            }
            // 跳过"空壳持仓行"：份额为 0 且**没有任何资金痕迹**（已实现收益与总成本都为 0）——
            // 例如流水被全部删除后重算留下的行。注意"全部卖出"的行必须保留（用户 V5.35 口径）：
            // 卖光后份额=0、市值=0，但已实现收益/成本有值，是真实的投资历史。
            if (nvl(position.getTotalShare()).compareTo(BigDecimal.ZERO) == 0
                    && nvl(position.getRealizedPnl()).compareTo(BigDecimal.ZERO) == 0
                    && nvl(position.getTotalCost()).compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            LastQuote quote = fundQueryService.lastQuote(fund);
            BigDecimal price = quote == null ? null : quote.price();
            BigDecimal share = position.getTotalShare();
            BigDecimal marketValue = price == null ? null : price.multiply(share).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal floating = marketValue == null ? null
                    : marketValue.subtract(position.getTotalCost()).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal floatingPct = floating == null || position.getTotalCost().compareTo(BigDecimal.ZERO) == 0 ? null
                    : floating.multiply(BigDecimal.valueOf(100)).divide(position.getTotalCost(), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal dayPnl = quote == null || quote.prevPrice() == null || price == null ? null
                    : price.subtract(quote.prevPrice()).multiply(share).setScale(2, java.math.RoundingMode.HALF_UP);
            String typeDesc = FundTypeEnum.ETF.getCode() == fund.getFundType()
                    ? FundTypeEnum.ETF.getDesc() : FundTypeEnum.OTC.getDesc();
            items.add(new HoldingVO(position.getFundCode(), fund.getFundName(), fund.getFundType(), typeDesc,
                    share, position.getAvgCostPrice(), price, marketValue, dayPnl,
                    floating, floatingPct, position.getRealizedPnl()));
        }
        return items;
    }

    private void validateFund(String fundCode) {
        if (fundBasicMapper.selectCount(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getFundCode, fundCode).eq(FundBasic::getStatus, 1)) == 0) {
            throw new BizException("基金不在自选池: " + fundCode);
        }
    }

    private TradeFlow toEntity(Long id, TradeFlowRequest request) {
        TradeFlow flow = new TradeFlow();
        flow.setId(id);
        flow.setFundCode(request.getFundCode());
        flow.setTradeType(request.getTradeType());
        flow.setTradeDate(request.getTradeDate());
        flow.setPrice(request.getPrice());
        flow.setShare(request.getShare());
        flow.setAmount(request.getAmount());
        flow.setFee(request.getFee());
        flow.setNote(request.getNote());
        return flow;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
