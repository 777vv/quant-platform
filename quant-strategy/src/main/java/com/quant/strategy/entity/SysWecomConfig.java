package com.quant.strategy.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 微信通知配置（V5.20，企业微信自建应用 + 微信插件 → 消息直达个人微信；单行配置）。
 * Secret 明文存本机库（与邮件授权码同一约定），接口打码、留空不覆盖。
 */
@Data
@TableName("sys_wecom_config")
public class SysWecomConfig {

    /** 固定主键（单行配置，值恒为 1） */
    @TableId
    private Long id;

    /** 微信通知总开关：1 启用（交易信号推送），0 关闭 */
    private Integer enabled;

    /** 企业 ID（企业微信后台-我的企业） */
    private String corpid;

    /** 自建应用的 AgentId */
    private String agentId;

    /** 自建应用的 Secret（明文存本机库；接口打码） */
    private String secret;

    /** 接收人（企业微信 userid，多个用 | 分隔；@all=全员） */
    private String touser;

    /** 最近修改时间 */
    private LocalDateTime updatedAt;
}
