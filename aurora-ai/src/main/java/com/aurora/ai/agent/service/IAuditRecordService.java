package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.AuditRecord;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 内容审核记录 Service 接口(V14)。
 * 写入方:审核服务在送审存证时调用 saveRecord 落一条流水;
 * 读取方:管理后台「内容审核日志」列表(ai:audit:list)经 pageQuery 分页查询。
 */
public interface IAuditRecordService extends IService<AuditRecord> {

    /**
     * 保存一条审核记录(单条 insert,流水表无需事务)。
     * create_time 由 @TableField(fill=INSERT) 自动回填(DB default 兜底)。
     *
     * @param record 审核记录实体(由审核服务组装,user_id 等取自登录态/送审上下文)
     */
    void saveRecord(AuditRecord record);

    /**
     * 分页查询审核记录,按 create_time 倒序。
     * 各筛选条件均可空(空则不参与过滤);时间区间用 create_time 的 between/ge/le。
     *
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param objectType 对象类型(text/media,可空)
     * @param suggest    审核建议(pass/review/risky/error,可空)
     * @param result     最终处置(PASS/REJECT/ERROR,可空)
     * @param userId     送审用户(可空)
     * @param beginTime  起始时间(yyyy-MM-dd HH:mm:ss,可空)
     * @param endTime    截止时间(yyyy-MM-dd HH:mm:ss,可空)
     * @return 分页结果
     */
    IPage<AuditRecord> pageQuery(Integer pageNum, Integer pageSize, String objectType, String suggest,
                                 String result, Long userId, String beginTime, String endTime);
}
