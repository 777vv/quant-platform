package com.quant.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.ai.entity.AiRuntimeConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 运行时配置 Mapper（单行：当前启用厂商 + 全局额度）
 */
@Mapper
public interface AiRuntimeConfigMapper extends BaseMapper<AiRuntimeConfig> {
}
