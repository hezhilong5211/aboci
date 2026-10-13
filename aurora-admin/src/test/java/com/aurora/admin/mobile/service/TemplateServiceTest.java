package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.mapper.TemplateMapper;
import com.aurora.admin.mobile.mapper.TemplateCategoryMapper;
import com.aurora.admin.mobile.service.impl.TemplateServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * TemplateService 单测:params JSON 原样存取 + 按 category 精确筛选。
 * 用内存版 Mapper(InMemoryTemplateMapper)在无数据库/无 Spring 容器下验证 Service 编排:
 * - save→getById:params 字符串完整往返一致(不强转对象);
 * - pageList:LambdaQueryWrapper 的 category eq 条件正确生效(image 只命中 image)。
 */
class TemplateServiceTest {

    private TemplateServiceImpl service;
    private InMemoryTemplateMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // LambdaQueryWrapper 生成 SQL 段需 Template 的 TableInfo 缓存(正常由容器初始化),
        // 纯单测下手动注册一次(TableInfoHelper 幂等,缓存为全局静态)。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Template.class);

        mapper = new InMemoryTemplateMapper();
        service = new TemplateServiceImpl(mock(TemplateCategoryMapper.class));
        // ServiceImpl 的 baseMapper 由 Spring 注入,单测用反射注入内存版 mapper
        injectBaseMapper(service, mapper);
    }

    @Test
    void params为JSON字符串时存取原样一致() {
        Template t = new Template();
        t.setCategory("image");
        t.setTitle("冷茶灰短BOB");
        t.setPrompt("一段提示词");
        t.setParams("{\"clarity\":\"4K\"}");
        t.setResultMedia("[\"https://a.com/1.png\",\"https://a.com/2.png\"]");

        service.save(t);
        assertNotNull(t.getTemplateId(), "保存后应回填自增主键");

        Template loaded = service.getById(t.getTemplateId());
        assertNotNull(loaded, "应能按 ID 读出");
        assertEquals("{\"clarity\":\"4K\"}", loaded.getParams(), "params JSON 应原样往返,不被改写");
        assertEquals("[\"https://a.com/1.png\",\"https://a.com/2.png\"]", loaded.getResultMedia(),
                "result_media JSON 数组应原样往返");
    }

    @Test
    void 按category精确筛选只返回该分类() {
        mapper.seed(buildSeed(1L, "image", "图片模板A"));
        mapper.seed(buildSeed(2L, "video", "视频模板B"));
        mapper.seed(buildSeed(3L, "image", "图片模板C"));

        IPage<Template> imagePage = service.pageList(1, 10, "image", null, null, null);
        List<Template> records = imagePage.getRecords();

        assertEquals(2, records.size(), "image 筛选应只命中 2 条");
        assertTrue(records.stream().allMatch(r -> "image".equals(r.getCategory())),
                "返回记录分类应全部为 image");
        List<Long> ids = records.stream().map(Template::getTemplateId).sorted().collect(Collectors.toList());
        assertEquals(List.of(1L, 3L), ids, "应命中 id=1、3 两条 image 记录");
    }

    @Test
    void 不传category时返回全部() {
        mapper.seed(buildSeed(1L, "image", "图片模板A"));
        mapper.seed(buildSeed(2L, "video", "视频模板B"));

        IPage<Template> all = service.pageList(1, 10, null, null, null, null);
        assertEquals(2, all.getRecords().size(), "不传 category 应返回全部分类");
    }

    @Test
    void 按行业分类筛选只返回对应案例() {
        Template hair = buildSeed(1L, "image", "美发案例");
        hair.setIndustryCategoryId(1L);
        Template nail = buildSeed(2L, "image", "美甲案例");
        nail.setIndustryCategoryId(2L);
        mapper.seed(hair);
        mapper.seed(nail);

        IPage<Template> page = service.pageList(1, 10, "image", 2L, null, null);

        assertEquals(1, page.getRecords().size());
        assertEquals("美甲案例", page.getRecords().get(0).getTitle());
    }

    private Template buildSeed(Long id, String category, String title) {
        Template t = new Template();
        t.setTemplateId(id);
        t.setCategory(category);
        t.setTitle(title);
        t.setPrompt("p");
        t.setStatus("0");
        return t;
    }

    /**
     * 反射注入 ServiceImpl.baseMapper(由 Spring 注入,单测手动注入内存版)。
     */
    private static void injectBaseMapper(TemplateServiceImpl target, TemplateMapper mapper) throws Exception {
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
