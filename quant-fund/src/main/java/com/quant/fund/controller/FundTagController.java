package com.quant.fund.controller;

import java.util.List;
import java.util.Map;

import com.quant.common.result.R;
import com.quant.fund.dto.FundTagVO;
import com.quant.fund.dto.FundTagsVO;
import com.quant.fund.service.FundTagService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基金标签接口：标签库维护 + 基金贴标签
 */
@RestController
@RequestMapping("/api")
public class FundTagController {

    private final FundTagService tagService;

    public FundTagController(FundTagService tagService) {
        this.tagService = tagService;
    }

    /** 标签库列表（含各标签下的基金数量） */
    @GetMapping("/tags")
    public R<List<FundTagVO>> library() {
        return R.ok(tagService.listLibrary());
    }

    /** 新建标签（名称唯一） */
    @PostMapping("/tags")
    public R<Void> create(@RequestBody TagNameRequest request) {
        tagService.createTag(request.name());
        return R.ok();
    }

    /** 重命名标签 */
    @PutMapping("/tags/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody TagNameRequest request) {
        tagService.renameTag(id, request.name());
        return R.ok();
    }

    /** 删除标签（同时清理关联，前端二次确认） */
    @DeleteMapping("/tags/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tagService.deleteTag(id);
        return R.ok();
    }

    /** 某基金的标签 */
    @GetMapping("/funds/{code}/tags")
    public R<FundTagsVO> tagsOfFund(@PathVariable String code) {
        return R.ok(new FundTagsVO(code, tagService.tagsOfFund(code)));
    }

    /** 覆盖式设置某基金的标签 */
    @PutMapping("/funds/{code}/tags")
    public R<Void> setTags(@PathVariable String code, @RequestBody SetTagsRequest request) {
        tagService.setFundTags(code, request.tagIds());
        return R.ok();
    }

    /** 全部基金的标签映射（自选列表批量展示） */
    @GetMapping("/funds/tags")
    public R<Map<String, List<String>>> allFundTags() {
        return R.ok(tagService.allFundTags());
    }

    /**
     * 标签名请求体
     */
    public record TagNameRequest(
            /** 标签名（1-32 字符，唯一） */
            String name) {
    }

    /**
     * 设置基金标签请求体
     */
    public record SetTagsRequest(
            /** 标签 ID 列表（空数组表示清空该基金标签） */
            List<Long> tagIds) {
    }
}
