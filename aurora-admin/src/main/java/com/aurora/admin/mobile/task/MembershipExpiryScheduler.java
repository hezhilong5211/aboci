package com.aurora.admin.mobile.task;

import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 会员到期短周期补偿任务；接口惰性结算负责即时体验，本任务负责无人访问用户。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "membership.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MembershipExpiryScheduler {

    private final IMembershipExpiryService membershipExpiryService;

    @Value("${membership.expiry.batch-size:500}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${membership.expiry.fixed-delay-ms:30000}",
            initialDelayString = "${membership.expiry.initial-delay-ms:15000}")
    public void settleExpiredMemberships() {
        int processed = membershipExpiryService.settleExpiredBatch(MembershipPolicy.now(), batchSize);
        if (processed > 0) {
            log.info("会员到期批处理完成: processed={}", processed);
        }
    }
}
