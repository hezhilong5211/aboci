package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.GlobalPrompt;
import com.aurora.admin.mobile.mapper.GlobalPromptMapper;
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
 * 内存版全局提示词 Mapper:在无数据库/无 Spring 容器下验证 Service 层「全局唯一生效」编排。
 * 只实现测试路径用到的方法:
 * - insert:回填自增 id,模拟数据库自增主键。
 * - selectById:按主键取行(返回副本)。
 * - update(entity, wrapper):对应 GlobalPromptServiceImpl.deactivateCurrent 的固定语义
 *   ——把所有 is_active='1' 的行置 '0'(deactivateCurrent 是其唯一调用方)。
 * - updateById:按主键更新 is_active(activate 启用目标版本)。
 * 其余 BaseMapper 方法抛 UnsupportedOperationException。
 */
class InMemoryGlobalPromptMapper implements GlobalPromptMapper {

    /** id → 行 */
    private final Map<Long, GlobalPrompt> rows = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);

    // ===== 测试辅助 =====

    /** 当前所有行(副本) */
    List<GlobalPrompt> all() {
        List<GlobalPrompt> list = new ArrayList<>();
        rows.values().forEach(r -> list.add(copy(r)));
        return list;
    }

    /** 取指定行的 is_active(副本读取) */
    String activeFlagOf(Long id) {
        GlobalPrompt r = rows.get(id);
        return r == null ? null : r.getIsActive();
    }

    /** content(副本读取) */
    String contentOf(Long id) {
        GlobalPrompt r = rows.get(id);
        return r == null ? null : r.getContent();
    }

    // ===== BaseMapper:测试路径实现 =====

    @Override
    public int insert(GlobalPrompt entity) {
        long id = idGen.incrementAndGet();
        entity.setId(id);
        rows.put(id, copy(entity));
        return 1;
    }

    @Override
    public GlobalPrompt selectById(Serializable id) {
        GlobalPrompt r = rows.get(Long.valueOf(id.toString()));
        return r == null ? null : copy(r);
    }

    @Override
    public int update(GlobalPrompt entity, Wrapper<GlobalPrompt> updateWrapper) {
        // 对应 deactivateCurrent:把当前生效(is_active='1')的行置 '0'。
        int affected = 0;
        for (GlobalPrompt r : rows.values()) {
            if ("1".equals(r.getIsActive())) {
                r.setIsActive("0");
                affected++;
            }
        }
        return affected;
    }

    @Override
    public int updateById(GlobalPrompt entity) {
        GlobalPrompt r = rows.get(entity.getId());
        if (r == null) {
            return 0;
        }
        if (entity.getIsActive() != null) {
            r.setIsActive(entity.getIsActive());
        }
        if (entity.getContent() != null) {
            r.setContent(entity.getContent());
        }
        return 1;
    }

    private static GlobalPrompt copy(GlobalPrompt src) {
        GlobalPrompt t = new GlobalPrompt();
        t.setId(src.getId());
        t.setContent(src.getContent());
        t.setIsActive(src.getIsActive());
        return t;
    }

    // ===== 其余 BaseMapper 抽象方法:非测试路径 =====

    @Override
    public int deleteById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(GlobalPrompt entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteBatchIds(Collection<?> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GlobalPrompt> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<GlobalPrompt> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GlobalPrompt> selectByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GlobalPrompt selectOne(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long selectCount(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GlobalPrompt> selectList(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(Wrapper<GlobalPrompt> queryWrapper, ResultHandler<GlobalPrompt> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GlobalPrompt> selectList(IPage<GlobalPrompt> page, Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(IPage<GlobalPrompt> page, Wrapper<GlobalPrompt> queryWrapper,
                           ResultHandler<GlobalPrompt> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(Wrapper<GlobalPrompt> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page,
                                                Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<GlobalPrompt> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<GlobalPrompt> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> void selectObjs(Wrapper<GlobalPrompt> queryWrapper, ResultHandler<E> resultHandler) {
        throw new UnsupportedOperationException();
    }
}
