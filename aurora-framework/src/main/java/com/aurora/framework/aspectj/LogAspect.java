package com.aurora.framework.aspectj;

import com.aurora.common.core.service.ISysOperLogService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.entity.SysOperLog;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.utils.IpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志记录切面
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /**
     * 排除敏感属性字段
     */
    private static final String[] EXCLUDE_PROPERTIES = { "password", "oldPassword", "newPassword", "confirmPassword", "apiKey", "secret" };

    /**
     * 用于记录方法开始时间
     */
    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    private final ISysOperLogService operLogService;
    private final ObjectMapper objectMapper;

    public LogAspect(ISysOperLogService operLogService, ObjectMapper objectMapper) {
        this.operLogService = operLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理请求前执行 - 记录开始时间
     */
    @Before("@annotation(controllerLog)")
    public void doBefore(JoinPoint joinPoint, Log controllerLog) {
        START_TIME.set(System.currentTimeMillis());
    }

    /**
     * 处理完请求后执行 - 正常返回
     */
    @AfterReturning(pointcut = "@annotation(controllerLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Log controllerLog, Object jsonResult) {
        handleLog(joinPoint, controllerLog, null, jsonResult);
    }

    /**
     * 拦截异常操作
     */
    @AfterThrowing(value = "@annotation(controllerLog)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Log controllerLog, Exception e) {
        handleLog(joinPoint, controllerLog, e, null);
    }

    /**
     * 处理日志记录
     */
    protected void handleLog(final JoinPoint joinPoint, Log controllerLog, final Exception e, Object jsonResult) {
        try {
            // 获取当前用户
            String operName = getOperName();

            // 构建操作日志对象
            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(0); // 0=正常
            operLog.setOperTime(LocalDateTime.now());

            // 请求的地址
            HttpServletRequest request = getRequest();
            if (request != null) {
                operLog.setOperIp(IpUtils.getIpAddr(request));
                operLog.setOperLocation(IpUtils.getIpLocation(operLog.getOperIp()));
                operLog.setOperUrl(truncateString(request.getRequestURI(), 255));
                operLog.setRequestMethod(request.getMethod());
            }

            // 设置操作人员
            operLog.setOperName(operName);

            // 设置方法名称
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            operLog.setMethod(truncateString(className + "." + methodName + "()", 100));

            // 处理注解上的参数
            operLog.setTitle(controllerLog.title());
            operLog.setBusinessType(controllerLog.businessType().getCode());

            // 保存请求参数
            if (controllerLog.isSaveRequestData()) {
                setRequestValue(joinPoint, operLog, controllerLog.excludeParamNames());
            }

            // 保存响应数据
            if (controllerLog.isSaveResponseData() && jsonResult != null) {
                operLog.setJsonResult(truncateString(toJsonString(jsonResult), 2000));
            }

            // 如果有异常，记录异常信息
            if (e != null) {
                operLog.setStatus(1); // 1=异常
                operLog.setErrorMsg(truncateString(e.getMessage(), 2000));
            }

            // 计算耗时
            Long startTime = START_TIME.get();
            if (startTime != null) {
                operLog.setCostTime(System.currentTimeMillis() - startTime);
            }

            // 保存日志
            operLogService.save(operLog);

        } catch (Exception ex) {
            log.error("记录操作日志异常: {}", ex.getMessage(), ex);
        } finally {
            START_TIME.remove();
        }
    }

    /**
     * 获取请求的参数
     */
    private void setRequestValue(JoinPoint joinPoint, SysOperLog operLog, String[] excludeParamNames) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (args == null || args.length == 0) {
                return;
            }

            // 需要排除的参数名称
            Set<String> excludeSet = Arrays.stream(excludeParamNames).collect(Collectors.toSet());
            // 添加默认排除的敏感字段
            excludeSet.addAll(Arrays.asList(EXCLUDE_PROPERTIES));

            StringBuilder params = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                String paramName = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;

                // 排除敏感参数
                if (excludeSet.contains(paramName)) {
                    continue;
                }

                // 排除不能序列化的对象
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile) {
                    continue;
                }

                if (arg instanceof Collection || arg instanceof Map) {
                    params.append(paramName).append("=").append(toJsonString(arg)).append("; ");
                } else if (arg != null) {
                    params.append(paramName).append("=").append(toJsonString(arg)).append("; ");
                }
            }

            operLog.setOperParam(truncateString(params.toString(), 2000));
        } catch (Exception e) {
            log.warn("获取请求参数异常: {}", e.getMessage());
        }
    }

    /**
     * 获取当前登录用户名
     */
    private String getOperName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) authentication.getPrincipal();
                return loginUser.getUsername();
            }
        } catch (Exception e) {
            log.warn("获取当前用户失败: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * 获取HttpServletRequest
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 对象转JSON字符串
     */
    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    /**
     * 截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
