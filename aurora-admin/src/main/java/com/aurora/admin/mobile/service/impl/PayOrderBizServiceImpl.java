package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.exception.MembershipRequiredException;
import com.aurora.admin.mobile.mapper.PayOrderMapper;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.IMembershipRefundEntitlementService;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.service.IPayOrderService;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import com.aurora.admin.mobile.service.MembershipRefundEntitlementResult;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.PayParamsVO;
import com.aurora.admin.mobile.vo.PurchaseRecordVO;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微信支付业务 Service 实现
 * 下单(JSAPI)→ 回调(验签解密由 Controller 完成,本类做行锁+幂等+金额校验)→ 查单补单。
 */
@Slf4j
@Service
public class PayOrderBizServiceImpl implements IPayOrderBizService {

    /** 订单类型:会员 */
    private static final String PRODUCT_TYPE_MEMBERSHIP = "MEMBERSHIP";
    /** 订单类型:豆子充值 */
    private static final String PRODUCT_TYPE_BEAN_RECHARGE = "BEAN_RECHARGE";
    /** 支付渠道:传统微信支付 */
    private static final String PAY_CHANNEL_WXPAY = "WXPAY";
    /** 支付渠道:微信虚拟支付 */
    private static final String PAY_CHANNEL_VIRTUAL = "VIRTUAL";
    /** 已扣款但因会员在支付期间到期而未履约，必须进入人工退款队列。 */
    private static final int ORDER_STATUS_PAID_PENDING_REFUND = 4;

    private final WxServiceFactory wxServiceFactory;
    private final IPayOrderService payOrderService;
    private final IMobileUserService mobileUserService;
    private final PayOrderMapper payOrderMapper;
    private final IUserMembershipService userMembershipService;
    private final IBeanAccountService beanAccountService;
    private final IMembershipLevelService membershipLevelService;
    private final IMembershipRefundEntitlementService membershipRefundEntitlementService;
    private final IBeanRechargePackageService beanRechargePackageService;
    private final ISysWxConfigService sysWxConfigService;
    private final IDistributionFinanceService distributionFinanceService;
    /**
     * 自身代理引用(@Lazy 防循环依赖):queryOrder 走 self.handlePaySuccess 才能命中
     * @Transactional 事务边界 + 行锁(同类内直接 this 调用会绕过 Spring 代理)。
     */
    private final IPayOrderBizService self;

    public PayOrderBizServiceImpl(WxServiceFactory wxServiceFactory,
                                  IPayOrderService payOrderService,
                                  IMobileUserService mobileUserService,
                                  PayOrderMapper payOrderMapper,
                                  IUserMembershipService userMembershipService,
                                  IBeanAccountService beanAccountService,
                                  IMembershipLevelService membershipLevelService,
                                  IMembershipRefundEntitlementService membershipRefundEntitlementService,
                                  @Lazy IBeanRechargePackageService beanRechargePackageService,
                                  ISysWxConfigService sysWxConfigService,
                                  IDistributionFinanceService distributionFinanceService,
                                  @Lazy IPayOrderBizService self) {
        this.wxServiceFactory = wxServiceFactory;
        this.payOrderService = payOrderService;
        this.mobileUserService = mobileUserService;
        this.payOrderMapper = payOrderMapper;
        this.userMembershipService = userMembershipService;
        this.beanAccountService = beanAccountService;
        this.membershipLevelService = membershipLevelService;
        this.membershipRefundEntitlementService = membershipRefundEntitlementService;
        this.beanRechargePackageService = beanRechargePackageService;
        this.sysWxConfigService = sysWxConfigService;
        this.distributionFinanceService = distributionFinanceService;
        this.self = self;
    }

    @Override
    public CreateOrderVO createOrder(Long userId, Integer amount, String subject) {
        // 老支付页兼容:不带商品类型,支付成功后不做业务流转
        return createOrder(userId, amount, subject, null, null, null);
    }

