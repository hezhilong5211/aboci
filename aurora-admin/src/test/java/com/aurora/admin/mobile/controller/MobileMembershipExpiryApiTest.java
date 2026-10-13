package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
import com.aurora.admin.mobile.service.IBeanTransactionService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.IMobileLoginService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.service.IVirtualPayService;
import com.aurora.admin.mobile.vo.BeanAccountVO;
import com.aurora.admin.mobile.vo.MembershipInfoVO;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mobile.MobileJwtFilter;
import com.aurora.framework.web.exception.GlobalExceptionHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 移动端会员到期 HTTP 契约：会员状态、账户余额、EXPIRE 流水和非会员加油包拦截。
 */
class MobileMembershipExpiryApiTest {

    private static final long USER_ID = 1001L;

    private MockMvc mockMvc;
    private IUserMembershipService userMembershipService;
    private IBeanAccountService beanAccountService;
    private IBeanTransactionService beanTransactionService;
    private IMembershipExpiryService membershipExpiryService;
    private IVirtualPayService virtualPayService;
    private IMembershipLevelService membershipLevelService;
    private IBeanRechargePackageService beanRechargePackageService;

    @BeforeEach
    void setUp() {
        IMobileLoginService mobileLoginService = mock(IMobileLoginService.class);
        userMembershipService = mock(IUserMembershipService.class);
        beanAccountService = mock(IBeanAccountService.class);
        beanTransactionService = mock(IBeanTransactionService.class);
        beanRechargePackageService = mock(IBeanRechargePackageService.class);
        membershipExpiryService = mock(IMembershipExpiryService.class);
        virtualPayService = mock(IVirtualPayService.class);
        membershipLevelService = mock(IMembershipLevelService.class);
        IPayOrderBizService payOrderBizService = mock(IPayOrderBizService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MembershipMobileController(membershipLevelService, payOrderBizService),
                        new MobileUserController(mobileLoginService, userMembershipService),
                        new MobileBeanController(beanAccountService, beanTransactionService,
                                beanRechargePackageService, membershipExpiryService, userMembershipService),
                        new VirtualPayController(virtualPayService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 会员商品对外展示等级实际配置时长() throws Exception {
        MembershipLevel level = new MembershipLevel();
        level.setLevelId(1L);
        level.setName("青铜会员");
        level.setPrice(new BigDecimal("1.99"));
        level.setDurationDays(99);
        level.setGrantBeans(2000L);
        level.setStatus("0");
        when(membershipLevelService.listOnSale()).thenReturn(List.of(level));

        mockMvc.perform(get("/api/mobile/membership/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("青铜会员"))
                .andExpect(jsonPath("$.data[0].price").value(1.99))
                .andExpect(jsonPath("$.data[0].durationDays").value(99))
                .andExpect(jsonPath("$.data[0].grantBeans").value(2000));
    }

    @Test
    void 最后会员段到期后_会员接口返回非会员_账户接口返回零余额() throws Exception {
        MembershipInfoVO membership = new MembershipInfoVO();
        membership.setRemainDays(0);
        when(userMembershipService.getCurrentMembership(USER_ID)).thenReturn(membership);

        BeanAccountVO account = new BeanAccountVO();
        account.setBalance(0L);
        account.setTotalEarned(4050L);
        account.setTotalConsumed(600L);
        account.setTotalExpired(3450L);
        when(beanAccountService.getAccount(USER_ID)).thenReturn(account);

        mockMvc.perform(get("/api/mobile/user/membership")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.levelName").doesNotExist())
                .andExpect(jsonPath("$.data.expireTime").doesNotExist())
                .andExpect(jsonPath("$.data.remainDays").value(0));

        mockMvc.perform(get("/api/mobile/bean/balance")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.totalEarned").value(4050))
                .andExpect(jsonPath("$.data.totalConsumed").value(600))
                .andExpect(jsonPath("$.data.totalExpired").value(3450));

        verify(membershipExpiryService).settleUserIfExpired(USER_ID);
    }

    @Test
    void 豆子流水接口返回唯一会员赠豆EXPIRE记录() throws Exception {
        BeanTransaction expire = new BeanTransaction();
        expire.setId(99L);
        expire.setUserId(USER_ID);
        expire.setType("EXPIRE");
        expire.setChangeValue(-3450L);
        expire.setBalanceAfter(0L);
        expire.setBizNo("membership-expire:1001:1769904000");
        expire.setRemark("会员到期，仅失效剩余会员赠豆；加油包豆永久有效");
        expire.setCreateTime(LocalDateTime.of(2026, 2, 1, 0, 0));
        Page<BeanTransaction> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(expire));
        when(beanTransactionService.pageByUser(USER_ID, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/mobile/bean/transactions")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("EXPIRE"))
                .andExpect(jsonPath("$.data.records[0].changeValue").value(-3450))
                .andExpect(jsonPath("$.data.records[0].balanceAfter").value(0))
                .andExpect(jsonPath("$.data.records[0].remark")
                        .value("会员到期，仅失效剩余会员赠豆；加油包豆永久有效"));

        verify(membershipExpiryService).settleUserIfExpired(USER_ID);
    }

    @Test
    void 非会员创建加油包虚拟支付订单被拒绝() throws Exception {
        doThrow(new ServiceException("加油包仅限有效会员购买"))
                .when(virtualPayService).create(eq(USER_ID), any());

        mockMvc.perform(post("/api/mobile/vpay/create")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "BEAN_RECHARGE",
                                  "productId": 2,
                                  "clientPlatform": "android"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("加油包仅限有效会员购买"));

        verify(virtualPayService).create(eq(USER_ID), any());
    }

    @Test
    void 虚拟支付客户端失败上报保留微信原始错误() throws Exception {
        mockMvc.perform(post("/api/mobile/vpay/client-failure")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "20260813131604441184",
                                  "errCode": "-4",
                                  "errMsg": "requestVirtualPayment:fail risk control",
                                  "rawError": "{\\"errCode\\":-4,\\"errMsg\\":\\"requestVirtualPayment:fail risk control\\"}",
                                  "productType": "BEAN_RECHARGE",
                                  "productId": 2,
                                  "platform": "ios",
                                  "system": "iOS 18.7",
                                  "wechatVersion": "8.0.67",
                                  "sdkVersion": "3.8.10",
                                  "miniProgramVersion": "2.0.0",
                                  "envVersion": "release"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(virtualPayService).reportClientFailure(eq(USER_ID), any());
    }

    @Test
    void 非会员访问加油包列表被拒绝() throws Exception {
        doThrow(new ServiceException("加油包仅限有效会员购买"))
                .when(userMembershipService).requireActiveMembership(USER_ID);

        mockMvc.perform(get("/api/mobile/bean/package/list")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("加油包仅限有效会员购买"));

        verify(beanRechargePackageService, never()).listOnShelf();
    }

    @Test
    void 非会员调用传统加油包下单接口也被拒绝() throws Exception {
        doThrow(new ServiceException("加油包仅限有效会员购买"))
                .when(beanRechargePackageService).createRecharge(USER_ID, 2L);

        mockMvc.perform(post("/api/mobile/bean/package/recharge")
                        .requestAttr(MobileJwtFilter.MOBILE_USER_ID_ATTR, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("加油包仅限有效会员购买"));

        verify(beanRechargePackageService).createRecharge(USER_ID, 2L);
    }
}
