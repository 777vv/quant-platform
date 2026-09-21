package com.quant.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/** AI 模型配置 Mapper */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
