package com.quant.common.result;

import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 统一分页响应体
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    private PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records;
    }

    /** 由 MyBatis-Plus 分页结果转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    /** 直接构建 */
    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records);
    }

    public long getTotal() {
        return total;
    }

    public List<T> getRecords() {
        return records;
    }
}
