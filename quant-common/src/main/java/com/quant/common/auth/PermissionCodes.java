package com.quant.common.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 权限码清单（V5.58，前后端共用的单一来源：后端常量在此定义，前端经「权限字典」接口下发，不自造清单）。
 *
 * <p>两组权限码：
 * <ul>
 *   <li><b>menu:*</b> 菜单可见码——决定侧栏菜单、路由与页面入口是否可见；数据类 GET 接口不做菜单校验
 *       （用户口径：财务数据对临时账号全部可见，菜单只控入口）；</li>
 *   <li><b>action:*</b> 操作码——对应写接口（POST/PUT/DELETE），后端逐接口用
 *       {@code @SaCheckPermission} 强制校验，前端只做按钮显隐（绕过前端也写不进去）。</li>
 * </ul>
 *
 * <p>不在分配清单内的敏感操作（保存平台配置、发测试邮件/企微、手动跑信号、AI 模型配置、用户管理等）
 * 一律 {@code @SaCheckRole("ADMIN")}，仅管理员可用。
 */
public final class PermissionCodes {

    /** 管理员角色码（sys_user.role） */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 临时账号角色码（sys_user.role） */
    public static final String ROLE_GUEST = "GUEST";

    // ===== 菜单可见码 =====
    public static final String MENU_DASHBOARD = "menu:dashboard";
    public static final String MENU_FUNDS = "menu:funds";
    public static final String MENU_TRADES = "menu:trades";
    public static final String MENU_SIGNALS = "menu:signals";
    public static final String MENU_MARKET_SIGNALS = "menu:marketSignals";
    public static final String MENU_COMPARE = "menu:compare";
    public static final String MENU_IMPORT = "menu:import";
    public static final String MENU_AI_USAGE = "menu:aiUsage";
    public static final String MENU_MANUAL = "menu:manual";
    public static final String MENU_PLATFORM_CONFIG = "menu:platformConfig";
    public static final String MENU_LOGIN_LOGS = "menu:loginLogs";

    // ===== 操作码 =====
    public static final String ACTION_SYNC = "action:sync";
    public static final String ACTION_IMPORT_FUND = "action:importFund";
    public static final String ACTION_TRADE = "action:trade";
    public static final String ACTION_TAG = "action:tag";
    public static final String ACTION_STRATEGY = "action:strategy";
    public static final String ACTION_AI_CHAT = "action:aiChat";

    /** 「一键只读访客」默认模板（用户拍板）：仪表盘 + 基金池 + 信号查询 + 基金对比 + 使用手册 */
    public static final List<String> GUEST_DEFAULT = Collections.unmodifiableList(Arrays.asList(
            MENU_DASHBOARD, MENU_FUNDS, MENU_SIGNALS, MENU_COMPARE, MENU_MANUAL));

    /** 全部权限码（管理员隐含拥有；字典下发与校验过滤用） */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            MENU_DASHBOARD, MENU_FUNDS, MENU_TRADES, MENU_SIGNALS, MENU_MARKET_SIGNALS,
            MENU_COMPARE, MENU_IMPORT, MENU_AI_USAGE, MENU_MANUAL, MENU_PLATFORM_CONFIG, MENU_LOGIN_LOGS,
            ACTION_SYNC, ACTION_IMPORT_FUND, ACTION_TRADE, ACTION_TAG, ACTION_STRATEGY, ACTION_AI_CHAT));

    private PermissionCodes() {
    }

    /** 权限码是否在已知清单内（保存用户时过滤非法码） */
    public static boolean isKnown(String code) {
        return ALL.contains(code);
    }

    /**
     * 权限码中文名（管理页展示用；与 ALL 顺序一致）。
     * 菜单码 label 只写菜单名，操作码 label 写清影响面，便于 admin 勾选时理解。
     */
    public static String labelOf(String code) {
        switch (code) {
            case MENU_DASHBOARD:
                return "仪表盘";
            case MENU_FUNDS:
                return "基金池（含基金详情/回测结果页）";
            case MENU_TRADES:
                return "交易流水";
            case MENU_SIGNALS:
                return "信号查询";
            case MENU_MARKET_SIGNALS:
                return "市场信号";
            case MENU_COMPARE:
                return "基金对比";
            case MENU_IMPORT:
                return "数据导入";
            case MENU_AI_USAGE:
                return "AI用量统计";
            case MENU_MANUAL:
                return "使用手册";
            case MENU_PLATFORM_CONFIG:
                return "平台配置";
            case MENU_LOGIN_LOGS:
                return "登录日志";
            case ACTION_SYNC:
                return "手动同步（基金池/详情/指数看板/批量同步入口）";
            case ACTION_IMPORT_FUND:
                return "导入/恢复基金（含移出自选）";
            case ACTION_TRADE:
                return "记账（记一笔/编辑/删除流水、转入转出）";
            case ACTION_TAG:
                return "标签管理（标签库与打标）";
            case ACTION_STRATEGY:
                return "策略与回测（新增/编辑/删除策略、发起回测）";
            case ACTION_AI_CHAT:
                return "AI 对话（消耗全局 AI 额度）";
            default:
                return code;
        }
    }

    /** 权限码分组：menu=菜单可见 / action=写操作（字典接口按此分组渲染勾选面板） */
    public static String groupOf(String code) {
        return code.startsWith("menu:") ? "menu" : "action";
    }
}
