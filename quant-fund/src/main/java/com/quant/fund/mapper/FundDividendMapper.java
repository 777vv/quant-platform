package com.quant.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.fund.entity.FundDividend;
import org.apache.ibatis.annotations.Mapper;

/** 基金分红记录 Mapper */
@Mapper
public interface FundDividendMapper extends BaseMapper<FundDividend> {
}
