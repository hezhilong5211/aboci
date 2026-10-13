package com.aurora.test;

import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.entity.SysOperLog;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.common.core.utils.IpUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 操作日志功能单元测试
 * 测试内容包括：
 * 1. BusinessType 枚举
 * 2. @Log 注解
 * 3. SysOperLog 实体
 * 4. IpUtils 工具类
 */
@DisplayName("操作日志功能测试")
public class OperLogTest {

    // ==================== BusinessType 枚举测试 ====================
    @Nested
    @DisplayName("BusinessType 枚举测试")
    class BusinessTypeTest {

        @Test
        @DisplayName("测试所有业务类型的 code 和 info")
        void testBusinessTypeValues() {
            assertEquals(0, BusinessType.OTHER.getCode());
            assertEquals("其它", BusinessType.OTHER.getInfo());

            assertEquals(1, BusinessType.INSERT.getCode());
            assertEquals("新增", BusinessType.INSERT.getInfo());

            assertEquals(2, BusinessType.UPDATE.getCode());
            assertEquals("修改", BusinessType.UPDATE.getInfo());

            assertEquals(3, BusinessType.DELETE.getCode());
            assertEquals("删除", BusinessType.DELETE.getInfo());

            assertEquals(4, BusinessType.GRANT.getCode());
            assertEquals("授权", BusinessType.GRANT.getInfo());

            assertEquals(5, BusinessType.EXPORT.getCode());
            assertEquals("导出", BusinessType.EXPORT.getInfo());

            assertEquals(6, BusinessType.IMPORT.getCode());
            assertEquals("导入", BusinessType.IMPORT.getInfo());

            assertEquals(9, BusinessType.CLEAN.getCode());
            assertEquals("清空数据", BusinessType.CLEAN.getInfo());

            System.out.println("✅ BusinessType 枚举测试通过");
        }

        @Test
        @DisplayName("测试枚举数量完整性")
        void testBusinessTypeCount() {
            BusinessType[] values = BusinessType.values();
            assertEquals(10, values.length, "BusinessType 应该有 10 个枚举值");
            System.out.println("✅ BusinessType 枚举数量测试通过，共 " + values.length + " 个");
        }
    }

    // ==================== @Log 注解测试 ====================
    @Nested
    @DisplayName("@Log 注解测试")
    class LogAnnotationTest {

        // 模拟一个带有 @Log 注解的方法
        @Log(title = "测试模块", businessType = BusinessType.INSERT, isSaveRequestData = true, isSaveResponseData = false)
        public void mockMethodWithLog() {
            // 空实现，仅用于测试注解
        }

        @Log(title = "用户管理", businessType = BusinessType.DELETE, excludeParamNames = { "password", "token" })
        public void mockMethodWithExcludeParams() {
            // 空实现
        }

        @Test
        @DisplayName("测试 @Log 注解属性读取")
        void testLogAnnotationAttributes() throws NoSuchMethodException {
            Method method = this.getClass().getMethod("mockMethodWithLog");
            Log logAnnotation = method.getAnnotation(Log.class);

            assertNotNull(logAnnotation, "@Log 注解应存在");
            assertEquals("测试模块", logAnnotation.title());
            assertEquals(BusinessType.INSERT, logAnnotation.businessType());
            assertTrue(logAnnotation.isSaveRequestData());
            assertFalse(logAnnotation.isSaveResponseData());

            System.out.println("✅ @Log 注解属性测试通过");
        }

        @Test
        @DisplayName("测试 @Log 注解排除参数")
        void testLogAnnotationExcludeParams() throws NoSuchMethodException {
            Method method = this.getClass().getMethod("mockMethodWithExcludeParams");
            Log logAnnotation = method.getAnnotation(Log.class);

            assertNotNull(logAnnotation);
            String[] excludeParams = logAnnotation.excludeParamNames();
            assertEquals(2, excludeParams.length);
            assertEquals("password", excludeParams[0]);
            assertEquals("token", excludeParams[1]);

            System.out.println("✅ @Log 注解排除参数测试通过");
        }

        @Test
        @DisplayName("测试 @Log 注解默认值")
        void testLogAnnotationDefaults() throws NoSuchMethodException {
            // 使用一个只设置了必需属性的方法
            Method method = this.getClass().getMethod("mockMethodWithLog");
            Log logAnnotation = method.getAnnotation(Log.class);

            // 检查默认值
            assertNotNull(logAnnotation);
            assertEquals(0, logAnnotation.excludeParamNames().length, "默认排除参数应为空数组");

            System.out.println("✅ @Log 注解默认值测试通过");
        }
    }

