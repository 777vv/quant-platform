package com.quant.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.ai.entity.AiUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用量流水 Mapper
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {
}
