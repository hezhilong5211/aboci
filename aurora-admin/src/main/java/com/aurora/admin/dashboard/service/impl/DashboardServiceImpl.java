package com.aurora.admin.dashboard.service.impl;

import com.aurora.admin.dashboard.mapper.DashboardMapper;
import com.aurora.admin.dashboard.service.IDashboardService;
import com.aurora.admin.dashboard.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台首页统计 Service 实现。
 * 单值指标直接取 Mapper 标量;趋势在此用 Java 生成近 30 天骨架再按天填充(避免 SQL 补零);
 * 金额由分转元(除 100,两位小数);状态码转中文。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private final DashboardMapper dashboardMapper;

    /**
     * 近 30 天的天数(含今天)
     */
    private static final int TREND_DAYS = 30;

    /**
     * 趋势日期展示格式
     */
    private static final DateTimeFormatter MMDD = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 时间字段输出格式
     */
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public DashboardVO overview() {
        DashboardVO vo = new DashboardVO();

        // 用户
        vo.setUserTotal(nz(dashboardMapper.countUserTotal()));
        vo.setUserToday(nz(dashboardMapper.countUserToday()));

        // 产出
        vo.setGenTotal(nz(dashboardMapper.countGenTotal()));
        vo.setGenToday(nz(dashboardMapper.countGenToday()));
        vo.setGenYesterday(nz(dashboardMapper.countGenYesterday()));
        vo.setActiveCreatorsToday(nz(dashboardMapper.countActiveCreatorsToday()));

        // 收入(分转元)
        vo.setIncomeTotal(centToYuan(dashboardMapper.sumIncomeTotalCent()));
        vo.setIncomeToday(centToYuan(dashboardMapper.sumIncomeTodayCent()));

        // 会员
        vo.setMemberActive(nz(dashboardMapper.countMemberActive()));

        // 趋势(30 天骨架填充)
        vo.setTrend(buildTrend());

        // 豆子经济
        vo.setBean(buildBean());

        // 模型 / 类型分布
        vo.setModelDist(buildModelDist());
        vo.setTypeDist(buildTypeDist());

        // 最近订单 / 产出
        vo.setRecentOrders(buildRecentOrders());
        vo.setRecentGens(buildRecentGens());

        return vo;
    }

    /**
     * 生成近 30 天趋势:先建日期骨架(全 0),再把 SQL 聚合结果按天填回。
     */
    private List<DashboardVO.TrendItem> buildTrend() {
        // SQL 返回的 yyyy-MM-dd -> 产出数 / 收入分
        Map<String, Long> genByDay = toDayLongMap(dashboardMapper.selectGenTrend(), "genCount");
        Map<String, Long> incomeByDay = toDayLongMap(dashboardMapper.selectIncomeTrend(), "incomeCent");

        List<DashboardVO.TrendItem> trend = new ArrayList<>(TREND_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1L);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            String key = day.toString();
            DashboardVO.TrendItem item = new DashboardVO.TrendItem();
            item.setDate(day.format(MMDD));
            item.setGenCount(genByDay.getOrDefault(key, 0L));
            item.setIncome(centToYuan(incomeByDay.getOrDefault(key, 0L)));
            trend.add(item);
        }
        return trend;
    }

    /**
     * 把按天聚合行(date 列为 java.sql.Date 或 LocalDate)转成 yyyy-MM-dd -> 数值 的 map。
     */
    private Map<String, Long> toDayLongMap(List<Map<String, Object>> rows, String valueKey) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object dateObj = row.get("date");
            if (dateObj == null) {
                continue;
            }
            String key;
            if (dateObj instanceof Date) {
                key = ((Date) dateObj).toLocalDate().toString();
            } else if (dateObj instanceof LocalDate) {
                key = ((LocalDate) dateObj).toString();
            } else {
                key = dateObj.toString();
            }
            map.put(key, toLong(row.get(valueKey)));
        }
        return map;
    }

    /**
     * 豆子经济单行聚合 -> VO
     */
    private DashboardVO.BeanEconomy buildBean() {
        Map<String, Object> row = dashboardMapper.selectBeanEconomy();
        DashboardVO.BeanEconomy bean = new DashboardVO.BeanEconomy();
        if (row == null) {
            bean.setRecharge(0L);
            bean.setConsume(0L);
            bean.setGrant(0L);
            return bean;
        }
        bean.setRecharge(toLong(row.get("recharge")));
        bean.setConsume(toLong(row.get("consume")));
        bean.setGrant(toLong(row.get("grant")));
        return bean;
    }

    /**
     * 模型分布
     */
    private List<DashboardVO.NameCount> buildModelDist() {
        List<DashboardVO.NameCount> list = new ArrayList<>();
        List<Map<String, Object>> rows = dashboardMapper.selectModelDist();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardVO.NameCount nc = new DashboardVO.NameCount();
            Object name = row.get("name");
            nc.setName(name == null ? "未知" : name.toString());
            nc.setCount(toLong(row.get("count")));
            list.add(nc);
        }
        return list;
    }

    /**
     * 类型分布
     */
    private List<DashboardVO.TypeCount> buildTypeDist() {
        List<DashboardVO.TypeCount> list = new ArrayList<>();
        List<Map<String, Object>> rows = dashboardMapper.selectTypeDist();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardVO.TypeCount tc = new DashboardVO.TypeCount();
            Object type = row.get("type");
            tc.setType(type == null ? "" : type.toString());
            tc.setCount(toLong(row.get("count")));
            list.add(tc);
        }
        return list;
    }

    /**
     * 最近订单(金额分转元,状态转中文)
     */
    private List<DashboardVO.RecentOrder> buildRecentOrders() {
        List<DashboardVO.RecentOrder> list = new ArrayList<>();
        List<Map<String, Object>> rows = dashboardMapper.selectRecentOrders();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardVO.RecentOrder o = new DashboardVO.RecentOrder();
            o.setOrderNo(toStr(row.get("orderNo")));
            o.setProductName(toStr(row.get("productName")));
            o.setAmount(centToYuan(toLong(row.get("amountCent"))));
            o.setStatusText(orderStatusText(toInt(row.get("status"))));
            o.setCreateTime(formatTime(row.get("createTime")));
            list.add(o);
        }
        return list;
    }

    /**
     * 最近产出(状态转中文)
     */
    private List<DashboardVO.RecentGen> buildRecentGens() {
        List<DashboardVO.RecentGen> list = new ArrayList<>();
        List<Map<String, Object>> rows = dashboardMapper.selectRecentGens();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            DashboardVO.RecentGen g = new DashboardVO.RecentGen();
            g.setUserId(toLong(row.get("userId")));
            g.setTaskType(toStr(row.get("taskType")));
            g.setStatusText(genStatusText(toStr(row.get("status"))));
            g.setBeanCost(toLong(row.get("beanCost")));
            g.setCreateTime(formatTime(row.get("createTime")));
            list.add(g);
        }
        return list;
    }

    /**
     * 订单状态码转中文(0待支付/1已支付/2已退款/3已关闭/4已支付待退款)
     */
    private String orderStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "待支付";
            case 1:
                return "已支付";
            case 2:
                return "已退款";
            case 3:
                return "已关闭";
            case 4:
                return "已支付待退款";
            default:
                return "未知";
        }
    }

    /**
     * 产出任务状态转中文(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
     */
    private String genStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "CREATED":
                return "已创建";
            case "QUEUED":
                return "排队中";
            case "RUNNING":
                return "生成中";
            case "SUCCEEDED":
                return "已完成";
            case "FAILED":
                return "失败";
            case "CANCELLED":
                return "已取消";
            default:
                return status;
        }
    }

    /**
     * 分转元(两位小数,空视为 0)
     */
    private BigDecimal centToYuan(Long cent) {
        long v = cent == null ? 0L : cent;
        return BigDecimal.valueOf(v).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * null 安全的 Long(空转 0)
     */
    private Long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 任意数值对象转 Long(空转 0)
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * 任意数值对象转 Integer(空返回 null)
     */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 任意对象转 String(空转 null)
     */
    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 时间字段格式化为 yyyy-MM-dd HH:mm:ss(空转 null)
     */
    private String formatTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATETIME);
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().format(DATETIME);
        }
        return value.toString();
    }
}
