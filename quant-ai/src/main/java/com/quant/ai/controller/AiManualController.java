package com.quant.ai.controller;

import com.quant.ai.manual.ManualCatalog;
import com.quant.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台使用手册接口（V4.2）：给【使用手册】页读同一份 Markdown。
 *
 * <p>与 AI 的手册工具**同源**：都读随 jar 发布的那份手册（`docs/05-使用手册.md` 构建期拷入 classpath），
 * 所以"页面上写的"和"AI 答的"不会走散。
 */
@RestController
@RequestMapping("/api/ai/manual")
public class AiManualController {

    private final ManualCatalog catalog;

    public AiManualController(ManualCatalog catalog) {
        this.catalog = catalog;
    }

    /** 手册全文（Markdown 原文；前端自行渲染与生成目录） */
    @GetMapping
    public R<String> manual() {
        catalog.requireLoaded();
        return R.ok(catalog.markdown());
    }
}