    // ==================== SysOperLog 实体测试 ====================
    @Nested
    @DisplayName("SysOperLog 实体测试")
    class SysOperLogEntityTest {

        @Test
        @DisplayName("测试实体基本属性设置和获取")
        void testEntityProperties() {
            SysOperLog operLog = new SysOperLog();

            // 设置属性
            operLog.setOperId(1L);
            operLog.setTitle("用户管理");
            operLog.setBusinessType(1);
            operLog.setMethod("com.aurora.admin.controller.SysUserController.add()");
            operLog.setRequestMethod("POST");
            operLog.setOperName("admin");
            operLog.setOperUrl("/system/user");
            operLog.setOperIp("127.0.0.1");
            operLog.setOperLocation("内网IP");
            operLog.setOperParam("{\"userName\":\"test\"}");
            operLog.setJsonResult("{\"code\":200,\"msg\":\"操作成功\"}");
            operLog.setStatus(0);
            operLog.setErrorMsg(null);
            operLog.setOperTime(LocalDateTime.now());
            operLog.setCostTime(50L);

            // 验证属性
            assertEquals(1L, operLog.getOperId());
            assertEquals("用户管理", operLog.getTitle());
            assertEquals(1, operLog.getBusinessType());
            assertEquals("POST", operLog.getRequestMethod());
            assertEquals("admin", operLog.getOperName());
            assertEquals("127.0.0.1", operLog.getOperIp());
            assertEquals("内网IP", operLog.getOperLocation());
            assertEquals(0, operLog.getStatus());
            assertEquals(50L, operLog.getCostTime());
            assertNotNull(operLog.getOperTime());

            System.out.println("✅ SysOperLog 实体属性测试通过");
        }

        @Test
        @DisplayName("测试实体异常状态")
        void testEntityErrorStatus() {
            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(1); // 异常状态
            operLog.setErrorMsg("空指针异常");

            assertEquals(1, operLog.getStatus());
            assertEquals("空指针异常", operLog.getErrorMsg());

            System.out.println("✅ SysOperLog 异常状态测试通过");
        }

        @Test
        @DisplayName("测试实体空值处理")
        void testEntityNullValues() {
            SysOperLog operLog = new SysOperLog();

            // 所有属性应该默认为 null
            assertNull(operLog.getOperId());
            assertNull(operLog.getTitle());
            assertNull(operLog.getOperName());
            assertNull(operLog.getOperIp());
            assertNull(operLog.getOperTime());
            assertNull(operLog.getCostTime());

            System.out.println("✅ SysOperLog 空值处理测试通过");
        }
    }

    // ==================== IpUtils 工具类测试 ====================
    @Nested
    @DisplayName("IpUtils 工具类测试")
    class IpUtilsTest {

        @Test
        @DisplayName("测试内网 IP 判断 - 127.0.0.1")
        void testInternalIpLocalhost() {
            assertTrue(IpUtils.isInternalIp("127.0.0.1"));
            assertTrue(IpUtils.isInternalIp("localhost"));
            System.out.println("✅ 本地回环地址判断测试通过");
        }

        @Test
        @DisplayName("测试内网 IP 判断 - 10.x.x.x 段")
        void testInternalIp10Segment() {
            assertTrue(IpUtils.isInternalIp("10.0.0.1"));
            assertTrue(IpUtils.isInternalIp("10.255.255.255"));
            assertTrue(IpUtils.isInternalIp("10.100.50.25"));
            System.out.println("✅ 10.x.x.x 内网段判断测试通过");
        }

        @Test
        @DisplayName("测试内网 IP 判断 - 172.16.x.x ~ 172.31.x.x 段")
        void testInternalIp172Segment() {
            assertTrue(IpUtils.isInternalIp("172.16.0.1"));
            assertTrue(IpUtils.isInternalIp("172.31.255.255"));
            assertTrue(IpUtils.isInternalIp("172.20.100.50"));

            // 172.15.x.x 和 172.32.x.x 不是内网
            assertFalse(IpUtils.isInternalIp("172.15.0.1"));
            assertFalse(IpUtils.isInternalIp("172.32.0.1"));
            System.out.println("✅ 172.16~31.x.x 内网段判断测试通过");
        }

