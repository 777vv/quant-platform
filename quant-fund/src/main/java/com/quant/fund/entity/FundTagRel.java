package com.quant.fund.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 基金与标签的关联（多对多）
 */
@Data
@TableName("fund_tag_rel")
public class FundTagRel {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 标签 ID（fund_tag.id） */
    private Long tagId;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;
}
