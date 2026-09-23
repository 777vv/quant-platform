package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.dto.FundScaleHistoryVO;
import com.quant.fund.entity.FundScaleHistory;
import com.quant.fund.mapper.FundScaleHistoryMapper;
import com.quant.fund.service.FundScaleHistoryService;
import org.springframework.stereotype.Service;

/**
 * 基金规模历史实现：每次档案刷新成功后调 {@link #record}，同基金同日只留一行（后写覆盖）；
 * 查询按日期升序返回，前端画成阶梯线即可。
 */
@Service
public class FundScaleHistoryServiceImpl implements FundScaleHistoryService {

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
            mapper.updateById(existing);
            return;
        }
        FundScaleHistory row = new FundScaleHistory();
        row.setFundCode(fundCode);
        row.setStatDate(today);
        row.setFundScale(scale);
        row.setScaleDate(scaleDate);
        mapper.insert(row);
    }

    @Override
    public List<FundScaleHistoryVO> history(String fundCode) {
        return mapper.selectList(new LambdaQueryWrapper<FundScaleHistory>()
                .eq(FundScaleHistory::getFundCode, fundCode)
                .orderByAsc(FundScaleHistory::getStatDate)).stream()
                .map(row -> new FundScaleHistoryVO(row.getStatDate(), row.getFundScale()))
                .toList();
    }
}
