package com.aurora.admin.dashboard.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 后台首页数据概览出参
 * 字段口径见 .ai 契约;金额一律转为元(BigDecimal),计数为 Long。
 */
@Data
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户总数(有效用户)
     */
    private Long userTotal;

    /**
     * 今日新增用户
     */
    private Long userToday;

    /**
     * 累计 AI 产出
     */
    private Long genTotal;

    /**
     * 今日产出
     */
    private Long genToday;

    /**
     * 昨日产出(用于算环比)
     */
    private Long genYesterday;

    /**
     * 今日活跃创作用户(今日有产出的去重用户数)
     */
    private Long activeCreatorsToday;

    /**
     * 累计收入(元)
     */
    private BigDecimal incomeTotal;

    /**
     * 今日收入(元)
     */
    private BigDecimal incomeToday;

    /**
     * 有效会员数
     */
    private Long memberActive;

    /**
     * 近 30 天趋势(按天,缺失天补 0)
     */
    private List<TrendItem> trend;

    /**
     * 豆子经济
     */
    private BeanEconomy bean;

    /**
     * 各模型产出占比
     */
    private List<NameCount> modelDist;

    /**
     * 各产出类型占比
     */
    private List<TypeCount> typeDist;

    /**
     * 最近 5 笔订单
     */
    private List<RecentOrder> recentOrders;

    /**
     * 最近 5 条产出
     */
    private List<RecentGen> recentGens;

    /**
     * 趋势项(近 30 天按天)
     */
    @Data
    public static class TrendItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 日期(MM-dd)
         */
        private String date;

        /**
         * 当天产出数
         */
        private Long genCount;

        /**
         * 当天收入(元)
         */
        private BigDecimal income;
    }

    /**
     * 豆子经济
     */
    @Data
    public static class BeanEconomy implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 累计充值豆
         */
        private Long recharge;

        /**
         * 累计消耗豆(取绝对值)
         */
        private Long consume;

        /**
         * 累计赠送 + 发放豆
         */
        private Long grant;
    }

    /**
     * 名称-数量项(模型分布)
     */
    @Data
    public static class NameCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 模型名
         */
        private String name;

        /**
         * 产出数
         */
        private Long count;
    }

    /**
     * 类型-数量项(产出类型分布)
     */
    @Data
    public static class TypeCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 产出类型(如 text2img/img2img)
         */
        private String type;

        /**
         * 数量
         */
        private Long count;
    }

    /**
     * 最近订单项
     */
    @Data
    public static class RecentOrder implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户订单号
         */
        private String orderNo;

        /**
         * 商品名称
         */
        private String productName;

        /**
         * 金额(元)
         */
        private BigDecimal amount;

        /**
         * 状态中文
         */
        private String statusText;

        /**
         * 创建时间
         */
        private String createTime;
    }

    /**
     * 最近产出项
     */
    @Data
    public static class RecentGen implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 任务类型
         */
        private String taskType;

        /**
         * 状态中文
         */
        private String statusText;

        /**
         * 消耗豆子
         */
        private Long beanCost;

        /**
         * 创建时间
         */
        private String createTime;
    }
}
