package com.aurora.ai.agent.billing;

/**
 * 豆子扣减端口(跨模块 SPI)
 *
 * 用于 AI 计费场景下对豆子余额的扣减、退还与余额查询。
 * 此接口定义在 aurora-ai 模块,由 aurora-admin 模块的 BeanDeductAdapter 实现
 * (适配 BeanAccountService —— 豆子余额唯一写入路径)。
 * aurora-ai 模块通过 @Autowired(required=false) 可选注入,规避 ai→admin 循环依赖。
 *
 * @author aurora
 */
public interface IBeanDeductPort {

    /**
     * 扣费:原子扣减,余额不足由实现方抛 ServiceException("豆子余额不足")。
     *
     * @param userId 用户ID
     * @param beans  扣减豆子数(>0,整数)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void deduct(Long userId, long beans, String bizNo, String remark);

    /** Atomically settle text usage against the available balance and return the recorded debit. */
    long deductUpToBalance(Long userId, long beans, String bizNo, String remark);

    /**
     * 退还:回冲已消耗豆子。
     *
     * @param userId 用户ID
     * @param beans  退还豆子数(>0,整数)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void refund(Long userId, long beans, String bizNo, String remark);

    /**
     * 查询余额(供扣费前校验)。
     *
     * @param userId 用户ID
     * @return 当前余额;无账户返回 0
     */
    long getBalance(Long userId);
}
