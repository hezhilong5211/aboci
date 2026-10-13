package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 豆子流水 Service 接口(只读查询;流水写入由 IBeanAccountService 在同事务内完成)
 */
public interface IBeanTransactionService extends IService<BeanTransaction> {

    /**
     * 分页查询某用户流水(按时间倒序)。
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 流水分页
     */
    IPage<BeanTransaction> pageByUser(Long userId, Integer pageNum, Integer pageSize);
}
