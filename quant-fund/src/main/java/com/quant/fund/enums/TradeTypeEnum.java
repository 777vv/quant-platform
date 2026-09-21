package com.quant.fund.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 交易类型：1=买入/申购 2=卖出/赎回 3=分红 4=资金转入 5=资金转出
 * 转入/转出为账户级现金流（fund_code 为空），用于现金余额与总资产核算。
 */
public enum TradeTypeEnum {

    /** 买入/申购 */
    BUY(1, "买入"),

    /** 卖出/赎回 */
    SELL(2, "卖出"),

    /** 分红 */
    DIVIDEND(3, "分红"),

    /** 资金转入（银证转账入金，现金增加） */
    TRANSFER_IN(4, "转入"),

    /** 资金转出（银证转账出金，现金减少） */
    TRANSFER_OUT(5, "转出");

    @EnumValue
    private final int code;

    private final String desc;

    TradeTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static TradeTypeEnum of(Integer code) {
        for (TradeTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知交易类型: " + code);
    }
}
