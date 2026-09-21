package com.quant.fund.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.client.EastmoneyClient;
import com.quant.fund.entity.FundDividend;
import com.quant.fund.mapper.FundDividendMapper;
import com.quant.fund.service.DividendService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基金分红服务实现。
 */
@Service
public class DividendServiceImpl implements DividendService {

    /** 数据来源标记（东财分红送配页） */
    private static final String SOURCE_EASTMONEY_FHSP = "EASTMONEY_FHSP";

    private final FundDividendMapper dividendMapper;

    private final EastmoneyClient client;

    public DividendServiceImpl(FundDividendMapper dividendMapper, EastmoneyClient client) {
        this.dividendMapper = dividendMapper;
        this.client = client;
    }

    @Override
    public List<FundDividend> listByFund(String fundCode) {
        return dividendMapper.selectList(new LambdaQueryWrapper<FundDividend>()
                .eq(FundDividend::getFundCode, fundCode)
                .orderByAsc(FundDividend::getExDate));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refresh(String fundCode) {
        List<EastmoneyClient.DividendItem> items = client.fetchFundDividends(fundCode);
        if (items.isEmpty()) {
            // 抓不到（分红送配页无记录或被抓取封堵）时保留库内已有数据，不清空
            return 0;
        }
        dividendMapper.delete(new LambdaQueryWrapper<FundDividend>().eq(FundDividend::getFundCode, fundCode));
        for (EastmoneyClient.DividendItem item : items) {
            FundDividend dividend = new FundDividend();
            dividend.setFundCode(fundCode);
            dividend.setRecordDate(item.recordDate());
            dividend.setExDate(item.exDate());
            dividend.setPayDate(item.payDate());
            dividend.setPer10Amount(item.per10Amount());
            dividend.setSource(SOURCE_EASTMONEY_FHSP);
            dividendMapper.insert(dividend);
        }
        return items.size();
    }
}
