package com.aurora.framework.web.exception;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/**
 * 全局异常处理器
 * 拦截系统中抛出的所有异常，统一返回 JSON 格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 Spring Security 认证失败异常
     * 用户名或密码错误
     */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}', 认证失败: {}", requestURI, e.getMessage());
        return Result.error(401, "账号或密码错误");
    }

    /**
     * 处理 Spring Security 权限不足异常
     * 用户没有访问权限
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}', 权限不足: {}", requestURI, e.getMessage());
        return Result.error(403, "没有权限，请联系管理员授权");
    }

    /**
     * 处理参数校验异常 (MethodArgumentNotValidException)
     * 使用 @Valid 注解的参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        BindingResult bindingResult = e.getBindingResult();

        // 获取第一个错误信息
        String errorMsg = "参数校验失败";
        if (bindingResult.hasErrors()) {
            List<ObjectError> errors = bindingResult.getAllErrors();
            if (!errors.isEmpty()) {
                ObjectError error = errors.get(0);
                errorMsg = error.getDefaultMessage();
                if (error instanceof FieldError) {
                    FieldError fieldError = (FieldError) error;
                    log.warn("请求地址'{}', 参数校验失败: 字段[{}], 值[{}], 原因: {}",
                            requestURI, fieldError.getField(), fieldError.getRejectedValue(), errorMsg);
                } else {
                    log.warn("请求地址'{}', 参数校验失败: {}", requestURI, errorMsg);
                }
            }
        }

        return Result.error(400, errorMsg);
    }

    /**
     * 处理参数绑定异常 (BindException)
     * 表单参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        BindingResult bindingResult = e.getBindingResult();

        // 获取第一个错误信息
        String errorMsg = "参数绑定失败";
        if (bindingResult.hasErrors()) {
            List<ObjectError> errors = bindingResult.getAllErrors();
            if (!errors.isEmpty()) {
                ObjectError error = errors.get(0);
                errorMsg = error.getDefaultMessage();
                if (error instanceof FieldError) {
                    FieldError fieldError = (FieldError) error;
                    log.warn("请求地址'{}', 参数绑定失败: 字段[{}], 值[{}], 原因: {}",
                            requestURI, fieldError.getField(), fieldError.getRejectedValue(), errorMsg);
                } else {
                    log.warn("请求地址'{}', 参数绑定失败: {}", requestURI, errorMsg);
                }
            }
        }

        return Result.error(400, errorMsg);
    }

    /**
     * 处理 multipart 文件上传异常
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingServletRequestPartException(MissingServletRequestPartException e,
            HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}', multipart 文件上传异常: 请求参数[{}]", requestURI, e.getRequestPartName());
        return Result.error(400, "文件上传失败：缺少文件参数");
    }

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public Result<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        Integer code = e.getCode() != null ? e.getCode() : Result.FAIL;
        String message = e.getMessage() != null ? e.getMessage() : "业务处理失败";

        log.warn("请求地址'{}', 业务异常: [code={}, message={}]", requestURI, code, message, e);
        return Result.error(code, message);
    }

    /**
     * 处理文件上传超限异常(MultipartException 子类,优先于 RuntimeException 处理)。
     * 返回友好文案,避免落到「系统运行时异常」通用兜底。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
                                                             HttpServletRequest request) {
        log.warn("请求地址'{}', 上传文件超限: {}", request.getRequestURI(), e.getMessage());
        return Result.error("图片过大,请压缩后重试(单张上限 10MB)");
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String errorMessage = e.getMessage();
        // 如果是权限相关异常，返回更明确的错误信息
        if (e.getMessage() != null && (e.getMessage().contains("Access") || e.getMessage().contains("权限")
                || e.getMessage().contains("denied"))) {
            log.warn("请求地址'{}', 权限异常: {}", requestURI, errorMessage, e);
            return Result.error(403, "没有权限，请联系管理员授权");
        }
        log.error("请求地址'{}', 发生运行时异常: {}", requestURI, errorMessage, e);
        // 统一返回通用文案，异常详情只写日志，避免向前端泄露内部实现细节
        return Result.error(500, "系统运行时异常，请联系管理员");
    }

    /**
     * 处理所有其他未知异常 (兜底处理)
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) throws Exception {
        String requestURI = request.getRequestURI();

        // 排除 API 文档路径，让 SpringDoc 原生处理
        if (requestURI.contains("/v3/api-docs") || requestURI.contains("/swagger")) {
            throw e;
        }

        // 打印详细堆栈日志，方便后台排查
        log.error("请求地址'{}', 发生系统未知异常", requestURI, e);

        // 返回给前端的消息统一为 "系统未知异常，请联系管理员"，避免暴露代码细节
        return Result.error(500, "系统未知异常，请联系管理员");
    }
}