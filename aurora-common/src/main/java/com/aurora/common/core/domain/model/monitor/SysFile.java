package com.aurora.common.core.domain.model.monitor;

import lombok.Data;
import java.io.Serializable;

/**
 * 系统文件相关信息
 */
@Data
public class SysFile implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 盘符路径
     */
    private String dirName;

    /**
     * 盘符类型
     */
    private String sysTypeName;

    /**
     * 文件系统类型
     */
    private String typeName;

    /**
     * 总大小
     */
    private String total;

    /**
     * 剩余大小
     */
    private String free;

    /**
     * 已用大小
     */
    private String used;

    /**
     * 资源利用率
     */
    private double usage;
}
