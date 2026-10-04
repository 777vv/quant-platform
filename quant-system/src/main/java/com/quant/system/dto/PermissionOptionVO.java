package com.quant.system.dto;

import lombok.Data;

/**
 * 权限码字典项（V5.58）：用户管理页勾选面板的一条选项。
 * group 取值 menu（菜单可见）/ action（写操作），前端按分组渲染两块勾选区。
 */
@Data
public class PermissionOptionVO {

    /** 权限码（如 menu:funds / action:sync） */
    private String code;

    /** 中文名（勾选面板展示） */
    private String label;

    /** 分组：menu / action */
    private String group;

    /** 分组中文名 */
    private String groupLabel;
}
