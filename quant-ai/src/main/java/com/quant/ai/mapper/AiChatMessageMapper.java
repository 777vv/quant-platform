package com.quant.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话消息 Mapper
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
