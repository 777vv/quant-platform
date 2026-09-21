package com.quant.fund.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 基金标签（预定义标签库，用户维护；贴标签见 fund_tag_rel）
 */
@Data
@TableName("fund_tag")
public class FundTag {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名（唯一，如 红利/宽基/债券） */
    private String name;

    /** 排序号（小者靠前，用于标签展示与筛选顺序） */
    private Integer sortNo;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
