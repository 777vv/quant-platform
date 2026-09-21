package com.quant.common.spi;

/**
 * 通知收件人提供方 SPI（M4-04）：
 * 通知模块（quant-strategy）需要读取用户资料中的通知邮箱，但不允许反向依赖用户模块（quant-system），
 * 故在 common 定义本接口，由 quant-system 提供实现、Spring 注入到通知服务，完成依赖反转。
 */
public interface MailRecipientProvider {

    /**
     * 返回用户资料中维护的通知邮箱；未维护时返回 null。
     */
    String notifyRecipient();
}
