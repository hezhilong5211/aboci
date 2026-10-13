package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 记录式流水 Mapper:仅捕获 insert 的流水到线程安全列表,供断言流水条数/快照。
 * 其余 BaseMapper 抽象方法非测试路径,抛 UnsupportedOperationException。
 */
class RecordingTransactionMapper implements BeanTransactionMapper {

    /** 已写入的流水(线程安全,供并发测试断言) */
    final List<BeanTransaction> records = new CopyOnWriteArrayList<>();

    @Override
    public int insert(BeanTransaction entity) {
        if (entity.getId() == null) {
            entity.setId((long) records.size() + 1L);
        }
        records.add(entity);
        return 1;
    }

    @Override
    public BeanTransaction selectRefundRevoke(Long userId, String bizNo) {
        return find(userId, "REVOKE", bizNo, null, false);
    }

    @Override
    public List<BeanTransaction> selectLedgerByUserId(Long userId) {
        return records.stream()
                .filter(tx -> java.util.Objects.equals(userId, tx.getUserId()))
                .sorted(java.util.Comparator.comparing(BeanTransaction::getId))
                .toList();
    }

    @Override
    public BeanTransaction selectRenewalRestoreByOrder(Long userId, String renewalOrderNo) {
        return find(userId, "RENEW_RESTORE", renewalOrderNo, null, false);
    }

    @Override
    public BeanTransaction selectRenewalRestoreBySource(Long userId, String expiryBizNo) {
        return find(userId, "RENEW_RESTORE", null, expiryBizNo, true);
    }

    @Override
    public BeanTransaction selectMembershipExpiry(Long userId, String expiryBizNo) {
        return find(userId, "EXPIRE", expiryBizNo, null, false);
    }

    @Override
    public BeanTransaction selectLatestDeduct(Long userId, String bizNo) {
        return find(userId, "DEDUCT", bizNo, null, false);
    }

    @Override
    public BeanTransaction selectLatestRefund(Long userId, String bizNo) {
        return find(userId, "REFUND", bizNo, null, false);
    }

    @Override
    public Long selectExpiredAmountForRenewal(Long userId, String expiryBizNo) {
        return records.stream()
                .filter(tx -> java.util.Objects.equals(userId, tx.getUserId()))
                .filter(tx -> "EXPIRE".equals(tx.getType()))
                .filter(tx -> java.util.Objects.equals(expiryBizNo, tx.getBizNo())
                        || java.util.Objects.equals(expiryBizNo, tx.getSourceBizNo())
                        || tx.getSourceBizNo() != null
                        && tx.getSourceBizNo().startsWith(expiryBizNo + "|"))
                .map(BeanTransaction::getChangeValue)
                .filter(java.util.Objects::nonNull)
                .filter(value -> value < 0)
                .mapToLong(value -> -value)
                .sum();
    }

    private BeanTransaction find(Long userId, String type, String bizNo,
                                 String sourceBizNo, boolean matchSource) {
        for (int i = records.size() - 1; i >= 0; i--) {
            BeanTransaction tx = records.get(i);
            if (!java.util.Objects.equals(userId, tx.getUserId())
                    || !java.util.Objects.equals(type, tx.getType())) {
                continue;
            }
            String actual = matchSource ? tx.getSourceBizNo() : tx.getBizNo();
            String expected = matchSource ? sourceBizNo : bizNo;
            if (java.util.Objects.equals(expected, actual)) {
                return tx;
            }
        }
        return null;
    }

    @Override
    public int deleteById(BeanTransaction entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int updateById(BeanTransaction entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(BeanTransaction entity, Wrapper<BeanTransaction> updateWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanTransaction selectById(Serializable id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BeanTransaction> selectBatchIds(Collection<? extends Serializable> idList) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<BeanTransaction> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long selectCount(Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BeanTransaction> selectList(Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(Wrapper<BeanTransaction> queryWrapper, ResultHandler<BeanTransaction> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BeanTransaction> selectList(IPage<BeanTransaction> page, Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectList(IPage<BeanTransaction> page, Wrapper<BeanTransaction> queryWrapper,
                           ResultHandler<BeanTransaction> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(Wrapper<BeanTransaction> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page,
                                                Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<BeanTransaction> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<BeanTransaction> queryWrapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> void selectObjs(Wrapper<BeanTransaction> queryWrapper, ResultHandler<E> resultHandler) {
        throw new UnsupportedOperationException();
    }
}
