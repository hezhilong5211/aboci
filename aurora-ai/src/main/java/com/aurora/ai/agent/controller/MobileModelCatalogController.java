package com.aurora.ai.agent.controller;

import com.aurora.ai.agent.dto.MobileModelVO;
import com.aurora.ai.agent.service.MobileModelCatalogService;
import com.aurora.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/agent")
@RequiredArgsConstructor
public class MobileModelCatalogController {
    private final MobileModelCatalogService catalog;

    @GetMapping("/models")
    public Result<Map<String, List<MobileModelVO>>> models(@RequestAttribute("mobileUserId") Long userId) {
        return Result.success(catalog.catalog());
    }
}