        @Test
        @DisplayName("测试内网 IP 判断 - 192.168.x.x 段")
        void testInternalIp192Segment() {
            assertTrue(IpUtils.isInternalIp("192.168.0.1"));
            assertTrue(IpUtils.isInternalIp("192.168.255.255"));
            assertTrue(IpUtils.isInternalIp("192.168.1.100"));
            System.out.println("✅ 192.168.x.x 内网段判断测试通过");
        }

        @Test
        @DisplayName("测试外网 IP 判断")
        void testExternalIp() {
            assertFalse(IpUtils.isInternalIp("8.8.8.8")); // Google DNS
            assertFalse(IpUtils.isInternalIp("114.114.114.114")); // 114 DNS
            assertFalse(IpUtils.isInternalIp("220.181.38.148")); // 百度 IP
            System.out.println("✅ 外网 IP 判断测试通过");
        }

        @Test
        @DisplayName("测试 IP 归属地获取")
        void testGetIpLocation() {
            assertEquals("内网IP", IpUtils.getIpLocation("127.0.0.1"));
            assertEquals("内网IP", IpUtils.getIpLocation("192.168.1.1"));
            assertEquals("内网IP", IpUtils.getIpLocation("10.0.0.1"));
            assertEquals("外网IP", IpUtils.getIpLocation("8.8.8.8"));
            assertEquals("未知", IpUtils.getIpLocation(null));
            assertEquals("未知", IpUtils.getIpLocation(""));
            System.out.println("✅ IP 归属地获取测试通过");
        }

        @Test
        @DisplayName("测试空值和边界情况处理")
        void testEdgeCases() {
            assertFalse(IpUtils.isInternalIp(null));
            assertFalse(IpUtils.isInternalIp(""));
            assertFalse(IpUtils.isInternalIp("invalid_ip"));
            System.out.println("✅ 边界情况处理测试通过");
        }

        @Test
        @DisplayName("测试 getIpAddr 空请求处理")
        void testGetIpAddrNullRequest() {
            assertEquals("unknown", IpUtils.getIpAddr(null));
            System.out.println("✅ 空请求处理测试通过");
        }
    }

    // ==================== 综合测试 ====================
    @Test
    @DisplayName("综合测试 - 模拟完整日志记录流程")
    void testFullLogRecordFlow() {
        // 1. 模拟业务操作
        String title = "用户管理";
        BusinessType businessType = BusinessType.INSERT;
        String operName = "admin";
        String operIp = "192.168.1.100";

        // 2. 创建日志对象
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(title);
        operLog.setBusinessType(businessType.getCode());
        operLog.setOperName(operName);
        operLog.setOperIp(operIp);
        operLog.setOperLocation(IpUtils.getIpLocation(operIp));
        operLog.setOperTime(LocalDateTime.now());
        operLog.setStatus(0);
        operLog.setCostTime(100L);
        operLog.setRequestMethod("POST");
        operLog.setOperUrl("/system/user");
        operLog.setOperParam("{\"userName\":\"testUser\",\"nickName\":\"测试用户\"}");
        operLog.setJsonResult("{\"code\":200,\"msg\":\"操作成功\"}");

        // 3. 验证日志对象
        assertNotNull(operLog);
        assertEquals("用户管理", operLog.getTitle());
        assertEquals(1, operLog.getBusinessType()); // INSERT = 1
        assertEquals("admin", operLog.getOperName());
        assertEquals("192.168.1.100", operLog.getOperIp());
        assertEquals("内网IP", operLog.getOperLocation());
        assertEquals(0, operLog.getStatus());
        assertTrue(operLog.getCostTime() > 0);

        System.out.println("==========================================");
        System.out.println("✅ 综合测试通过！模拟日志记录流程完成");
        System.out.println("- 模块: " + operLog.getTitle());
        System.out.println("- 操作类型: " + businessType.getInfo());
        System.out.println("- 操作人员: " + operLog.getOperName());
        System.out.println("- IP地址: " + operLog.getOperIp());
        System.out.println("- IP归属: " + operLog.getOperLocation());
        System.out.println("- 耗时: " + operLog.getCostTime() + "ms");
        System.out.println("==========================================");
    }
}
