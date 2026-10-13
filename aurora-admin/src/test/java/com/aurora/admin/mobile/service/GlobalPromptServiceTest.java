package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.GlobalPrompt;
import com.aurora.admin.mobile.mapper.GlobalPromptMapper;
import com.aurora.admin.mobile.service.impl.GlobalPromptServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GlobalPromptService 单测(纯逻辑,内存版 Mapper,无数据库/无 Spring 容器):
 * ① 全局唯一生效:连续保存多版本后仅一条 is_active=1;启用旧版后切换正确(旧版置1、原最新置0);
 * ② htmlUnescape 往返:写带 &quot; 的内容,落库时反转义为 "(恢复送大模型的原始文本)。
 * 直接检查内存 Mapper 的底层行状态,只走 insert/update/updateById/selectById 已实现路径。
 */
class GlobalPromptServiceTest {

    private GlobalPromptServiceImpl service;
    private InMemoryGlobalPromptMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // deactivateCurrent 用 LambdaUpdateWrapper.set/eq 需 GlobalPrompt 的 TableInfo lambda 缓存
        // (正常由容器初始化),纯单测下手动注册一次(TableInfoHelper 幂等,缓存为全局静态)。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, GlobalPrompt.class);

        mapper = new InMemoryGlobalPromptMapper();
        service = new GlobalPromptServiceImpl();
        // ServiceImpl 的 baseMapper 由 Spring 注入,单测用反射注入内存版 mapper
        injectBaseMapper(service, mapper);
    }

    @Test
    void 连续保存多版本后仅一条生效() {
        service.saveAndActivate("版本一");
        service.saveAndActivate("版本二");
        service.saveAndActivate("版本三");

        List<GlobalPrompt> all = mapper.all();
        assertEquals(3, all.size(), "应保留 3 个历史版本");

        long activeCount = all.stream().filter(p -> "1".equals(p.getIsActive())).count();
        assertEquals(1, activeCount, "全局唯一生效:仅一条 is_active=1");

        GlobalPrompt active = all.stream().filter(p -> "1".equals(p.getIsActive())).findFirst().orElse(null);
        assertNotNull(active, "应有一条生效版本");
        assertEquals("版本三", active.getContent(), "最新保存的版本应为当前生效");
    }

    @Test
    void 启用旧版本后切换生效正确() {
        service.saveAndActivate("版本一");
        Long v1Id = mapper.all().get(0).getId();
        service.saveAndActivate("版本二");

        // 启用前:版本二生效、版本一历史
        assertEquals("0", mapper.activeFlagOf(v1Id), "启用前版本一应为历史(0)");

        service.activate(v1Id);

        // 启用后:版本一生效、版本二历史,且全局仍仅一条生效
        assertEquals("1", mapper.activeFlagOf(v1Id), "启用后版本一应生效(1)");
        long activeCount = mapper.all().stream().filter(p -> "1".equals(p.getIsActive())).count();
        assertEquals(1, activeCount, "切换后仍全局唯一生效");
        GlobalPrompt active = mapper.all().stream()
                .filter(p -> "1".equals(p.getIsActive())).findFirst().orElseThrow();
        assertEquals("版本一", active.getContent(), "当前生效应切换为版本一");
    }

    @Test
    void 保存时content的HTML转义被反转义恢复原文() {
        // 模拟 XSS 过滤器把引号转成实体后送入 Service
        service.saveAndActivate("请用 &quot;正式&quot; 语气 &amp; 避免 &lt;标签&gt;");

        Long id = mapper.all().get(0).getId();
        String stored = mapper.contentOf(id);
        assertEquals("请用 \"正式\" 语气 & 避免 <标签>", stored,
                "落库前应 htmlUnescape,恢复送大模型的原始文本");
    }

    /**
     * 反射注入 ServiceImpl.baseMapper(由 Spring 注入,单测手动注入内存版)。
     */
    private static void injectBaseMapper(GlobalPromptServiceImpl target, GlobalPromptMapper mapper) throws Exception {
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
