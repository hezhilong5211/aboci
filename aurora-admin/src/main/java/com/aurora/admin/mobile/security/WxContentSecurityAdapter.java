package com.aurora.admin.mobile.security;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckRequest;
import cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckResponse;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.ai.agent.domain.AuditRecord;
import com.aurora.ai.agent.security.ContentCheckResult;
import com.aurora.ai.agent.security.IContentSecurityPort;
import com.aurora.ai.agent.service.IAuditRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信内容安全审核端口适配器。
 *
 * 实现 aurora-ai 定义的 SPI 接口 {@link IContentSecurityPort},内部走微信 msgSecCheck v2
 * (依赖方向 admin→ai,规避 ai→admin 循环依赖)。
 * 存证策略:仅命中(review/risky)与异常(error)写 {@code audit_record};命中存原文摘要、
 * 异常不存原文(只记元数据),正常(pass)不记 —— 省存储与隐私。content 与 openid 在日志中脱敏。
 *
 * @author aurora
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WxContentSecurityAdapter implements IContentSecurityPort {

    private final WxServiceFactory wxServiceFactory;
    private final IMobileUserService mobileUserService;
    private final IAuditRecordService auditRecordService;

    /** msgSecCheck 接口版本(v2) */
    private static final String SEC_CHECK_VERSION = "2";

    /** 审核场景:资料文本(对话输入按官方枚举取 4) */
    private static final int SEC_CHECK_SCENE_CHAT = 4;

    /** 存证文本截断上限(字符) */
    private static final int SNIPPET_MAX_LEN = 500;

    @Override
    public ContentCheckResult checkText(Long userId, String content, String scene) {
        // 1. 查 openid:缺失则无法走 v2 审核 → 降级放行并存证
        String openid = null;
        String nickname = null;
        MobileUser user = mobileUserService.getById(userId);
        if (user != null) {
            openid = user.getWxOpenid();
            nickname = user.getNickName();
        }
        if (openid == null || openid.isEmpty()) {
            String reason = "缺少openid,降级放行";
            saveAudit(userId, null, "error", null, "ERROR", reason);
            return ContentCheckResult.degraded(reason);
        }

        // 2. 调微信 msgSecCheck v2
        try {
            WxMaService wxMaService = wxServiceFactory.getWxMaService();
            WxMaMsgSecCheckCheckRequest req = WxMaMsgSecCheckCheckRequest.builder()
                    .version(SEC_CHECK_VERSION)
                    .scene(SEC_CHECK_SCENE_CHAT)
                    .openid(openid)
                    .nickname(nickname)
                    .content(content)
                    .build();
            WxMaMsgSecCheckCheckResponse resp = wxMaService.getSecurityService().checkMessage(req);

            // 3. 解析 suggest/label(result 可能为空,防御取值)
            String suggest = null;
            String label = null;
            if (resp != null && resp.getResult() != null) {
                suggest = resp.getResult().getSuggest();
                label = resp.getResult().getLabel();
            }

            if ("pass".equals(suggest)) {
                // 正常内容(pass)不落库:只记命中与异常,省存储与隐私
                return ContentCheckResult.pass();
            }
            // review / risky(或其他非 pass 值)均按拦截处理
            String hit = (label != null && !label.isEmpty()) ? label : suggest;
            String reason = "检测到可能违规内容(" + hit + "),请调整后重试";
            saveAudit(userId, content, suggest, label, "REJECT", reason);
            return ContentCheckResult.reject(suggest, label, reason);
        } catch (Exception e) {
            // 4. 审核服务异常:告警(脱敏,不打印完整 content/openid)+ 降级放行 + 存证
            log.warn("内容审核服务异常,降级放行,userId={}:{}", userId, e.getMessage());
            String reason = "审核服务暂不可用,已放行";
            saveAudit(userId, null, "error", null, "ERROR", reason);
            return ContentCheckResult.degraded(reason);
        }
    }

    /**
     * 写一条审核存证流水。content 截断到 ≤{@value SNIPPET_MAX_LEN} 字,不落完整明文。
     * conversation_id/task_id 本期文本审核留空。
     */
    private void saveAudit(Long userId, String content, String suggest, String label, String result, String reason) {
        try {
            AuditRecord record = new AuditRecord();
            record.setUserId(userId);
            record.setObjectType("text");
            record.setBizType("chat_input");
            record.setContentSnippet(truncate(content));
            record.setSuggest(suggest);
            record.setLabel(label);
            record.setResult(result);
            record.setReason(reason);
            auditRecordService.saveRecord(record);
        } catch (Exception e) {
            // 存证失败不阻断审核主流程,仅告警
            log.warn("写审核存证失败,userId={}:{}", userId, e.getMessage());
        }
    }

    /**
     * 文本截断:null 返回 null,超长截断到上限。
     */
    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= SNIPPET_MAX_LEN ? content : content.substring(0, SNIPPET_MAX_LEN);
    }
}
