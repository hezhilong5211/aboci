package com.aurora.common.core.domain.model.monitor;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 内存相关信息
 */
@Data
public class Mem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 内存总量
     */
    private double total;

    /**
     * 已用内存
     */
    private double used;

    /**
     * 剩余内存
     */
    private double free;

    public double getTotal() {
        return div(total, (1024 * 1024 * 1024), 2);
    }

    public double getUsed() {
        return div(used, (1024 * 1024 * 1024), 2);
    }

    public double getFree() {
        return div(free, (1024 * 1024 * 1024), 2);
    }

    public double getUsage() {
        return div(used * 100, total, 2);
    }

    private double div(double v1, double v2, int scale) {
        if (v2 == 0) return 0;
        BigDecimal b1 = new BigDecimal(Double.toString(v1));
        BigDecimal b2 = new BigDecimal(Double.toString(v2));
        return b1.divide(b2, scale, RoundingMode.HALF_UP).doubleValue();
    }
}
