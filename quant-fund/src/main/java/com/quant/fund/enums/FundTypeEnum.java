package com.quant.fund.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 基金类型：1=场内ETF 2=场外指数基金
 */
public enum FundTypeEnum {

    /** 场内ETF */
    ETF(1, "ETF"),

    /** 场外指数基金 */
    OTC(2, "场外指数基金");

    @EnumValue
    private final int code;

    private final String desc;

    FundTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
