package com.aurora.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 后台首页统计 Mapper(跨表只读聚合,均在同库 Aurora_datebase)。
 * 仅做查询,无写操作;趋势的 30 天骨架补零在 Service 用 Java 生成,SQL 只按天聚合存在的数据。
 */
@Mapper
public interface DashboardMapper {

    /**
     * 有效用户总数
     */
    @Select("SELECT COUNT(1) FROM mobile_user WHERE del_flag = '0'")
    Long countUserTotal();

    /**
     * 今日新增有效用户
     */
    @Select("SELECT COUNT(1) FROM mobile_user WHERE del_flag = '0' AND create_time >= CURDATE()")
    Long countUserToday();

    /**
     * 累计 AI 产出数
     */
    @Select("SELECT COUNT(1) FROM generation_task")
    Long countGenTotal();

    /**
     * 今日产出数
     */
    @Select("SELECT COUNT(1) FROM generation_task WHERE create_time >= CURDATE()")
    Long countGenToday();

    /**
     * 昨日产出数
     */
    @Select("SELECT COUNT(1) FROM generation_task "
            + "WHERE create_time >= CURDATE() - INTERVAL 1 DAY AND create_time < CURDATE()")
    Long countGenYesterday();

    /**
     * 今日活跃创作用户(去重)
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM generation_task WHERE create_time >= CURDATE()")
    Long countActiveCreatorsToday();

    /**
     * 累计收入(单位:分)
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_order WHERE status = 1")
    Long sumIncomeTotalCent();

    /**
     * 今日收入(单位:分)
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_order WHERE status = 1 AND create_time >= CURDATE()")
    Long sumIncomeTodayCent();

    /**
     * 有效会员人数(叠加制:一个用户可有多段会员,按 user_id 去重;且仅算未过期段)
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM user_membership "
            + "WHERE status = '0' AND start_time <= NOW() AND expire_time > NOW()")
    Long countMemberActive();

    /**
     * 近 30 天每天产出数(仅返回有数据的天,date 为 yyyy-MM-dd)
     */
    @Select("SELECT DATE(create_time) AS date, COUNT(1) AS genCount FROM generation_task "
            + "WHERE create_time >= CURDATE() - INTERVAL 29 DAY "
            + "GROUP BY DATE(create_time)")
    List<Map<String, Object>> selectGenTrend();

    /**
     * 近 30 天每天收入(单位:分,仅返回有数据的天,date 为 yyyy-MM-dd)
     */
    @Select("SELECT DATE(create_time) AS date, COALESCE(SUM(amount), 0) AS incomeCent FROM pay_order "
            + "WHERE status = 1 AND create_time >= CURDATE() - INTERVAL 29 DAY "
            + "GROUP BY DATE(create_time)")
    List<Map<String, Object>> selectIncomeTrend();

    /**
     * 豆子经济:充值/消耗(绝对值)/赠送+发放
     */
    @Select("SELECT "
            + "COALESCE(SUM(CASE WHEN type = 'RECHARGE' THEN change_value ELSE 0 END), 0) AS recharge, "
            + "COALESCE(ABS(SUM(CASE WHEN type = 'DEDUCT' THEN change_value ELSE 0 END)), 0) AS consume, "
            + "COALESCE(SUM(CASE WHEN type IN ('GIFT', 'ISSUE') THEN change_value ELSE 0 END), 0) AS `grant` "
            + "FROM bean_transaction")
    Map<String, Object> selectBeanEconomy();

    /**
     * 各模型产出占比(关联模型名,name 缺失记为 未知)
     */
    @Select("SELECT COALESCE(m.model_name, '未知') AS name, COUNT(1) AS count "
            + "FROM generation_task t LEFT JOIN sys_ai_model m ON t.model_id = m.id "
            + "GROUP BY t.model_id, m.model_name "
            + "ORDER BY count DESC")
    List<Map<String, Object>> selectModelDist();

    /**
     * 各产出类型占比
     */
    @Select("SELECT task_type AS type, COUNT(1) AS count FROM generation_task "
            + "GROUP BY task_type ORDER BY count DESC")
    List<Map<String, Object>> selectTypeDist();

    /**
     * 最近 5 笔订单(amount 为分)
     */
    @Select("SELECT order_no AS orderNo, product_name AS productName, amount AS amountCent, "
            + "status, create_time AS createTime "
            + "FROM pay_order ORDER BY create_time DESC LIMIT 5")
    List<Map<String, Object>> selectRecentOrders();

    /**
     * 最近 5 条产出
     */
    @Select("SELECT user_id AS userId, task_type AS taskType, status, bean_cost AS beanCost, "
            + "create_time AS createTime "
            + "FROM generation_task ORDER BY create_time DESC LIMIT 5")
    List<Map<String, Object>> selectRecentGens();
}
