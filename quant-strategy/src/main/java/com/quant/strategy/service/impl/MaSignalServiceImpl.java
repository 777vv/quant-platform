package com.quant.strategy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.result.PageResult;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundMaDailyMapper;
import com.quant.fund.entity.FundMaDaily;
import com.quant.strategy.dto.MaSignalItemVO;
import com.quant.strategy.entity.MaSignal;
import com.quant.strategy.mapper.MaSignalMapper;
import com.quant.strategy.service.MaSignalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 均线信号服务实现（V5.70）。
 *
 * <p>判定逻辑（用户口径）：取 fund_ma_daily 每只基金**最近两个交易日**的快照，
 * 对 5/10/20/30/60/90/120/250 日均线两两组合（28 对）比较"短期均线 − 长期均线"的符号：
 * 前一日与当日符号翻转即为上穿（负→正）/下穿（正→负）。窗口本身就是均线，
 * 天然过滤了周期差异，无需再做窗口对齐。
 *
 * <p>数据时机：每交易日 10:00 定时任务跑，比较的是**最近两个已收盘交易日**（数据由前夜的
 * ma:daily 任务算好），即"盘前看昨日收盘信号"。
 *
 * <p>幂等：唯一键 (fund_code, signal_date, ma_short, ma_long)，已存在的信号不重复入库。
 */
@Service
public class MaSignalServiceImpl implements MaSignalService {

    private final MaSignalMapper maSignalMapper;

    private final FundMaDailyMapper maDailyMapper;

    private final FundBasicMapper fundBasicMapper;

    public MaSignalServiceImpl(MaSignalMapper maSignalMapper, FundMaDailyMapper maDailyMapper,
                               FundBasicMapper fundBasicMapper) {
        this.maSignalMapper = maSignalMapper;
        this.maDailyMapper = maDailyMapper;
        this.fundBasicMapper = fundBasicMapper;
    }

    @Override
    @Transactional
    public int computeFromMaDaily() {
        // V6.44 改口径：每只基金取**自己的**最近两个快照日判定上/下穿（signal_date = 基金自己的最新快照日）。
        // 此前取"全局最近两日 + 基金交集"——任一晚快照残缺（如 10-12 只有 ETF、10-09 只有场外，10-09~10-13 实证）
        // 全局日期就裂开、交集为 0，全体基金停摆；改为各基金独立判定后，某晚某条数据腿失败只影响对应基金，
        // 互不拖累。快照表只存近期日期（refresh 每基金每天一行），全量读取开销可忽略。
        List<FundMaDaily> all = maDailyMapper.selectList(new LambdaQueryWrapper<FundMaDaily>()
                .orderByAsc(FundMaDaily::getFundCode)
                .orderByAsc(FundMaDaily::getTradeDate));
        if (all.isEmpty()) {
            return 0;
        }
        Map<String, List<FundMaDaily>> rowsByCode = new LinkedHashMap<>();
        for (FundMaDaily row : all) {
            rowsByCode.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(row);
        }

        int[] windows = {5, 10, 20, 30, 60, 90, 120, 250};
        int created = 0;
        for (List<FundMaDaily> rows : rowsByCode.values()) {
            if (rows.size() < 2) {
                continue;
            }
            FundMaDaily prev = rows.get(rows.size() - 2);
            FundMaDaily curr = rows.get(rows.size() - 1);
            for (int i = 0; i < windows.length; i++) {
                for (int j = i + 1; j < windows.length; j++) {
                    BigDecimal prevDiff = diffOf(prev, windows[i], windows[j]);
                    BigDecimal currDiff = diffOf(curr, windows[i], windows[j]);
                    if (prevDiff == null || currDiff == null) {
                        continue;
                    }
                    int prevSign = prevDiff.compareTo(BigDecimal.ZERO);
                    int currSign = currDiff.compareTo(BigDecimal.ZERO);
                    if (prevSign == 0 || prevSign == currSign) {
                        continue;
                    }
                    MaSignal signal = new MaSignal();
                    signal.setFundCode(curr.getFundCode());
                    signal.setSignalDate(curr.getTradeDate());
                    signal.setMaShort(windows[i]);
                    signal.setMaLong(windows[j]);
                    signal.setDirection(currSign > 0 ? "UP" : "DOWN");
                    signal.setPriceAt(curr.getClosePrice());
                    signal.setMaShortVal(maValue(curr, windows[i]));
                    signal.setMaLongVal(maValue(curr, windows[j]));
                    if (insertIfAbsent(signal)) {
                        created++;
                    }
                }
            }
        }
        return created;
    }

