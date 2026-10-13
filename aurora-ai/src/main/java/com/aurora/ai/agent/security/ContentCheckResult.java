package com.aurora.ai.agent.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本内容安全审核结论。
 *
 * 由 {@link IContentSecurityPort#checkText} 返回,承载放行/拦截结论及违规提示文案。
 *
 * @author aurora
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentCheckResult {

    /**
     * 是否放行(true=放行,false=拦截)
     */
    private boolean pass;

    /**
     * 审核建议(pass / review / risky / error)
     */
    private String suggest;

    /**
     * 微信命中标签(可空)
     */
    private String label;

    /**
     * 给用户的违规提示文案(可空)
     */
    private String reason;

    /**
     * 放行结论。
     *
     * @return pass=true、suggest="pass" 的结论
     */
    public static ContentCheckResult pass() {
        return new ContentCheckResult(true, "pass", null, null);
    }

    /**
     * 拦截结论。
     *
     * @param suggest 审核建议(review / risky 等)
     * @param label   微信命中标签(可空)
     * @param reason  给用户的违规提示文案(可空)
     * @return pass=false 的结论
     */
    public static ContentCheckResult reject(String suggest, String label, String reason) {
        return new ContentCheckResult(false, suggest, label, reason);
    }

    /**
     * 异常降级结论:审核服务异常时放行,记原因。
     *
     * @param reason 降级原因
     * @return pass=true、suggest="error" 的结论
     */
    public static ContentCheckResult degraded(String reason) {
        return new ContentCheckResult(true, "error", null, reason);
    }
}
