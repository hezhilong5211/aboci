package com.aurora.common.core.domain.model.monitor;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CPU相关信息
 */
@Data
public class Cpu implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 核心数
     */
    private int cpuNum;

    /**
     * CPU总的使用率
     */
    private double total;

    /**
     * CPU系统使用率
     */
    private double sys;

    /**
     * CPU用户使用率
     */
    private double used;

    /**
     * CPU当前等待率
     */
    private double wait;

    /**
     * CPU当前空闲率
     */
    private double free;

    public double getTotal() {
        return handleDouble(total);
    }

    public double getSys() {
        return handleDouble(sys);
    }

    public double getUsed() {
        return handleDouble(used);
    }

    public double getWait() {
        return handleDouble(wait);
    }

    public double getFree() {
        return handleDouble(free);
    }

    private double handleDouble(double val) {
        return new BigDecimal(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