    @Override
    public PageResult<MaSignalItemVO> page(String fundCode, String keyword, String direction, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        LambdaQueryWrapper<MaSignal> query = new LambdaQueryWrapper<>();
        if (fundCode != null && !fundCode.isBlank()) {
            query.eq(MaSignal::getFundCode, fundCode.trim());
        }
        // 关键词模糊查询（V6.02 用户口径）：按基金代码或名称模糊匹配（ma_signal 只存代码）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<String> codes = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                            .and(w -> w.like(FundBasic::getFundCode, kw).or().like(FundBasic::getFundName, kw)))
                    .stream().map(FundBasic::getFundCode).toList();
            if (codes.isEmpty()) {
                return PageResult.of(0, List.of());
            }
            query.in(MaSignal::getFundCode, codes);
        }
        // 方向过滤（V6.00）：只认 UP/DOWN 两个值，其他输入一律当作"不过滤"
        if ("UP".equals(direction) || "DOWN".equals(direction)) {
            query.eq(MaSignal::getDirection, direction);
        }
        long total = maSignalMapper.selectCount(query);
        List<MaSignal> rows = maSignalMapper.selectList(query
                .orderByDesc(MaSignal::getSignalDate).orderByDesc(MaSignal::getId)
                .last("LIMIT " + ((pageNo - 1) * pageSize) + "," + pageSize));
        return PageResult.of(total, toVoList(rows));
    }

    @Override
    public List<MaSignalItemVO> listByFund(String fundCode) {
        List<MaSignal> rows = maSignalMapper.selectList(new LambdaQueryWrapper<MaSignal>()
                .eq(MaSignal::getFundCode, fundCode)
                .orderByDesc(MaSignal::getSignalDate).orderByDesc(MaSignal::getId));
        return toVoList(rows);
    }

    /** 幂等插入：唯一键 (fund_code, signal_date, ma_short, ma_long) 已存在则跳过 */
    private boolean insertIfAbsent(MaSignal signal) {
        Long exists = maSignalMapper.selectCount(new LambdaQueryWrapper<MaSignal>()
                .eq(MaSignal::getFundCode, signal.getFundCode())
                .eq(MaSignal::getSignalDate, signal.getSignalDate())
                .eq(MaSignal::getMaShort, signal.getMaShort())
                .eq(MaSignal::getMaLong, signal.getMaLong()));
        if (exists != null && exists > 0) {
            return false;
        }
        maSignalMapper.insert(signal);
        return true;
    }

    /** 短期均线值 − 长期均线值（任一为 null 视为样本不足，返回 null） */
    private BigDecimal diffOf(FundMaDaily row, int shortWindow, int longWindow) {
        BigDecimal shortVal = maValue(row, shortWindow);
        BigDecimal longVal = maValue(row, longWindow);
        if (shortVal == null || longVal == null) {
            return null;
        }
        return shortVal.subtract(longVal);
    }

    private BigDecimal maValue(FundMaDaily row, int window) {
        return switch (window) {
            case 5 -> row.getMa5();
            case 10 -> row.getMa10();
            case 20 -> row.getMa20();
            case 30 -> row.getMa30();
            case 60 -> row.getMa60();
            case 90 -> row.getMa90();
            case 120 -> row.getMa120();
            case 250 -> row.getMa250();
            default -> null;
        };
    }

    /** 批量补基金名称并转行视图（含信号描述） */
    private List<MaSignalItemVO> toVoList(List<MaSignal> rows) {
        Map<String, String> nameByCode = new HashMap<>();
        if (!rows.isEmpty()) {
            Set<String> codes = new HashSet<>();
            for (MaSignal row : rows) {
                codes.add(row.getFundCode());
            }
            for (FundBasic fund : fundBasicMapper.selectList(
                    new LambdaQueryWrapper<FundBasic>().in(FundBasic::getFundCode, codes))) {
                nameByCode.put(fund.getFundCode(), fund.getFundName());
            }
        }
        List<MaSignalItemVO> result = new ArrayList<>();
        for (MaSignal row : rows) {
            MaSignalItemVO vo = new MaSignalItemVO();
            vo.setId(row.getId());
            vo.setFundCode(row.getFundCode());
            vo.setFundName(nameByCode.getOrDefault(row.getFundCode(), row.getFundCode()));
            vo.setSignalDate(row.getSignalDate());
            vo.setMaShort(row.getMaShort());
            vo.setMaLong(row.getMaLong());
            vo.setDirection(row.getDirection());
            vo.setSignalDesc(row.getMaShort() + "日均线上穿" + row.getMaLong() + "日均线");
            if ("DOWN".equals(row.getDirection())) {
                vo.setSignalDesc(row.getMaShort() + "日均线下穿" + row.getMaLong() + "日均线");
            }
            vo.setPriceAt(row.getPriceAt());
            vo.setMaShortVal(row.getMaShortVal());
            vo.setMaLongVal(row.getMaLongVal());
            result.add(vo);
        }
        return result;
    }
}
