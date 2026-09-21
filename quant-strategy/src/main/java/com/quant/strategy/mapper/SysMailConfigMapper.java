package com.quant.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quant.strategy.entity.SysMailConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮件通知配置 Mapper（单行）
 */
@Mapper
public interface SysMailConfigMapper extends BaseMapper<SysMailConfig> {
}
