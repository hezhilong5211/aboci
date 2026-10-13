package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.aurora.admin.mobile.service.IBeanTransactionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 豆子流水 Service 实现(只读查询)
 */
@Service
public class BeanTransactionServiceImpl extends ServiceImpl<BeanTransactionMapper, BeanTransaction>
        implements IBeanTransactionService {

    @Override
    public IPage<BeanTransaction> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<BeanTransaction> wrapper = new LambdaQueryWrapper<BeanTransaction>()
                .eq(BeanTransaction::getUserId, userId)
                .orderByDesc(BeanTransaction::getCreateTime)
                .orderByDesc(BeanTransaction::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
