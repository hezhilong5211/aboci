package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Skill;
import com.aurora.admin.mobile.mapper.SkillMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版 Skill Mapper:在无数据库/无 Spring 容器下验证 Service 层 inject_prompt 反转义往返。
 * 只实现测试路径用到的方法:insert(回填自增主键)、selectById(按主键取行副本)。
 * 其余 BaseMapper 方法抛 UnsupportedOperationException。
 */
class InMemorySkillMapper implements SkillMapper {

    /** skillId → 行 */
    private final Map<Long, Skill> rows = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);

    @Override
    public int insert(Skill entity) {
        long id = idGen.incrementAndGet();
        entity.setSkillId(id);
        rows.put(id, copy(entity));
        return 1;
    }

    @Override
    public Skill selectById(Serializable id) {
        Skill s = rows.get(Long.valueOf(id.toString()));
        return s == null ? null : copy(s);
    }

    private static Skill copy(Skill src) {
        Skill s = new Skill();
        s.setSkillId(src.getSkillId());
        s.setName(src.getName());
        s.setIcon(src.getIcon());
        s.setDescription(src.getDescription());
        s.setInjectPrompt(src.getInjectPrompt());
        s.setCapabilities(src.getCapabilities());
        s.setSort(src.getSort());
        s.setStatus(src.getStatus());
        return s;
    }

    // ===== 其余 BaseMapper 抽象方法:非测试路径 =====

    @Override
    public int deleteById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(Skill entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteBatchIds(Collection<?> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int updateById(Skill entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Skill entity, Wrapper<Skill> updateWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Skill> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<Skill> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Skill> selectByMap(Map<String, Object> columnMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Skill selectOne(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long selectCount(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Skill> selectList(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(Wrapper<Skill> queryWrapper, ResultHandler<Skill> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Skill> selectList(IPage<Skill> page, Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(IPage<Skill> page, Wrapper<Skill> queryWrapper, ResultHandler<Skill> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(Wrapper<Skill> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page,
                                                Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Skill> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<Skill> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> void selectObjs(Wrapper<Skill> queryWrapper, ResultHandler<E> resultHandler) {
        throw new UnsupportedOperationException();
    }
}
