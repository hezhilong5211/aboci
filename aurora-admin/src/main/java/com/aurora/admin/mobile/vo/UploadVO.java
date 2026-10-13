package com.aurora.admin.mobile.vo;

import lombok.Data;

/**
 * 文件上传出参
 */
@Data
public class UploadVO {

    /** 可访问URL */
    private String url;

    /** 存储相对路径 */
    private String path;
}
