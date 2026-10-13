package com.aurora.admin.mobile.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPayNotifyProvideGoodsRequest;
import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPayQueryOrderRequest;
import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPayQueryOrderResponse;
import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPaySigParams;
import cn.binarywang.wx.miniapp.constant.WxMaConstants;
import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.dto.VirtualPayCreateDTO;
import com.aurora.admin.mobile.dto.VirtualPayClientFailureDTO;
import com.aurora.admin.mobile.dto.VirtualPayGoodsNotifyDTO;
import com.aurora.admin.mobile.dto.VirtualPayRefundNotifyDTO;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.service.IPayOrderService;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.IVirtualPayService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.VirtualPayCreateVO;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 微信小程序虚拟支付普通道具直购实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualPayServiceImpl implements IVirtualPayService {

    private static final String PRODUCT_TYPE_MEMBERSHIP = "MEMBERSHIP";
    private static final String PRODUCT_TYPE_BEAN_RECHARGE = "BEAN_RECHARGE";
    private static final String PAY_CHANNEL_VIRTUAL = "VIRTUAL";
    private static final String MODE_SHORT_SERIES_GOODS = "short_series_goods";
    private static final String EVENT_GOODS_DELIVER = "xpay_goods_deliver_notify";
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,20}$");
    /** 微信支付时间戳按绝对时间解析，再固定转换为业务数据库使用的北京时间。 */
    private static final ZoneId BUSINESS_ZONE = MembershipPolicy.BUSINESS_ZONE;
    /** 已履约订单查退款的最小间隔：首次用户主动查单立即执行，后续防抖。 */
    private static final Duration FULFILLED_REFUND_SYNC_INTERVAL = Duration.ofMinutes(5);
    /** 同一订单的客户端失败日志防抖，避免异常客户端重复上报。 */
    private static final Duration CLIENT_FAILURE_LOG_INTERVAL = Duration.ofSeconds(30);

    private final ISysWxConfigService sysWxConfigService;
    private final WxServiceFactory wxServiceFactory;
    private final IMobileUserService mobileUserService;
    private final IMembershipLevelService membershipLevelService;
    private final IBeanRechargePackageService beanRechargePackageService;
    private final IPayOrderService payOrderService;
    private final IPayOrderBizService payOrderBizService;
    private final IUserMembershipService userMembershipService;
    private final EncryptUtils encryptUtils;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Instant> fulfilledRefundSyncClaims = new ConcurrentHashMap<>();
    private final AtomicInteger fulfilledRefundSyncClaimCount = new AtomicInteger();
    private final ConcurrentMap<String, Instant> clientFailureLogClaims = new ConcurrentHashMap<>();
    private final AtomicInteger clientFailureLogClaimCount = new AtomicInteger();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VirtualPayCreateVO create(Long userId, VirtualPayCreateDTO dto) {
        if (userId == null || dto == null || dto.getProductId() == null || isBlank(dto.getProductType())) {
            throw new ServiceException("商品类型和商品ID不能为空");
        }

        SysWxConfig config = getVirtualPayConfig(true);
        validateConfig(config, true);
        int env = config.getEnv();
        if (env == 1 && "ios".equalsIgnoreCase(dto.getClientPlatform())) {
            throw new ServiceException("iOS 不支持虚拟支付沙箱，请切换正式环境后再试");
        }
        String appKey = appKey(config, env);

        MobileUser user = mobileUserService.getById(userId);
        if (user == null || isBlank(user.getWxOpenid())) {
            throw new ServiceException("用户未绑定微信，无法发起虚拟支付");
        }
        String sessionKey = decryptRequired(user.getWxSessionKey(), "微信登录状态已过期，请重新登录");
        ProductSnapshot product = resolveProduct(dto.getProductType(), dto.getProductId());
        // 与支付回调保持“订单行 → 用户账户行”的锁顺序，避免重建订单和晚到回调互锁。
        closeReplacedOrder(userId, dto.getReplacesOrderNo());
        if (PRODUCT_TYPE_BEAN_RECHARGE.equals(product.productType())) {
            userMembershipService.requireActiveMembership(userId);
        }

        String orderNo = generateOrderNo();
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOpenId(user.getWxOpenid());
        order.setAmount(product.amountFen());
        order.setSubject(product.name());
        order.setProductType(product.productType());
        order.setProductId(product.productId());
        order.setProductName(product.name());
        order.setPayChannel(PAY_CHANNEL_VIRTUAL);
        order.setEnv(env);
        order.setVirtualProductId(product.virtualProductId());
        order.setSnapshotBeans(product.beans());
        order.setSnapshotDurationDays(product.durationDays());
        order.setSnapshotGrantBeans(product.grantBeans());
        order.setSnapshotLevelSort(product.levelSort());
        order.setFulfillmentStatus(0);
        order.setDeliveryConfirmed(0);
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        if (!payOrderService.save(order)) {
            throw new ServiceException("虚拟支付订单创建失败");
        }

        String signData = buildSignData(config.getOfferId(), env, product, orderNo);
        WxMaXPaySigParams sigParams = WxMaXPaySigParams.builder()
                .appKey(appKey)
                .sessionKey(sessionKey)
                .build();

        VirtualPayCreateVO vo = new VirtualPayCreateVO();
        vo.setOrderNo(orderNo);
        vo.setMode(MODE_SHORT_SERIES_GOODS);
        vo.setSignData(signData);
        vo.setPaySig(sigParams.calcPaySig("requestVirtualPayment", signData));
        vo.setSignature(sigParams.calcSig(signData));
        vo.setEnv(env);
        return vo;
    }

    @Override
    public OrderVO query(Long userId, String orderNo, boolean syncWechat) {
        if (userId == null || isBlank(orderNo)) {
            throw new ServiceException("订单号不能为空");
        }
        PayOrder order = findOrder(orderNo);
        if (!userId.equals(order.getUserId())) {
            throw new ServiceException("无权查看该订单");
        }
        if (!PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())) {
            throw new ServiceException("该订单不是虚拟支付订单");
        }

        if (syncWechat) {
            try {
                if (Integer.valueOf(0).equals(order.getStatus())
                        || Integer.valueOf(4).equals(order.getStatus())) {
                    syncFromWechat(order);
                } else if (Integer.valueOf(1).equals(order.getStatus())
                        && claimFulfilledRefundSync(order.getOrderNo())) {
                    // 已履约本地终态也要查微信：退款推送丢失时，用户主动查单可补偿撤销权益。
                    // 按订单 5 分钟防抖，避免小程序重复刷新高频调微信。
                    syncFromWechat(order);
                } else if (Integer.valueOf(1).equals(order.getStatus())
                        && !Integer.valueOf(1).equals(order.getDeliveryConfirmed())
                        && !isBlank(order.getWxOrderId())) {
                    notifyProvideGoods(order, getVirtualPayConfig(false));
                    markDeliveryConfirmed(order.getId());
                }
            } catch (WxErrorException e) {
                // 查单轮询失败不篡改本地状态；下次轮询或消息重推继续补偿。
                log.warn("虚拟支付查单/确认发货失败,订单号={},错误码={},原因={}",
                        orderNo, e.getError() == null ? null : e.getError().getErrorCode(), e.getMessage());
            }
        }
        return payOrderBizService.queryOrder(orderNo, false, userId);
    }

    @Override
    public void reportClientFailure(Long userId, VirtualPayClientFailureDTO dto) {
        if (userId == null || dto == null || isBlank(dto.getOrderNo())) {
            throw new ServiceException("支付失败诊断参数不完整");
        }
        PayOrder order = findOrder(dto.getOrderNo());
        if (!userId.equals(order.getUserId()) || !PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())) {
            throw new ServiceException("无权上报该订单诊断信息");
        }

        String errCode = safeLogValue(dto.getErrCode(), 64, "unknown");
        if (!claimClientFailureLog(order.getOrderNo())) {
            return;
        }
        String errMsg = safeLogValue(dto.getErrMsg(), 500, "");
        String rawError = safeLogValue(dto.getRawError(), 1000, "");
        String clientProductType = safeLogValue(dto.getProductType(), 32, "");
        boolean productMatches = isBlank(clientProductType)
                || (order.getProductType() != null
                && order.getProductType().equalsIgnoreCase(clientProductType));
        boolean productIdMatches = dto.getProductId() == null
                || dto.getProductId().equals(order.getProductId());

        String logPattern = "虚拟支付客户端失败: orderNo={}, userId={}, errCode={}, errMsg={}, rawError={}, "
                + "productType={}, productId={}, amountFen={}, payEnv={}, orderStatus={}, productMatches={}, "
                + "platform={}, system={}, model={}, wechatVersion={}, sdkVersion={}, "
                + "miniProgramVersion={}, envVersion={} ";
        Object[] args = {
                order.getOrderNo(), userId, errCode, errMsg, rawError,
                order.getProductType(), order.getProductId(), order.getAmount(), order.getEnv(), order.getStatus(),
                productMatches && productIdMatches,
                safeLogValue(dto.getPlatform(), 32, ""),
                safeLogValue(dto.getSystem(), 80, ""),
                safeLogValue(dto.getModel(), 80, ""),
                safeLogValue(dto.getWechatVersion(), 32, ""),
                safeLogValue(dto.getSdkVersion(), 32, ""),
                safeLogValue(dto.getMiniProgramVersion(), 32, ""),
                safeLogValue(dto.getEnvVersion(), 16, "")
        };
        if ("-2".equals(errCode)) {
            log.info(logPattern, args);
        } else {
            log.warn(logPattern, args);
        }
    }

    private String safeLogValue(String raw, int maxLength, String fallback) {
        if (raw == null) return fallback;
        String value = raw.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (value.isEmpty()) return fallback;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean claimClientFailureLog(String orderNo) {
        Instant now = Instant.now();
        AtomicBoolean claimed = new AtomicBoolean(false);
        clientFailureLogClaims.compute(orderNo, (ignored, previous) -> {
            if (previous == null || !previous.plus(CLIENT_FAILURE_LOG_INTERVAL).isAfter(now)) {
                claimed.set(true);
                return now;
            }
            return previous;
        });
        if ((clientFailureLogClaimCount.incrementAndGet() & 0xFF) == 0) {
            Instant staleBefore = now.minus(CLIENT_FAILURE_LOG_INTERVAL.multipliedBy(2));
            clientFailureLogClaims.entrySet().removeIf(entry -> entry.getValue().isBefore(staleBefore));
        }
        return claimed.get();
    }

    private boolean claimFulfilledRefundSync(String orderNo) {
        Instant now = Instant.now();
        AtomicBoolean claimed = new AtomicBoolean(false);
        fulfilledRefundSyncClaims.compute(orderNo, (ignored, previous) -> {
            if (previous == null
                    || !previous.plus(FULFILLED_REFUND_SYNC_INTERVAL).isAfter(now)) {
                claimed.set(true);
                return now;
            }
            return previous;
        });

        // 长运行节点上定期清理早已过期的限流键，防止随订单数无界增长。
        if ((fulfilledRefundSyncClaimCount.incrementAndGet() & 0xFF) == 0) {
            Instant staleBefore = now.minus(FULFILLED_REFUND_SYNC_INTERVAL.multipliedBy(2));
            fulfilledRefundSyncClaims.entrySet().removeIf(
                    entry -> entry.getValue().isBefore(staleBefore));
        }
        return claimed.get();
    }

    @Override
    public void handleGoodsDeliver(VirtualPayGoodsNotifyDTO notify) {
        if (notify == null || !EVENT_GOODS_DELIVER.equals(notify.getEvent())
                || isBlank(notify.getOutTradeNo()) || notify.getGoodsInfo() == null) {
            throw new ServiceException("虚拟支付发货通知格式错误");
        }
        PayOrder order = findOrder(notify.getOutTradeNo());
        VirtualPayGoodsNotifyDTO.GoodsInfo goods = notify.getGoodsInfo();

        requireEquals(PAY_CHANNEL_VIRTUAL, order.getPayChannel(), "订单支付渠道不匹配");
        requireEquals(order.getOpenId(), notify.getOpenId(), "支付用户不匹配");
        requireEquals(order.getEnv(), notify.getEnv(), "支付环境不匹配");
        requireEquals(order.getVirtualProductId(), goods.getProductId(), "支付道具不匹配");
        requireEquals(Integer.valueOf(1), goods.getQuantity(), "购买数量不匹配");
        requireEquals(order.getAmount(), goods.getOrigPrice(), "道具原价不匹配");
        requireEquals(order.getAmount(), goods.getActualPrice(), "实付金额不匹配");
        requireEquals(order.getOrderNo(), goods.getAttach(), "支付透传订单号不匹配");

        String transactionId = notify.getWeChatPayInfo() == null
                ? null : notify.getWeChatPayInfo().getTransactionId();
        LocalDateTime paidTime = fromWechatTimestamp(
                notify.getWeChatPayInfo() == null ? null : notify.getWeChatPayInfo().getPaidTime(),
                "PaidTime", LocalDateTime.now(BUSINESS_ZONE));
        try {
            payOrderBizService.handleVirtualPaySuccess(order.getOrderNo(), null,
                    transactionId, goods.getActualPrice(), paidTime);
        } catch (RuntimeException e) {
            markFulfillmentFailed(order.getId(), e.getMessage());
            throw e;
        }
        PayOrder latest = findOrder(order.getOrderNo());
        if (Integer.valueOf(1).equals(latest.getFulfillmentStatus())) {
            markDeliveryConfirmed(order.getId());
        } else if (Integer.valueOf(2).equals(latest.getStatus())) {
            log.info("虚拟支付订单已退款，忽略晚到发货通知: orderNo={}", latest.getOrderNo());
        } else {
            log.error("虚拟支付已扣款但权益未发放，跳过发货确认并等待退款: orderNo={}, reason={}",
                    latest.getOrderNo(), latest.getFulfillmentError());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefund(VirtualPayRefundNotifyDTO notify) {
        if (notify == null || notify.getRetCode() == null) {
            throw new ServiceException("虚拟支付退款通知格式错误");
        }
        // 失败结果只记录日志并让微信认为已接收；成功退款才更新订单。
        if (notify.getRetCode() != 0) {
            log.warn("虚拟支付退款失败,商户退款单={},原因={}", notify.getMchRefundId(), notify.getRetMsg());
            return;
        }

        PayOrder order;
        if (!isBlank(notify.getMchOrderId())) {
            order = findOrder(notify.getMchOrderId());
        } else if (!isBlank(notify.getWxOrderId())) {
            order = payOrderService.getOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getWxOrderId, notify.getWxOrderId()).last("LIMIT 1"));
            if (order == null) {
                throw new ServiceException("退款对应订单不存在");
            }
        } else {
            throw new ServiceException("退款通知缺少订单号");
        }

        requireEquals(PAY_CHANNEL_VIRTUAL, order.getPayChannel(), "退款订单支付渠道不匹配");
        if (!isBlank(notify.getOpenId())) {
            requireEquals(order.getOpenId(), notify.getOpenId(), "退款用户不匹配");
        }
        // 当前只售卖 quantity=1 的不可拆分普通道具，本地订单状态也只建模全额退款。
        // 不接受部分退款，避免把“部分退”误标为“已退款”并丢失后续事件。
        if (notify.getRefundFee() == null || !notify.getRefundFee().equals(order.getAmount())) {
            throw new ServiceException("虚拟道具仅支持全额退款");
        }

        LocalDateTime refundTime;
        if (notify.getRefundSuccTimestamp() != null && notify.getRefundSuccTimestamp() > 0) {
            refundTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(notify.getRefundSuccTimestamp()), BUSINESS_ZONE);
        } else {
            refundTime = LocalDateTime.now(BUSINESS_ZONE);
        }
        payOrderBizService.handleVirtualRefund(order.getOrderNo(),
                !isBlank(notify.getWxRefundId()) ? notify.getWxRefundId() : notify.getMchRefundId(),
                notify.getRefundFee(), refundTime);
        log.info("虚拟支付订单退款已同步，会员及豆子权益已自动处理: orderNo={}", order.getOrderNo());
    }

    private void syncFromWechat(PayOrder order) throws WxErrorException {
        SysWxConfig config = getVirtualPayConfig(false);
        validateConfig(config, false);
        int env = order.getEnv() == null ? config.getEnv() : order.getEnv();
        WxMaService maService = wxServiceFactory.getWxMaService();
        ensureAppIdMatches(config, maService);

        WxMaXPayQueryOrderRequest request = WxMaXPayQueryOrderRequest.builder()
                .openid(order.getOpenId())
                .env(env)
                .orderId(order.getOrderNo())
                .build();
        WxMaXPaySigParams sigParams = WxMaXPaySigParams.builder()
                .appKey(appKey(config, env))
                .build();
        WxMaXPayQueryOrderResponse response = maService.getWxMaXPayService()
                .queryOrder(request, sigParams);
        WxMaXPayQueryOrderResponse.OrderInfo info = response.getOrder();
        if (info == null || info.getStatus() == null) {
            return;
        }

        int status = info.getStatus();
        if (status == WxMaConstants.XPayOrderStatus.PAID
                || status == WxMaConstants.XPayOrderStatus.PROVIDING
                || status == WxMaConstants.XPayOrderStatus.PROVIDED) {
            if (info.getOrderFee() != null && info.getOrderFee() != order.getAmount().longValue()) {
                throw new ServiceException("微信虚拟支付订单原价不一致");
            }
            if (info.getPaidFee() == null || info.getPaidFee() != order.getAmount().longValue()) {
                throw new ServiceException("微信虚拟支付订单实付金额不一致");
            }
            try {
                payOrderBizService.handleVirtualPaySuccess(order.getOrderNo(), info.getWxOrderId(),
                        info.getWxpayOrderId(), Math.toIntExact(info.getPaidFee()),
                        fromWechatTimestamp(info.getPaidTime(), "paidTime",
                                LocalDateTime.now(BUSINESS_ZONE)));
            } catch (RuntimeException e) {
                markFulfillmentFailed(order.getId(), e.getMessage());
                throw e;
            }
            PayOrder paid = findOrder(order.getOrderNo());
            if (!Integer.valueOf(1).equals(paid.getFulfillmentStatus())) {
                log.error("虚拟支付查单发现已付款待退款订单，跳过发货确认: orderNo={}, reason={}",
                        paid.getOrderNo(), paid.getFulfillmentError());
                return;
            }
            if (status == WxMaConstants.XPayOrderStatus.PROVIDED) {
                markDeliveryConfirmed(order.getId());
            } else {
                notifyProvideGoods(paid, config);
                markDeliveryConfirmed(order.getId());
            }
        } else if (status == WxMaConstants.XPayOrderStatus.REFUNDED) {
            Integer refundFen = info.getRefundFee() == null
                    ? order.getAmount() : Math.toIntExact(info.getRefundFee());
            // 微信查单结果没有可靠的退款完成时间字段，不能把 paidTime 冒充退款时间。
            LocalDateTime refundTime = LocalDateTime.now(BUSINESS_ZONE);
            payOrderBizService.handleVirtualRefund(order.getOrderNo(), null, refundFen, refundTime);
        } else if (status == WxMaConstants.XPayOrderStatus.CLOSED) {
            payOrderBizService.handleVirtualClosed(order.getOrderNo());
        } else if (status == WxMaConstants.XPayOrderStatus.REFUND_FAILED) {
            log.warn("微信虚拟支付退款失败,订单号={}", order.getOrderNo());
        } else if (status != WxMaConstants.XPayOrderStatus.INIT
                && status != WxMaConstants.XPayOrderStatus.CREATED) {
            log.warn("微信虚拟支付返回未知订单状态,订单号={},状态={}", order.getOrderNo(), status);
        }
    }

    private void notifyProvideGoods(PayOrder order, SysWxConfig config) throws WxErrorException {
        if (isBlank(order.getWxOrderId())) {
            throw new ServiceException("微信虚拟订单号缺失，暂无法确认发货");
        }
        int env = order.getEnv() == null ? config.getEnv() : order.getEnv();
        WxMaService maService = wxServiceFactory.getWxMaService();
        ensureAppIdMatches(config, maService);
        WxMaXPayNotifyProvideGoodsRequest request = WxMaXPayNotifyProvideGoodsRequest.builder()
                .env(env)
                .wxOrderId(order.getWxOrderId())
                .build();
        WxMaXPaySigParams sigParams = WxMaXPaySigParams.builder()
                .appKey(appKey(config, env))
                .build();
        maService.getWxMaXPayService().notifyProvideGoods(request, sigParams);
    }

    private void markDeliveryConfirmed(Long orderId) {
        // 仅已支付且权益仍成功的订单可确认发货，避免退款并发后被旧请求覆盖回已发货。
        payOrderService.lambdaUpdate()
                .eq(PayOrder::getId, orderId)
                .eq(PayOrder::getStatus, 1)
                .eq(PayOrder::getFulfillmentStatus, 1)
                .eq(PayOrder::getDeliveryConfirmed, 0)
                .set(PayOrder::getDeliveryConfirmed, 1)
                .set(PayOrder::getDeliveryConfirmedAt, LocalDateTime.now(BUSINESS_ZONE))
                .update();
    }

    /**
     * 微信字段规范为 epoch 秒；兼容少量历史/测试数据误传的 13 位毫秒值。
     * 无效或越界值回退到调用时刻，保证支付回调仍可幂等补偿且不会受 JVM 默认时区影响。
     */
    private LocalDateTime fromWechatTimestamp(Long raw, String field, LocalDateTime fallback) {
        if (raw == null || raw <= 0) {
            log.warn("微信虚拟支付{}缺失，回退到当前履约时间", field);
            return fallback;
        }
        try {
            Instant instant = raw >= 100_000_000_000L
                    ? Instant.ofEpochMilli(raw)
                    : Instant.ofEpochSecond(raw);
            return LocalDateTime.ofInstant(instant, BUSINESS_ZONE);
        } catch (RuntimeException e) {
            log.warn("微信虚拟支付{}非法({})，回退到当前履约时间", field, raw);
            return fallback;
        }
    }

    private void markFulfillmentFailed(Long orderId, String reason) {
        String safeReason = isBlank(reason) ? "权益发放失败" : reason;
        String storedReason = safeReason.length() > 500 ? safeReason.substring(0, 500) : safeReason;
        // 只标记仍在等待履约的订单，避免失败回调 A 在成功回调 B 之后把状态覆盖回失败。
        payOrderService.lambdaUpdate()
                .eq(PayOrder::getId, orderId)
                .eq(PayOrder::getStatus, 0)
                .eq(PayOrder::getFulfillmentStatus, 0)
                .set(PayOrder::getFulfillmentStatus, 2)
                .set(PayOrder::getFulfillmentError, storedReason)
                .update();
    }

    private ProductSnapshot resolveProduct(String rawType, Long productId) {
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        if (PRODUCT_TYPE_MEMBERSHIP.equals(type)) {
            MembershipLevel level = membershipLevelService.getById(productId);
            if (level == null || !"0".equals(level.getStatus())) {
                throw new ServiceException("会员商品不存在或已下架");
            }
            validateVirtualProduct(level.getVirtualProductId());
            if (level.getGrantBeans() == null || level.getGrantBeans() < 0
                    || level.getSort() == null || isBlank(level.getName())
                    || !MembershipPolicy.isValidDurationDays(level.getDurationDays())) {
                throw new ServiceException("会员商品权益配置不完整");
            }
            return new ProductSnapshot(type, productId, level.getName(), level.getVirtualProductId(),
                    toFen(level.getPrice()), null, level.getDurationDays(),
                    level.getGrantBeans(), level.getSort());
        }
        if (PRODUCT_TYPE_BEAN_RECHARGE.equals(type)) {
            BeanRechargePackage pkg = beanRechargePackageService.getById(productId);
            if (pkg == null || !"0".equals(pkg.getStatus())) {
                throw new ServiceException("豆子套餐不存在或已下架");
            }
            validateVirtualProduct(pkg.getVirtualProductId());
            if (pkg.getBeans() == null || pkg.getBeans() <= 0 || isBlank(pkg.getName())) {
                throw new ServiceException("豆子套餐权益配置不完整");
            }
            return new ProductSnapshot(type, productId, pkg.getName(), pkg.getVirtualProductId(),
                    toFen(pkg.getPrice()), pkg.getBeans(), null, null, null);
        }
        throw new ServiceException("不支持的虚拟支付商品类型");
    }

    private String buildSignData(String offerId, int env, ProductSnapshot product, String orderNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offerId", offerId);
        data.put("buyQuantity", 1);
        data.put("env", env);
        data.put("currencyType", "CNY");
        data.put("productId", product.virtualProductId());
        data.put("goodsPrice", product.amountFen());
        data.put("outTradeNo", orderNo);
        data.put("attach", orderNo);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new ServiceException("虚拟支付参数生成失败");
        }
    }

    private SysWxConfig getVirtualPayConfig(boolean requireActive) {
        SysWxConfig miniApp = sysWxConfigService.getActiveByType("mini_app");
        if (miniApp == null || isBlank(miniApp.getAppId())) {
            throw new ServiceException("已启用的小程序配置不存在");
        }
        LambdaQueryWrapper<SysWxConfig> wrapper = new LambdaQueryWrapper<SysWxConfig>()
                .eq(SysWxConfig::getAppType, "virtual_pay")
                .eq(SysWxConfig::getAppId, miniApp.getAppId())
                .eq(requireActive, SysWxConfig::getIsActive, 1)
                .orderByDesc(SysWxConfig::getIsActive)
                .orderByDesc(SysWxConfig::getUpdateTime)
                .last("LIMIT 1");
        SysWxConfig config = sysWxConfigService.getOne(wrapper);
        if (config == null) {
            throw new ServiceException(requireActive ? "虚拟支付尚未启用" : "虚拟支付配置不存在");
        }
        return config;
    }

    private void validateConfig(SysWxConfig config, boolean requirePushConfig) {
        if (isBlank(config.getAppId()) || isBlank(config.getOfferId())
                || (config.getEnv() == null || (config.getEnv() != 0 && config.getEnv() != 1))) {
            throw new ServiceException("虚拟支付 AppId、OfferId 或环境配置不完整");
        }
        appKey(config, config.getEnv());
        ensureAppIdMatches(config, wxServiceFactory.getWxMaService());
        if (requirePushConfig) {
            String token = decryptRequired(config.getPushToken(), "虚拟支付消息推送 Token 未配置");
            String aesKey = decryptRequired(config.getPushAesKey(), "虚拟支付消息推送 EncodingAESKey 未配置");
            if (token.length() < 3 || token.length() > 32 || aesKey.replace(" ", "").length() != 43) {
                throw new ServiceException("虚拟支付消息推送 Token 或 EncodingAESKey 格式错误");
            }
        }
    }

    private void ensureAppIdMatches(SysWxConfig config, WxMaService maService) {
        String miniAppId = maService.getWxMaConfig().getAppid();
        if (!config.getAppId().equals(miniAppId)) {
            throw new ServiceException("虚拟支付 AppId 与小程序 AppId 不一致");
        }
    }

    private String appKey(SysWxConfig config, int env) {
        if (env != 0 && env != 1) {
            throw new ServiceException("虚拟支付环境配置错误");
        }
        String encrypted = env == 1 ? config.getSandboxAppKey() : config.getProdAppKey();
        return decryptRequired(encrypted, env == 1
                ? "虚拟支付沙箱 AppKey 未配置" : "虚拟支付现网 AppKey 未配置");
    }

    private String decryptRequired(String encrypted, String message) {
        if (isBlank(encrypted)) {
            throw new ServiceException(message);
        }
        String plain = encryptUtils.decrypt(encrypted);
        if (isBlank(plain) || plain.startsWith("ENC:") || plain.startsWith("ENC2:")) {
            throw new ServiceException(message);
        }
        return plain;
    }

    private PayOrder findOrder(String orderNo) {
        PayOrder order = payOrderService.getOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, orderNo).last("LIMIT 1"));
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        return order;
    }

    private void closeReplacedOrder(Long userId, String orderNo) {
        if (isBlank(orderNo)) {
            return;
        }
        // 单条条件更新，避免“先查待支付、支付回调置成功、再覆盖成关闭”的竞态。
        payOrderService.lambdaUpdate()
                .eq(PayOrder::getOrderNo, orderNo)
                .eq(PayOrder::getUserId, userId)
                .eq(PayOrder::getPayChannel, PAY_CHANNEL_VIRTUAL)
                .eq(PayOrder::getStatus, 0)
                .set(PayOrder::getStatus, 3)
                .update();
    }

    private int toFen(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("商品价格未配置");
        }
        try {
            return price.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceException("商品价格必须精确到分");
        }
    }

    private void validateVirtualProduct(String productId) {
        if (isBlank(productId) || !PRODUCT_ID_PATTERN.matcher(productId).matches()) {
            throw new ServiceException("商品微信道具ID未正确配置");
        }
    }

    private void requireEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new ServiceException(message);
        }
    }

    private String generateOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return ts + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProductSnapshot(String productType, Long productId, String name,
                                   String virtualProductId, Integer amountFen, Long beans,
                                   Integer durationDays, Long grantBeans, Integer levelSort) {
    }
}
