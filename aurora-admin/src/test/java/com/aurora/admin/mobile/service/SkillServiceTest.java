package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Skill;
import com.aurora.admin.mobile.mapper.SkillMapper;
import com.aurora.admin.mobile.service.impl.SkillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SkillService 单测(纯逻辑,内存版 Mapper,无数据库/无 Spring 容器):
 * inject_prompt 是送给大模型的原始文本,save 入库前应 htmlUnescape 反转义恢复原文,
 * 验证写带 &quot; 的内容读回为 "(XSS 反转义往返)。
 */
class SkillServiceTest {

    private SkillServiceImpl service;
    private InMemorySkillMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new InMemorySkillMapper();
        service = new SkillServiceImpl();
        // ServiceImpl 的 baseMapper 由 Spring 注入,单测用反射注入内存版 mapper
        injectBaseMapper(service, mapper);
    }

    @Test
    void 保存时injectPrompt的HTML转义被反转义恢复原文() {
        Skill skill = new Skill();
        skill.setName("正式润色");
        // 模拟 XSS 过滤器把引号/符号转成实体后送入 Service
        skill.setInjectPrompt("请用 &quot;正式&quot; 语气 &amp; 避免 &lt;标签&gt;");

        service.save(skill);
        assertNotNull(skill.getSkillId(), "保存后应回填自增主键");

        Skill loaded = service.getById(skill.getSkillId());
        assertNotNull(loaded, "应能按 ID 读出");
        assertEquals("请用 \"正式\" 语气 & 避免 <标签>", loaded.getInjectPrompt(),
                "落库前应 htmlUnescape,恢复送大模型的原始文本");
    }

    /**
     * 反射注入 ServiceImpl.baseMapper(由 Spring 注入,单测手动注入内存版)。
     */
    private static void injectBaseMapper(SkillServiceImpl target, SkillMapper mapper) throws Exception {
        Field field = findField(target.getClass(), "baseMapper");
        field.setAccessible(true);
        field.set(target, mapper);
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
