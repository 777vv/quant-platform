package com.quant.fund.service;

import java.util.List;
import java.util.Map;

import com.quant.fund.dto.FundTagVO;

/**
 * 基金标签服务（预定义标签库）：标签库维护 + 基金贴标签 + 批量查询
 */
public interface FundTagService {

    /**
     * 标签库列表（含每个标签下的基金数量，按 sortNo 升序）。
     */
    List<FundTagVO> listLibrary();

    /**
     * 新建标签（名称唯一，重复报业务异常）。
     *
     * @param name 标签名
     */
    void createTag(String name);

    /**
     * 重命名标签（保持关联不变）。
     *
     * @param id   标签 ID
     * @param name 新标签名
     */
    void renameTag(Long id, String name);

    /**
     * 删除标签（同时清理其与基金的关联，二次确认由前端负责）。
     *
     * @param id 标签 ID
     */
    void deleteTag(Long id);

    /**
     * 某只基金的标签。
     *
     * @param fundCode 基金代码
     */
    List<FundTagVO> tagsOfFund(String fundCode);

    /**
     * 覆盖式设置某只基金的标签（先清后插，事务内完成）。
     *
     * @param fundCode 基金代码
     * @param tagIds   标签 ID 列表（可为空数组表示清空）
     */
    void setFundTags(String fundCode, List<Long> tagIds);

    /**
     * 全部基金的标签映射（自选列表批量展示用，避免逐只请求）。
     *
     * @return fundCode → 标签名列表（按 sortNo 升序）
     */
    Map<String, List<String>> allFundTags();

    /**
     * 某个标签下的基金代码集合（自选池按标签筛选使用；分页后筛选必须在服务端完成）。
     *
     * @param tagName 标签名（预定义标签库中的名称）
     * @return 基金代码列表；标签不存在时返回空列表（语义为"筛不出任何基金"，而非"不过滤"）
     */
    List<String> fundCodesOfTag(String tagName);
}
