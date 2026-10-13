package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.Skill;
import com.aurora.admin.mobile.mapper.SkillMapper;
import com.aurora.admin.mobile.service.ISkillService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Skill 配置 Service 实现
 * 逻辑删走 removeById(@TableLogic delFlag);inject_prompt 入库前反转义保留原始提示词语义。
 */
@Service
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements ISkillService {

    @Override
    public IPage<Skill> pageList(Integer pageNum, Integer pageSize, String name, String status) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Skill::getName, name)
                .eq(StringUtils.hasText(status), Skill::getStatus, status)
                .orderByAsc(Skill::getSort)
                .orderByDesc(Skill::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public boolean save(Skill entity) {
        unescapePromptField(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Skill entity) {
        unescapePromptField(entity);
        return super.updateById(entity);
    }

    /**
     * inject_prompt 是送给大模型的原始文本,XSS 过滤器会把 "<>& 转成实体污染提示词语义;
     * 入库前对其反转义恢复原文(name/description 为展示文本,保留 XSS 转义)。
     */
    private void unescapePromptField(Skill entity) {
        if (entity.getInjectPrompt() != null) {
            entity.setInjectPrompt(HtmlUtils.htmlUnescape(entity.getInjectPrompt()));
        }
    }
}
