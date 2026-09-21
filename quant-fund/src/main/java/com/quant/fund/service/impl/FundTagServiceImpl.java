package com.quant.fund.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.fund.dto.FundTagVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundTag;
import com.quant.fund.entity.FundTagRel;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundTagMapper;
import com.quant.fund.mapper.FundTagRelMapper;
import com.quant.fund.service.FundTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基金标签服务实现。
 * 设计取舍：标签为**预定义库**（先建标签再贴），避免"红利/红利策略/高股息"这类同义碎片；
 * 基金移出自选（软删）时保留关联记录，重新导入即自动恢复标签。
 */
@Service
public class FundTagServiceImpl implements FundTagService {

    /** 标签名长度上限（与库字段一致） */
    private static final int NAME_MAX_LENGTH = 32;

    private final FundTagMapper tagMapper;

    private final FundTagRelMapper relMapper;

    private final FundBasicMapper fundBasicMapper;

    public FundTagServiceImpl(FundTagMapper tagMapper, FundTagRelMapper relMapper, FundBasicMapper fundBasicMapper) {
        this.tagMapper = tagMapper;
        this.relMapper = relMapper;
        this.fundBasicMapper = fundBasicMapper;
    }

    @Override
    public List<FundTagVO> listLibrary() {
        List<FundTag> tags = tagMapper.selectList(new LambdaQueryWrapper<FundTag>()
                .orderByAsc(FundTag::getSortNo).orderByAsc(FundTag::getId));
        // 一次性统计各标签的基金数，避免逐个 count
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (FundTagRel rel : relMapper.selectList(new LambdaQueryWrapper<>())) {
            counts.merge(rel.getTagId(), 1L, Long::sum);
        }
        List<FundTagVO> result = new ArrayList<>(tags.size());
        for (FundTag tag : tags) {
            result.add(new FundTagVO(tag.getId(), tag.getName(), tag.getSortNo(), counts.getOrDefault(tag.getId(), 0L)));
        }
        return result;
    }

    @Override
    public void createTag(String name) {
        String trimmed = normalizeName(name);
        if (tagMapper.selectCount(new LambdaQueryWrapper<FundTag>().eq(FundTag::getName, trimmed)) > 0) {
            throw new BizException("标签已存在：" + trimmed);
        }
        FundTag tag = new FundTag();
        tag.setName(trimmed);
        // 新标签排到末尾，保持库内既有顺序稳定
        tag.setSortNo((int) (tagMapper.selectCount(new LambdaQueryWrapper<>()) + 1));
        tagMapper.insert(tag);
    }

    @Override
    public void renameTag(Long id, String name) {
        FundTag tag = requireTag(id);
        String trimmed = normalizeName(name);
        if (tagMapper.selectCount(new LambdaQueryWrapper<FundTag>()
                .eq(FundTag::getName, trimmed).ne(FundTag::getId, id)) > 0) {
            throw new BizException("标签名已被占用：" + trimmed);
        }
        tag.setName(trimmed);
        tagMapper.updateById(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        requireTag(id);
        relMapper.delete(new LambdaQueryWrapper<FundTagRel>().eq(FundTagRel::getTagId, id));
        tagMapper.deleteById(id);
    }

    @Override
    public List<FundTagVO> tagsOfFund(String fundCode) {
        return tagsOfFunds(List.of(fundCode)).getOrDefault(fundCode, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setFundTags(String fundCode, List<Long> tagIds) {
        if (fundBasicMapper.selectCount(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getFundCode, fundCode).eq(FundBasic::getStatus, 1)) == 0) {
            throw new BizException("基金不在自选池: " + fundCode);
        }
        relMapper.delete(new LambdaQueryWrapper<FundTagRel>().eq(FundTagRel::getFundCode, fundCode));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // 去重后插入，避免重复 ID 触发唯一键冲突
        for (Long tagId : tagIds.stream().distinct().toList()) {
            requireTag(tagId);
            FundTagRel rel = new FundTagRel();
            rel.setFundCode(fundCode);
            rel.setTagId(tagId);
            relMapper.insert(rel);
        }
    }

    @Override
    public Map<String, List<String>> allFundTags() {
        List<FundTagRel> rels = relMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, FundTag> tagById = new LinkedHashMap<>();
        tagMapper.selectList(new LambdaQueryWrapper<FundTag>()
                        .orderByAsc(FundTag::getSortNo).orderByAsc(FundTag::getId))
                .forEach(tag -> tagById.put(tag.getId(), tag));
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (FundTagRel rel : rels) {
            FundTag tag = tagById.get(rel.getTagId());
            if (tag != null) {
                result.computeIfAbsent(rel.getFundCode(), k -> new ArrayList<>()).add(tag.getName());
            }
        }
        return result;
    }

    @Override
    public List<String> fundCodesOfTag(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return List.of();
        }
        FundTag tag = tagMapper.selectOne(new LambdaQueryWrapper<FundTag>().eq(FundTag::getName, tagName));
        if (tag == null) {
            // 标签不存在：返回空集合，让筛选结果为空而不是"忽略筛选条件"
            return List.of();
        }
        return relMapper.selectList(new LambdaQueryWrapper<FundTagRel>().eq(FundTagRel::getTagId, tag.getId()))
                .stream().map(FundTagRel::getFundCode).distinct().toList();
    }

    /** 按基金批量取标签（保持 sortNo 顺序） */
    private Map<String, List<FundTagVO>> tagsOfFunds(List<String> fundCodes) {
        Map<String, List<FundTagVO>> result = new LinkedHashMap<>();
        if (fundCodes.isEmpty()) {
            return result;
        }
        Map<Long, FundTag> tagById = new LinkedHashMap<>();
        tagMapper.selectList(new LambdaQueryWrapper<FundTag>()
                        .orderByAsc(FundTag::getSortNo).orderByAsc(FundTag::getId))
                .forEach(tag -> tagById.put(tag.getId(), tag));
        relMapper.selectList(new LambdaQueryWrapper<FundTagRel>().in(FundTagRel::getFundCode, fundCodes))
                .forEach(rel -> {
                    FundTag tag = tagById.get(rel.getTagId());
                    if (tag != null) {
                        result.computeIfAbsent(rel.getFundCode(), k -> new ArrayList<>())
                                .add(new FundTagVO(tag.getId(), tag.getName(), tag.getSortNo(), null));
                    }
                });
        return result;
    }

    private FundTag requireTag(Long id) {
        FundTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BizException("标签不存在: " + id);
        }
        return tag;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("标签名不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BizException("标签名不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }
}