    @Override
    public CreateOrderVO createOrder(Long userId, Integer amount, String subject,
                                     String productType, Long productId, String productName) {
        if (sysWxConfigService.getActiveByType("virtual_pay") != null) {
            throw new ServiceException("支付方式已升级，请重新打开或更新小程序后再试");
        }
        if (amount == null || amount <= 0) {
            throw new ServiceException("支付金额必须大于 0");
        }
        if (subject == null || subject.isEmpty()) {
            throw new ServiceException("商品描述不能为空");
        }
        if (PRODUCT_TYPE_BEAN_RECHARGE.equals(productType)) {
            // 下单时先拦截；支付回调仍会再次校验，覆盖支付期间恰好到期的边界。
            userMembershipService.requireActiveMembership(userId);
        }

        // openid 服务端从用户表取,绝不信任前端传值
        MobileUser user = mobileUserService.getById(userId);
        if (user == null || user.getWxOpenid() == null || user.getWxOpenid().isEmpty()) {
            throw new ServiceException("用户未绑定微信,无法发起支付");
        }
        String openid = user.getWxOpenid();

        String orderNo = generateOrderNo();
        // 1. 落地本地订单(待支付),记录商品类型供支付成功后分发业务流转
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOpenId(openid);
        order.setAmount(amount);
        order.setSubject(subject);
        order.setProductType(productType);
        order.setProductId(productId);
        order.setProductName(productName);
        order.setPayChannel(PAY_CHANNEL_WXPAY);
        populateProductSnapshots(order);
        if (productType != null && !productType.isEmpty()) {
            order.setFulfillmentStatus(0);
        }
        order.setStatus(0);
        // 下单时间显式落库:实体未接审计自动填充,不设则 create_time 为 NULL(后台/购买记录都要展示)
        order.setCreateTime(MembershipPolicy.now());
        payOrderService.save(order);

        WxPayService wxPayService = wxServiceFactory.getWxPayService();
        try {
            // 2. 组装 V3 JSAPI 下单请求(appid/mchid/notifyUrl 不填,自动用 WxPayConfig 兜底)
            WxPayUnifiedOrderV3Request req = new WxPayUnifiedOrderV3Request();
            req.setDescription(subject);
            req.setOutTradeNo(orderNo);

            WxPayUnifiedOrderV3Request.Amount amt = new WxPayUnifiedOrderV3Request.Amount();
            amt.setTotal(amount);
            amt.setCurrency("CNY");
            req.setAmount(amt);

            WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
            payer.setOpenid(openid);
            req.setPayer(payer);

            // 3. 下单:JSAPI 返回 JsapiResult,已自动算好 paySign(RSA)
            WxPayUnifiedOrderV3Result.JsapiResult r =
                    wxPayService.createOrderV3(TradeTypeEnum.JSAPI, req);

            // 4. 组装给前端 wx.requestPayment 的参数(注意键名 package / paySign)
            PayParamsVO params = new PayParamsVO();
            params.setAppId(r.getAppId());
            params.setTimeStamp(r.getTimeStamp());
            params.setNonceStr(r.getNonceStr());
            params.setPackageValue(r.getPackageValue());
            params.setSignType(r.getSignType());
            params.setPaySign(r.getPaySign());

            CreateOrderVO vo = new CreateOrderVO();
            vo.setOrderNo(orderNo);
            vo.setPayParams(params);
            return vo;
        } catch (WxPayException e) {
            // 下单失败关闭本地订单
            order.setStatus(3);
            payOrderService.updateById(order);
            log.warn("微信支付下单失败,订单号={},原因={}", orderNo, e.getMessage());
            throw new ServiceException("微信支付下单失败:" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaySuccess(String orderNo, String transactionId, String tradeState, Integer paidFen) {
        // 非成功态忽略
        if (!"SUCCESS".equals(tradeState)) {
            return;
        }

        completePaidOrder(orderNo, null, transactionId, paidFen, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleVirtualPaySuccess(String orderNo, String wxOrderId,
                                        String transactionId, Integer paidFen) {
        handleVirtualPaySuccess(orderNo, wxOrderId, transactionId, paidFen, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleVirtualPaySuccess(String orderNo, String wxOrderId,
                                        String transactionId, Integer paidFen,
                                        LocalDateTime paidTime) {
        completePaidOrder(orderNo, wxOrderId, transactionId, paidFen, paidTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleVirtualRefund(String orderNo, String refundNo,
                                    Integer refundFen, LocalDateTime refundTime) {
        PayOrder order = payOrderMapper.findByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new ServiceException("退款对应订单不存在");
        }
        if (!PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())) {
            throw new ServiceException("退款订单支付渠道不匹配");
        }
        if (refundFen == null || !refundFen.equals(order.getAmount())) {
            throw new ServiceException("虚拟道具仅支持全额退款");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            if (!isBlank(refundNo) && !isBlank(order.getRefundNo())
                    && !refundNo.equals(order.getRefundNo())) {
                throw new ServiceException("同一订单收到不同退款单号");
            }
            return;
        }
        boolean wasFulfilled = Integer.valueOf(1).equals(order.getStatus());
        boolean wasPendingRefund = Integer.valueOf(ORDER_STATUS_PAID_PENDING_REFUND).equals(order.getStatus());
        LocalDateTime effectiveRefundTime = refundTime == null ? MembershipPolicy.now() : refundTime;
        LocalDateTime processedAt = MembershipPolicy.now();

        if (wasFulfilled) {
            revokeFulfilledEntitlement(order, effectiveRefundTime, processedAt);
            // 与权益撤销、订单退款状态共用事务；失败时整体回滚并等待微信重推。
            distributionFinanceService.reverseRefundedOrder(order, refundNo, effectiveRefundTime);
        }

        order.setStatus(2);
        order.setRefundNo(refundNo);
        order.setRefundAmount(refundFen);
        order.setRefundTime(effectiveRefundTime);
        if (wasFulfilled) {
            order.setFulfillmentStatus(2);
            order.setFulfillmentError("订单已退款，会员及豆子权益已自动撤销");
        } else if (wasPendingRefund) {
            order.setFulfillmentError("订单已退款，未发放权益");
        }
        payOrderService.updateById(order);
    }

    /**
     * 微信平台全额退款后的自动权益撤销。与 pay_order 状态更新处于同一事务：任一步失败全部回滚，
     * 微信可继续重推；重复成功通知由订单状态和 REVOKE 流水双重幂等。
     */
    private void revokeFulfilledEntitlement(PayOrder order,
                                            LocalDateTime refundTime,
                                            LocalDateTime processedAt) {
        if (PRODUCT_TYPE_MEMBERSHIP.equals(order.getProductType())) {
            MembershipRefundEntitlementResult result = membershipRefundEntitlementService.cancelForRefund(
                    order.getUserId(), order.getOrderNo(), refundTime, processedAt);
            if (!result.targetFound()) {
                throw new ServiceException("退款会员权益记录不存在，暂未确认退款状态");
            }
            // 只撤销该会员订单尚未消费的赠豆。加油包豆永久有效，即使退款后已无有效会员
            // 也不能清除与本订单无关的加油包或注册赠豆。
            beanAccountService.revokeForOrderRefund(order.getUserId(),
                    result.cancelledGrantBeans(), order.getOrderNo(),
                    "会员订单退款，撤销该订单尚未消费的赠豆");
            return;
        }
        if (PRODUCT_TYPE_BEAN_RECHARGE.equals(order.getProductType())) {
            long beans = order.getSnapshotBeans() == null ? 0L : Math.max(0L, order.getSnapshotBeans());
            beanAccountService.revokeForOrderRefund(order.getUserId(), beans,
                    order.getOrderNo(), "加油包订单退款，撤销该订单尚未消费的豆子");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleVirtualClosed(String orderNo) {
        PayOrder order = payOrderMapper.findByOrderNoForUpdate(orderNo);
        if (order != null && PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())
                && Integer.valueOf(0).equals(order.getStatus())) {
            order.setStatus(3);
            payOrderService.updateById(order);
        }
    }

    /**
     * 两种支付渠道共用的完成支付逻辑：行锁、幂等、金额校验、按快照履约。
     */
    private void completePaidOrder(String orderNo, String wxOrderId,
                                   String transactionId, Integer paidFen,
                                   LocalDateTime paidTime) {

        // 行锁查询,防并发回调竞态
        PayOrder order = payOrderMapper.findByOrderNoForUpdate(orderNo);
        if (order == null) {
            log.warn("支付回调:订单不存在 {}", orderNo);
            return;
        }

        // 幂等:已支付仅补齐可能由后续查单拿到的微信单号,不重复发放。
        if (order.getStatus() != null && order.getStatus() == 1) {
            boolean changed = false;
            if (isBlank(order.getWxOrderId()) && !isBlank(wxOrderId)) {
                order.setWxOrderId(wxOrderId);
                changed = true;
            }
            if (isBlank(order.getTransactionId()) && !isBlank(transactionId)) {
                order.setTransactionId(transactionId);
                changed = true;
            }
            if (changed) {
                payOrderService.updateById(order);
            }
            return;
        }
        if (Integer.valueOf(ORDER_STATUS_PAID_PENDING_REFUND).equals(order.getStatus())) {
            return;
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            // 退款通知可能先于延迟的 goods deliver 到达；本地终态优先，幂等吞掉晚到发货通知，
            // 让消息入口正常返回 success，避免微信无意义重复推送。
            log.info("忽略已退款虚拟订单的晚到支付/发货通知: orderNo={}", orderNo);
            return;
        }
        if (PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())
                && Integer.valueOf(3).equals(order.getStatus())) {
            // -15007 重建或微信权威查单已关闭的旧单不得被晚到回调“复活”，
            // 否则新旧两张订单都会发放权益。
            throw new ServiceException("虚拟支付订单已关闭，禁止再次发放权益");
        }

        // 金额一致性校验
        if (paidFen == null || !paidFen.equals(order.getAmount())) {
            throw new ServiceException("金额不一致 订单=" + order.getAmount() + " 实付=" + paidFen);
        }

        if (!isBlank(wxOrderId)) {
            order.setWxOrderId(wxOrderId);
        }
        order.setTransactionId(transactionId);
        LocalDateTime fulfilledAt = MembershipPolicy.now();
        LocalDateTime effectivePaidTime = paidTime == null ? fulfilledAt : paidTime;
        order.setPayTime(effectivePaidTime);

        // 先按订单快照发放；一般失败回滚重试。会员支付期间到期则落“已支付待退款”终态。
        try {
            dispatchBusiness(order, effectivePaidTime, fulfilledAt);
        } catch (MembershipRequiredException e) {
            order.setStatus(ORDER_STATUS_PAID_PENDING_REFUND);
            order.setFulfillmentStatus(2);
            order.setFulfillmentError("支付完成时会员已到期，未发放加油包，请人工退款");
            payOrderService.updateById(order);
            log.error("加油包已支付但会员资格已失效，订单进入待退款队列: orderNo={}, userId={}",
                    order.getOrderNo(), order.getUserId());
            return;
        }

        order.setStatus(1);
        if (order.getProductType() != null && !order.getProductType().isEmpty()) {
            order.setFulfillmentStatus(1);
            order.setFulfillmentError(null);
            order.setFulfilledAt(fulfilledAt);
        }
        payOrderService.updateById(order);
        // 必须位于权益履约和订单已支付落库之后，确保只有真正成功订单产生佣金。
        distributionFinanceService.recordPaidOrder(order);
        log.info("支付成功并完成履约:{} 渠道={} 金额={}分", orderNo, order.getPayChannel(), paidFen);
    }

    /**
     * 支付成功后按订单类型分发业务流转(在 handlePaySuccess 同一事务内调用)。
     * - MEMBERSHIP:查会员等级取时长,叠加开通会员段并发订阅豆。
     * - BEAN_RECHARGE:查充值套餐取豆数,充值到账。
     * - productType 为空(老订单):不流转。
     * 任一失败抛 ServiceException,触发外层事务回滚。
     */
    private void dispatchBusiness(PayOrder order, LocalDateTime paidTime, LocalDateTime fulfilledAt) {
        String productType = order.getProductType();
        if (productType == null || productType.isEmpty()) {
            return;
        }
        Long productId = order.getProductId();
        if (productId == null) {
            throw new ServiceException("订单缺少商品ID,无法流转 订单=" + order.getOrderNo());
        }

        if (PRODUCT_TYPE_MEMBERSHIP.equals(productType)) {
            // 新订单完全使用下单快照；兼容迁移前已创建但尚未回调的存量订单，缺快照时才回查一次配置。
            MembershipLevel fallback = null;
            if (order.getSnapshotGrantBeans() == null
                    || order.getSnapshotLevelSort() == null || isBlank(order.getProductName())) {
                fallback = membershipLevelService.getById(productId);
                if (fallback == null) {
                    throw new ServiceException("会员订单权益快照不完整且等级已不存在 order=" + order.getOrderNo());
                }
            }
            Long grantBeans = order.getSnapshotGrantBeans() != null
                    ? order.getSnapshotGrantBeans() : fallback.getGrantBeans();
            Integer durationDays = order.getSnapshotDurationDays() != null
                    ? order.getSnapshotDurationDays() : MembershipPolicy.DURATION_DAYS;
            Integer levelSort = order.getSnapshotLevelSort() != null
                    ? order.getSnapshotLevelSort() : fallback.getSort();
            String levelName = !isBlank(order.getProductName())
                    ? order.getProductName() : fallback.getName();
            BigDecimal snapshotPrice = BigDecimal.valueOf(order.getAmount(), 2);
            userMembershipService.grantFromPayment(order.getUserId(), productId, levelSort,
                    levelName, grantBeans, snapshotPrice, durationDays,
                    order.getOrderNo(), paidTime, fulfilledAt);
        } else if (PRODUCT_TYPE_BEAN_RECHARGE.equals(productType)) {
            // 付款期间会员可能到期，履约必须在同一事务中重新结算并校验后再加豆。
            userMembershipService.requireActiveMembership(order.getUserId());
            Long beans = order.getSnapshotBeans();
            if (beans == null) {
                BeanRechargePackage fallback = beanRechargePackageService.getById(productId);
                if (fallback == null) {
                    throw new ServiceException("豆子订单权益快照不完整且套餐已不存在 order=" + order.getOrderNo());
                }
                beans = fallback.getBeans();
            }
            beanAccountService.recharge(order.getUserId(), beans,
                    order.getOrderNo(), "充值:" + order.getProductName());
        } else {
            throw new ServiceException("未知订单类型 " + productType + " 订单=" + order.getOrderNo());
        }
    }

    /**
     * 下单时固化权益，避免支付期间后台改价、改豆或删商品影响履约。
     */
    private void populateProductSnapshots(PayOrder order) {
        if (order.getProductType() == null || order.getProductId() == null) {
            return;
        }
        if (PRODUCT_TYPE_MEMBERSHIP.equals(order.getProductType())) {
            MembershipLevel level = membershipLevelService.getById(order.getProductId());
            if (level == null) {
                throw new ServiceException("会员等级不存在或已删除");
            }
            if (!MembershipPolicy.isValidDurationDays(level.getDurationDays())) {
                throw new ServiceException("会员等级时长配置不完整");
            }
            order.setSnapshotDurationDays(level.getDurationDays());
            order.setSnapshotGrantBeans(level.getGrantBeans());
            order.setSnapshotLevelSort(level.getSort());
        } else if (PRODUCT_TYPE_BEAN_RECHARGE.equals(order.getProductType())) {
            BeanRechargePackage pkg = beanRechargePackageService.getById(order.getProductId());
            if (pkg == null) {
                throw new ServiceException("充值套餐不存在或已删除");
            }
            order.setSnapshotBeans(pkg.getBeans());
        }
    }

    @Override
    public OrderVO queryOrder(String orderNo, boolean syncWechat, Long userId) {
        PayOrder order = payOrderService.getOne(
                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        // 归属校验:移动端必须是本人订单
        if (userId != null && !userId.equals(order.getUserId())) {
            throw new ServiceException("无权查看该订单");
        }

        // 待支付状态下主动同步微信,防止回调丢失
        if (syncWechat && order.getStatus() != null && order.getStatus() == 0
                && !PAY_CHANNEL_VIRTUAL.equals(order.getPayChannel())) {
            try {
                WxPayService wxPayService = wxServiceFactory.getWxPayService();
                // 仅按商户订单号查时,第一个参数 transactionId 传 null
                WxPayOrderQueryV3Result wx = wxPayService.queryOrderV3(null, orderNo);
                String state = wx.getTradeState();
                if ("SUCCESS".equals(state)) {
                    // 走 self 代理调 handlePaySuccess,复用其 @Transactional+行锁+幂等+金额校验+业务流转(发豆/开会员);
                    // 本地无回调时查单也能补单。直接 this.handlePaySuccess 会绕过代理丢失事务,故必须经 self。
                    Integer paidFen = wx.getAmount() == null ? null : wx.getAmount().getTotal();
                    self.handlePaySuccess(orderNo, wx.getTransactionId(), "SUCCESS", paidFen);
                    // 重新读取最新状态用于返回
                    order = payOrderService.getById(order.getId());
                } else if ("CLOSED".equals(state) || "REVOKED".equals(state) || "PAYERROR".equals(state)) {
                    order.setStatus(3);
                    payOrderService.updateById(order);
                }
            } catch (Exception e) {
                // 查询失败不影响返回本地数据
                log.warn("微信订单查询失败,订单号={},原因={}", orderNo, e.getMessage());
            }
        }

        return toOrderVO(order);
    }

    @Override
    public IPage<PurchaseRecordVO> listMyOrders(Long userId, int pageNum, int pageSize) {
        // 分页参数收口:页码≥1,每页 1~50
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        Page<PayOrder> page = payOrderService.page(new Page<>(current, size),
                Wrappers.<PayOrder>lambdaQuery()
                        .eq(PayOrder::getUserId, userId)
                        .orderByDesc(PayOrder::getCreateTime));
        return page.convert(this::toPurchaseRecordVO);
    }

    /**
     * 实体转购买记录视图(不含 openId/交易号等敏感与内部字段)
     */
    private PurchaseRecordVO toPurchaseRecordVO(PayOrder order) {
        PurchaseRecordVO vo = new PurchaseRecordVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setProductType(order.getProductType());
        vo.setProductName(order.getProductName());
        vo.setSubject(order.getSubject());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setPayTime(order.getPayTime());
        vo.setFulfillmentStatus(order.getFulfillmentStatus());
        vo.setFulfillmentError(order.getFulfillmentError());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    /**
     * 商户订单号:年月日时分秒 + 6 位随机
     */
    private String generateOrderNo() {
        String ts = MembershipPolicy.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return ts + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /**
     * 实体转订单视图,附状态文案
     */
    private OrderVO toOrderVO(PayOrder order) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setAmount(order.getAmount());
        vo.setSubject(order.getSubject());
        vo.setPayTime(order.getPayTime());
        vo.setFulfillmentStatus(order.getFulfillmentStatus());
        vo.setFulfillmentError(order.getFulfillmentError());
        return vo;
    }

    /**
     * 订单状态文案
     */
    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已退款";
            case 3 -> "已关闭";
            case ORDER_STATUS_PAID_PENDING_REFUND -> "已支付待退款";
            default -> "未知";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
