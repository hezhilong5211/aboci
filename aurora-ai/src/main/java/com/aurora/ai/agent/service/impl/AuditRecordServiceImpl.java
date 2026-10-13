package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.AuditRecord;
import com.aurora.ai.agent.mapper.AuditRecordMapper;
import com.aurora.ai.agent.service.IAuditRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 内容审核记录 Service 实现(V14)。
 * audit_record 为流水表(无逻辑删):写方法为单条 insert,无需事务;
 * create_time 由实体 @TableField(fill=INSERT) 自动回填。
 */
@Service
public class AuditRecordServiceImpl extends ServiceImpl<AuditRecordMapper, AuditRecord>
        implements IAuditRecordService {

    @Override
    public void saveRecord(AuditRecord record) {
        save(record);
    }

    @Override
    public IPage<AuditRecord> pageQuery(Integer pageNum, Integer pageSize, String objectType, String suggest,
                                        String result, Long userId, String beginTime, String endTime) {
        LambdaQueryWrapper<AuditRecord> wrapper = new LambdaQueryWrapper<>();
        // 各筛选条件均可空:用 condition 形参控制是否参与拼装(空则不过滤)
        wrapper.eq(StringUtils.hasText(objectType), AuditRecord::getObjectType, objectType)
                .eq(StringUtils.hasText(suggest), AuditRecord::getSuggest, suggest)
                .eq(StringUtils.hasText(result), AuditRecord::getResult, result)
                .eq(userId != null, AuditRecord::getUserId, userId)
                // 时间区间:ge/le 分别处理,允许只传一端
                .ge(StringUtils.hasText(beginTime), AuditRecord::getCreateTime, beginTime)
                .le(StringUtils.hasText(endTime), AuditRecord::getCreateTime, endTime)
                .orderByDesc(AuditRecord::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
