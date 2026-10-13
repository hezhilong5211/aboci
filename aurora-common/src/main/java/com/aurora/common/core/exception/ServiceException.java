package com.aurora.common.core.exception;

/**
 * 自定义业务异常类
 * 用于处理业务逻辑中的异常情况
 */
public class ServiceException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private Integer code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 错误详情
     */
    private String detailMessage;

    public ServiceException() {
    }

    public ServiceException(String message) {
        this.message = message;
    }

    public ServiceException(String message, Integer code) {
        this.message = message;
        this.code = code;
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public ServiceException(String message, Integer code, Throwable cause) {
        super(message, cause);
        this.message = message;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public void setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage;
    }
    
    /**
     * 创建业务异常（使用默认错误码500）
     */
    public static ServiceException of(String message) {
        return new ServiceException(message);
    }
    
    /**
     * 创建业务异常（指定错误码）
     */
    public static ServiceException of(String message, Integer code) {
        return new ServiceException(message, code);
    }
    
    /**
     * 创建业务异常（包含原始异常）
     */
    public static ServiceException of(String message, Throwable cause) {
        return new ServiceException(message, cause);
    }
    
    /**
     * 创建业务异常（指定错误码和原始异常）
     */
    public static ServiceException of(String message, Integer code, Throwable cause) {
        return new ServiceException(message, code, cause);
    }
}
