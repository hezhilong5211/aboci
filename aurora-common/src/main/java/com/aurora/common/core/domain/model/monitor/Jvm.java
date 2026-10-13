package com.aurora.common.core.domain.model.monitor;

import lombok.Data;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;

import cn.hutool.core.date.BetweenFormatter.Level;

/**
 * JVM相关信息
 */
@Data
public class Jvm implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前JVM占用的内存总数(M)
     */
    private double total;

    /**
     * JVM最大可用内存总数(M)
     */
    private double max;

    /**
     * JVM空闲内存(M)
     */
    private double free;

    /**
     * JDK版本
     */
    private String version;

    /**
     * JDK路径
     */
    private String home;

    public double getTotal() {
        return div(total, (1024 * 1024), 2);
    }

    public double getMax() {
        return div(max, (1024 * 1024), 2);
    }

    public double getFree() {
        return div(free, (1024 * 1024), 2);
    }

    public double getUsed() {
        return div(total - free, (1024 * 1024), 2);
    }

    public double getUsage() {
        return div((total - free) * 100, total, 2);
    }

    /**
     * 获取JDK名称
     */
    public String getName() {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    /**
     * JDK启动时间
     */
    public String getStartTime() {
        return DateUtil.formatDateTime(new Date(ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    /**
     * JDK运行时间
     */
    public String getRunTime() {
        return DateUtil.formatBetween(System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime(), Level.SECOND);
    }

    private double div(double v1, double v2, int scale) {
        if (v2 == 0) return 0;
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.divide(b2, scale, RoundingMode.HALF_UP).doubleValue();
    }
}
