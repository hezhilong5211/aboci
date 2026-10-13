package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.mapper.TemplateMapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版模板 Mapper:在无数据库/无 Spring 容器下验证 Service 层逻辑。
 * - insert:回填自增 templateId,模拟数据库自增主键(用于 params 原样存取验证)。
 * - selectById:按主键取行(返回副本,模拟 ORM 取出独立对象)。
 * - selectList(IPage, Wrapper):读取 LambdaQueryWrapper 的参数键值对,按 category/status 精确、
 *   title 模糊过滤,模拟 service.pageList 构造的筛选条件(用于 category 筛选验证)。
 * 仅实现测试路径用到的方法,其余 BaseMapper 方法抛 UnsupportedOperationException。
 */
class InMemoryTemplateMapper implements TemplateMapper {

    /** templateId → 行 */
    private final Map<Long, Template> rows = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);

    // ===== 测试辅助 =====

    /** 预置一行(已带 id,不走自增) */
    void seed(Template t) {
        rows.put(t.getTemplateId(), copy(t));
    }

    // ===== BaseMapper:测试路径实现 =====

    @Override
    public int insert(Template entity) {
        long id = idGen.incrementAndGet();
        entity.setTemplateId(id);
        rows.put(id, copy(entity));
        return 1;
    }

    @Override
    public Template selectById(Serializable id) {
        Template t = rows.get(Long.valueOf(id.toString()));
        return t == null ? null : copy(t);
    }

    @Override
    public List<Template> selectList(IPage<Template> page, Wrapper<Template> queryWrapper) {
        // service.pageList 传入 LambdaQueryWrapper(AbstractWrapper 子类),取其参数键值对。
        AbstractWrapper<Template, ?, ?> aw = (AbstractWrapper<Template, ?, ?>) queryWrapper;
        // 先触发 SQL 段拼装,填充参数键值对(paramNameValuePairs 惰性填充,需先取 sqlSegment)。
        aw.getSqlSegment();
        // service.pageList 跳过空条件,wrapper 只携带非空筛选;
        // 从参数键值对按值特征还原 category(image/video 精确)、status(0/1 精确)、title(%kw% 模糊)。
        Map<String, Object> params = aw.getParamNameValuePairs();
        String category = null;
        Long industryCategoryId = null;
        String status = null;
        String title = null;
        for (Object v : params.values()) {
            if (v instanceof Number) {
                industryCategoryId = ((Number) v).longValue();
                continue;
            }
            if (!(v instanceof String)) {
                continue;
            }
            String s = (String) v;
            if (s.equals("image") || s.equals("video")) {
                category = s;
            } else if (s.equals("0") || s.equals("1")) {
                status = s;
            } else if (s.contains("%")) {
                title = s.replace("%", "");
            }
        }

        List<Template> result = new ArrayList<>();
        for (Template t : rows.values()) {
            if (category != null && !category.equals(t.getCategory())) {
                continue;
            }
            if (industryCategoryId != null
                    && !industryCategoryId.equals(t.getIndustryCategoryId())) {
                continue;
            }
            if (status != null && !status.equals(t.getStatus())) {
                continue;
            }
            if (title != null && (t.getTitle() == null || !t.getTitle().contains(title))) {
                continue;
            }
            result.add(copy(t));
        }
        return result;
    }

    private static Template copy(Template src) {
        Template t = new Template();
        t.setTemplateId(src.getTemplateId());
        t.setCategory(src.getCategory());
        t.setIndustryCategoryId(src.getIndustryCategoryId());
        t.setTitle(src.getTitle());
        t.setCoverUrl(src.getCoverUrl());
        t.setPrompt(src.getPrompt());
        t.setParams(src.getParams());
        t.setResultMedia(src.getResultMedia());
        t.setTags(src.getTags());
        t.setUseCount(src.getUseCount());
        t.setSort(src.getSort());
        t.setStatus(src.getStatus());
        return t;
    }

    // ===== 其余 BaseMapper 抽象方法:非测试路径 =====

    @Override
    public int deleteById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(Template entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteBatchIds(Collection<?> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int updateById(Template entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Template entity, Wrapper<Template> updateWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Template> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<Template> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Template> selectByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Template selectOne(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long selectCount(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Template> selectList(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(Wrapper<Template> queryWrapper, ResultHandler<Template> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(IPage<Template> page, Wrapper<Template> queryWrapper,
                           ResultHandler<Template> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(Wrapper<Template> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page,
                                                Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Template> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<Template> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> void selectObjs(Wrapper<Template> queryWrapper, ResultHandler<E> resultHandler) {
        throw new UnsupportedOperationException();
    }
}
