package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.dto.FundScaleHistoryVO;
import com.quant.fund.entity.FundScaleHistory;
import com.quant.fund.mapper.FundScaleHistoryMapper;
import com.quant.fund.service.FundScaleHistoryService;
import org.springframework.stereotype.Service;

/**
 * 基金规模历史实现：每次档案刷新成功后调 {@link #record}（披露口径），
 * 场内 ETF 另由每日任务调 {@link #recordEstimated}（估算口径，V6.15）；
 * 同基金同日只留一行（后写覆盖）；查询按日期升序返回，前端画成阶梯/折线。
 */
@Service
public class FundScaleHistoryServiceImpl implements FundScaleHistoryService {

    /** 口径：定期报告披露值 */
    public static final String SOURCE_DISCLOSED = "DISCLOSED";

    /** 口径：每日估算（场内 ETF：份额 × 当日单位净值） */
    public static final String SOURCE_ESTIMATED = "ESTIMATED";

    private final FundScaleHistoryMapper mapper;

    public FundScaleHistoryServiceImpl(FundScaleHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(String fundCode, BigDecimal scale, LocalDate scaleDate) {
        // 东财未披露规模（极少见）时不落行，避免画出断点
        if (scale == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        FundScaleHistory existing = mapper.selectOne(new LambdaQueryWrapper<FundScaleHistory>()
                .eq(FundScaleHistory::getFundCode, fundCode)
                .eq(FundScaleHistory::getStatDate, today));
        if (existing != null) {
            existing.setFundScale(scale);
            existing.setScaleDate(scaleDate);
            existing.setSource(SOURCE_DISCLOSED);
            mapper.updateById(existing);
            return;
        }
        FundScaleHistory row = new FundScaleHistory();
        row.setFundCode(fundCode);
        row.setStatDate(today);
        row.setFundScale(scale);
        row.setScaleDate(scaleDate);
        row.setSource(SOURCE_DISCLOSED);
        mapper.insert(row);
    }

    @Override
    public void recordEstimated(String fundCode, LocalDate statDate, BigDecimal scale) {
        if (scale == null || statDate == null) {
            return;
        }
        FundScaleHistory existing = mapper.selectOne(new LambdaQueryWrapper<FundScaleHistory>()
                .eq(FundScaleHistory::getFundCode, fundCode)
                .eq(FundScaleHistory::getStatDate, statDate));
        if (existing != null) {
            existing.setFundScale(scale);
            existing.setScaleDate(statDate);
            existing.setSource(SOURCE_ESTIMATED);
            mapper.updateById(existing);
            return;
        }
        FundScaleHistory row = new FundScaleHistory();
        row.setFundCode(fundCode);
        row.setStatDate(statDate);
        row.setFundScale(scale);
        row.setScaleDate(statDate);
        row.setSource(SOURCE_ESTIMATED);
        mapper.insert(row);
    }

    @Override
    public Map<String, EstimatedScale> latestEstimated(Collection<String> fundCodes) {
        Map<String, EstimatedScale> result = new HashMap<>();
        if (fundCodes == null || fundCodes.isEmpty()) {
            return result;
        }
        List<FundScaleHistory> rows = mapper.selectList(new LambdaQueryWrapper<FundScaleHistory>()
                .in(FundScaleHistory::getFundCode, fundCodes)
                .eq(FundScaleHistory::getSource, SOURCE_ESTIMATED)
                .orderByAsc(FundScaleHistory::getStatDate));
        for (FundScaleHistory row : rows) {
            if (row.getFundScale() == null) {
                continue;
            }
            // 升序遍历：同基金越晚的行覆盖越早的，最终留下最新一条
            result.put(row.getFundCode(), new EstimatedScale(row.getFundScale(), row.getStatDate()));
        }
        return result;
    }

    @Override
    public EstimatedScale latestEstimated(String fundCode) {
        return latestEstimated(List.of(fundCode)).get(fundCode);
    }

    @Override
    public List<FundScaleHistoryVO> history(String fundCode) {
        return mapper.selectList(new LambdaQueryWrapper<FundScaleHistory>()
                .eq(FundScaleHistory::getFundCode, fundCode)
                .orderByAsc(FundScaleHistory::getStatDate)).stream()
                .map(row -> new FundScaleHistoryVO(row.getStatDate(), row.getFundScale(),
                        row.getSource() == null ? SOURCE_DISCLOSED : row.getSource()))
                .toList();
    }
}
