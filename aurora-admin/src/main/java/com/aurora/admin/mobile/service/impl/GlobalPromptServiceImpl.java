package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.GlobalPrompt;
import com.aurora.admin.mobile.mapper.GlobalPromptMapper;
import com.aurora.admin.mobile.service.IGlobalPromptService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 全局提示词 Service 实现
 * 「全局唯一生效」:saveAndActivate / activate 均在事务内「先全表置0、再置目标为1」;
 * content 是送给大模型的原始文本,入库前 htmlUnescape 反转义恢复原文。
 */
@Service
public class GlobalPromptServiceImpl extends ServiceImpl<GlobalPromptMapper, GlobalPrompt>
        implements IGlobalPromptService {

    @Override
    public GlobalPrompt getActive() {
        LambdaQueryWrapper<GlobalPrompt> wrapper = new LambdaQueryWrapper<GlobalPrompt>()
                .eq(GlobalPrompt::getIsActive, "1")
                .orderByDesc(GlobalPrompt::getId)
                .last("limit 1");
        return getOne(wrapper);
    }

    @Override
    public IPage<GlobalPrompt> pageHistory(Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<GlobalPrompt> wrapper = new LambdaQueryWrapper<GlobalPrompt>()
                .orderByDesc(GlobalPrompt::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAndActivate(String content) {
        deactivateCurrent();
        GlobalPrompt entity = new GlobalPrompt();
        entity.setContent(content);
        entity.setIsActive("1");
        // save 内已做 content 反转义
        save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        GlobalPrompt target = getById(id);
        if (target == null) {
            throw new ServiceException("目标版本不存在");
        }
        deactivateCurrent();
        GlobalPrompt update = new GlobalPrompt();
        update.setId(id);
        update.setIsActive("1");
        updateById(update);
    }

    @Override
    public boolean save(GlobalPrompt entity) {
        unescapeContent(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(GlobalPrompt entity) {
        unescapeContent(entity);
        return super.updateById(entity);
    }

    /**
     * 把当前生效版本(is_active=1)置 0;带 where 条件避免被 BlockAttackInnerInterceptor 拦截,
     * 同时因「全局唯一生效」只会命中至多一条,语义等价全表置 0。
     */
    private void deactivateCurrent() {
        LambdaUpdateWrapper<GlobalPrompt> wrapper = new LambdaUpdateWrapper<GlobalPrompt>()
                .set(GlobalPrompt::getIsActive, "0")
                .eq(GlobalPrompt::getIsActive, "1");
        update(wrapper);
    }

    /**
     * content 是送给大模型的原始文本,XSS 过滤器会把 "<>& 转成实体污染提示词语义;
     * 入库前对其反转义恢复原文。
     */
    private void unescapeContent(GlobalPrompt entity) {
        if (entity.getContent() != null) {
            entity.setContent(HtmlUtils.htmlUnescape(entity.getContent()));
        }
    }
}
