package com.quant.fund.service;

import com.quant.fund.dto.AllocationCheckVO;
import com.quant.fund.dto.AllocationConfigRequest;
import com.quant.fund.entity.AllocationConfig;

/**
 * 全局仓位配置与检查（V5.36）：平台配置维护五类资产的目标占比范围，
 * 仓位检查（每周二 09:00 任务 / 手动触发）把当前组合与之比对。
 *
 * <p>比例口径：总资产 = 现金余额 + 全部持仓市值；
 * 现金桶 = 现金余额 + 标签「现金」的持仓市值；A/美/亚太/欧洲桶 = 打了对应标签的持仓市值。
 * 一只基金打多个类别标签会重复计入对应桶（配置标签时注意别重叠）。
 */
public interface AllocationService {

    /** 当前配置（单行；缺行时返回带默认值的空壳：启用、全类别 0~100） */
    AllocationConfig config();

    /**
     * 保存配置（单行 upsert，保存即生效）。
     *
     * @param request 五组比例与启用开关（min ≤ max 由校验保证）
     */
    void save(AllocationConfigRequest request);

    /**
     * 执行一次检查：算当前占比并与配置范围比对。
     * 只算不报——是否发送通知由调用方（任务/手动接口）根据违规行决定。
     *
     * @return 检查结果（evaluated=false 表示总资产 ≤ 0 无法评估）
     */
    AllocationCheckVO check();
}
