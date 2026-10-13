package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanAccount;
import com.aurora.admin.mobile.mapper.BeanAccountMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版账户 Mapper:精确模拟数据库账户行与原子 UPDATE 语义。
 * deductIfEnough 用 synchronized(行对象)保证「判断 balance>=amount + 扣减」原子,
 * 等价于数据库 UPDATE ... WHERE balance>=amount 的单语句原子性,用于并发不超扣验证。
 * 仅实现 BeanAccountServiceImpl 用到的方法(insert / selectList(Wrapper) / 三个自定义),
 * 其余 BaseMapper 方法非测试路径,抛 UnsupportedOperationException。
 */
class InMemoryAccountMapper implements BeanAccountMapper {

    /** userId → 账户行 */
    private final Map<Long, BeanAccount> rows = new ConcurrentHashMap<>();

    // ===== 测试辅助 =====

    void seed(long userId, long balance) {
        BeanAccount a = new BeanAccount();
        a.setAccountId(userId);
        a.setUserId(userId);
        a.setBalance(balance);
        a.setTotalEarned(balance);
        a.setTotalConsumed(0L);
        a.setTotalExpired(0L);
        a.setTotalRevoked(0L);
        rows.put(userId, a);
    }

    long balanceOf(long userId) {
        return rows.get(userId).getBalance();
    }

    long consumedOf(long userId) {
        return rows.get(userId).getTotalConsumed();
    }

    long expiredOf(long userId) {
        return rows.get(userId).getTotalExpired();
    }

    long revokedOf(long userId) {
        return rows.get(userId).getTotalRevoked();
    }

    void setExpired(long userId, long amount) {
        rows.get(userId).setTotalExpired(amount);
    }

    // ===== 自定义原子方法 =====

    @Override
    public BeanAccount selectByUserIdForUpdate(Long userId) {
        return rows.get(userId);
    }

    @Override
    public int increaseEarn(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            a.setBalance(a.getBalance() + amount);
            a.setTotalEarned(a.getTotalEarned() + amount);
        }
        return 1;
    }

    @Override
    public int deductIfEnough(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        // 行级锁内完成「条件判断 + 扣减」,模拟单条原子 UPDATE...WHERE balance>=amount
        synchronized (a) {
            if (a.getBalance() < amount) {
                return 0;
            }
            a.setBalance(a.getBalance() - amount);
            a.setTotalConsumed(a.getTotalConsumed() + amount);
            return 1;
        }
    }

    @Override
    public int refund(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            a.setBalance(a.getBalance() + amount);
            a.setTotalConsumed(Math.max(a.getTotalConsumed() - amount, 0L));
        }
        return 1;
    }

    @Override
    public int clearForMembershipExpiry(Long userId) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            if (a.getBalance() <= 0) {
                return 0;
            }
            a.setTotalExpired(a.getTotalExpired() + a.getBalance());
            a.setBalance(0L);
            return 1;
        }
    }

    @Override
    public int expireAmount(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            if (amount <= 0 || a.getBalance() < amount) {
                return 0;
            }
            a.setBalance(a.getBalance() - amount);
            a.setTotalExpired(a.getTotalExpired() + amount);
            return 1;
        }
    }

    @Override
    public int revokeForOrderRefund(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            if (amount < 0 || a.getBalance() < amount) {
                return 0;
            }
            a.setBalance(a.getBalance() - amount);
            a.setTotalRevoked(a.getTotalRevoked() + amount);
            return 1;
        }
    }

    @Override
    public int restoreExpiredForRenewal(Long userId, long amount) {
        BeanAccount a = rows.get(userId);
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            if (amount < 0 || a.getTotalExpired() < amount) {
                return 0;
            }
            a.setBalance(a.getBalance() + amount);
            a.setTotalExpired(a.getTotalExpired() - amount);
            return 1;
        }
    }

    // ===== BaseMapper:仅实现 service 用到的 insert / selectList(Wrapper) =====

    @Override
    public int insert(BeanAccount entity) {
        // 模拟 user_id 唯一约束:已存在则视为撞键(本测试不触发并发建账户)
        rows.putIfAbsent(entity.getUserId(), entity);
        return 1;
    }

    @Override
    public List<BeanAccount> selectList(Wrapper<BeanAccount> queryWrapper) {
        // service 仅按 user_id 查单账户;测试场景只有一个账户,返回全部即可被 selectOne 取首条
        return new ArrayList<>(rows.values());
    }

    // ===== 其余 BaseMapper 抽象方法:非测试路径 =====

    @Override
    public int deleteById(BeanAccount entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int updateById(BeanAccount entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(BeanAccount entity, Wrapper<BeanAccount> updateWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanAccount selectById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BeanAccount> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<BeanAccount> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long selectCount(Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(Wrapper<BeanAccount> queryWrapper, ResultHandler<BeanAccount> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BeanAccount> selectList(IPage<BeanAccount> page, Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(IPage<BeanAccount> page, Wrapper<BeanAccount> queryWrapper,
                           ResultHandler<BeanAccount> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(Wrapper<BeanAccount> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page,
                                                Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<BeanAccount> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<BeanAccount> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> void selectObjs(Wrapper<BeanAccount> queryWrapper, ResultHandler<E> resultHandler) {
        throw new UnsupportedOperationException();
    }
}
