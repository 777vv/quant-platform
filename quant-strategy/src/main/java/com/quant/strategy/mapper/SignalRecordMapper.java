package com.quant.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.strategy.entity.SignalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信号记录 Mapper
 */
@Mapper
public interface SignalRecordMapper extends BaseMapper<SignalRecord> {
}
