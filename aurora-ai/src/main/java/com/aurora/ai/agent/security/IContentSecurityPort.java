package com.aurora.ai.agent.security;

/**
 * 文本内容安全审核端口(跨模块 SPI）。
 *
 * 用于 AI 对话场景下对用户文本进行内容安全审核。
 * 此接口定义在 aurora-ai 模块,由 aurora-admin 模块的适配器实现
 * (同 {@link com.aurora.ai.agent.billing.IBeanDeductPort} 范式,规避 ai→admin 循环依赖)。
 * aurora-ai 模块通过 @Autowired(required=false) 可选注入,为空时降级放行。
 *
 * @author aurora
 */
public interface IContentSecurityPort {

    /**
     * 文本内容安全审核。
     *
     * @param userId  用户ID
     * @param content 待审核文本
     * @param scene   审核场景(用于区分调用来源)
     * @return 审核结论;放行时 pass=true,拦截时 pass=false
     */
    ContentCheckResult checkText(Long userId, String content, String scene);
}
