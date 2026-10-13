package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.GlobalPrompt;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 全局提示词 Service 接口
 * 「全局唯一生效」:同一时刻仅一条 is_active=1,切换由实现类事务保证(先全表置0再置目标为1)。
 */
public interface IGlobalPromptService extends IService<GlobalPrompt> {

    /**
     * 取当前生效的一条(is_active=1);无则返回 null。
     *
     * @return 当前生效的提示词,无则 null
     */
    GlobalPrompt getActive();

    /**
     * 历史版本分页查询,create_time 倒序。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<GlobalPrompt> pageHistory(Integer pageNum, Integer pageSize);

    /**
     * 保存新版本并即时生效。
     * 事务内:先把当前生效版本置 0,再插入新内容(is_active=1)。
     *
     * @param content 提示词内容(已由调用方做 htmlUnescape 或由本方法内部处理)
     */
    void saveAndActivate(String content);

    /**
     * 启用某历史版本。
     * 事务内:先把当前生效版本置 0,再把目标版本置 1。
     *
     * @param id 目标版本 ID
     */
    void activate(Long id);
}
