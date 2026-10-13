package com.aurora.admin.distribution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分销接口请求对象集合。
 */
public final class DistributionDtos {

    @Data
    public static class PartnerAdd {
        @NotNull(message = "请选择小程序用户")
        private Long userId;
        @Size(max = 500, message = "备注不能超过500个字符")
        private String remark;
    }

    @Data
    public static class PartnerStatus {
        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "分销商状态不正确")
        private String status;
        @Size(max = 500, message = "备注不能超过500个字符")
        private String remark;
    }

    @Data
    public static class Visit {
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 32, message = "邀请码格式不正确")
        private String inviteCode;
        @NotBlank(message = "访问事件编号不能为空")
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "访问事件编号格式不正确")
        private String eventId;
        @Size(max = 64, message = "启动场景不能超过64个字符")
        private String launchScene;
    }

    @Data
    public static class Bind {
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 32, message = "邀请码格式不正确")
        private String inviteCode;
        @NotBlank(message = "访问事件编号不能为空")
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "访问事件编号格式不正确")
        private String eventId;
    }

    @Data
    public static class ConfigUpdate {
        @NotNull(message = "功能开关不能为空")
        @Min(value = 0, message = "功能开关不正确")
        @Max(value = 1, message = "功能开关不正确")
        private Integer enabled;
        @NotNull(message = "佣金比例不能为空")
        @Min(value = 0, message = "佣金比例不能小于0")
        @Max(value = 10000, message = "佣金比例不能大于10000基点")
        private Integer commissionRateBps;
        @NotNull(message = "最低提现金额不能为空")
        @Min(value = 0, message = "最低提现金额不能小于0")
        private Long minWithdrawAmount;
    }

    @Data
    public static class CommissionAdjust {
        @NotNull(message = "调整后的有效佣金不能为空")
        @Min(value = 0, message = "调整后的有效佣金不能小于0")
        private Long effectiveAmount;
        @NotBlank(message = "调整原因不能为空")
        @Size(max = 300, message = "调整原因不能超过300个字符")
        private String reason;
    }

    @Data
    public static class WithdrawalApply {
        @NotNull(message = "提现金额不能为空")
        @Min(value = 1, message = "提现金额必须大于0")
        private Long applyAmount;
        @NotBlank(message = "收款方式不能为空")
        @Pattern(regexp = "WECHAT_QR|ALIPAY_QR|BANK_CARD", message = "收款方式不正确")
        private String methodType;
        @NotBlank(message = "收款人姓名不能为空")
        @Size(max = 64, message = "收款人姓名不能超过64个字符")
        private String payeeName;
        @Size(max = 128, message = "银行名称不能超过128个字符")
        private String bankName;
        @Size(max = 64, message = "银行卡号格式不正确")
        private String bankAccount;
        @AssertTrue(message = "请确认并同意收款资料使用说明")
        private Boolean privacyAccepted;
    }

    @Data
    public static class WithdrawalReject {
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 500, message = "驳回原因不能超过500个字符")
        private String reason;
    }

    private DistributionDtos() {
    }
}
