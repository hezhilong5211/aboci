package com.aurora.ai.agent.billing;

import com.aurora.common.core.exception.ServiceException;

/**
 * 出图预扣时余额不足异常。
 *
 * <p>携带余额 / 应扣 / 每张单价,供不同调用方差异化处理:
 * <ul>
 *   <li>对话内出图(generate_images 工具):中断工具循环并返回带充值动作的 SSE 错误事件;</li>
 *   <li>独立出图端点(POST /generate):捕获后返回业务错误码 + 中文文案,不提交任务、不扣豆。</li>
 * </ul>
 */
public class InsufficientBeanException extends ServiceException {

    private static final long serialVersionUID = 1L;

    /** 当前余额 */
    private final long balance;
    /** 本次应扣豆子 */
    private final long needed;
    /** 每张图单价(豆子) */
    private final long beanPerImage;

    public InsufficientBeanException(long balance, long needed, long beanPerImage) {
        super("豆子余额不足");
        this.balance = balance;
        this.needed = needed;
        this.beanPerImage = beanPerImage;
    }

    public long getBalance() {
        return balance;
    }

    public long getNeeded() {
        return needed;
    }

    public long getBeanPerImage() {
        return beanPerImage;
    }
}
