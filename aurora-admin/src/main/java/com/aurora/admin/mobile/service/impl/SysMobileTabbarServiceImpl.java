package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import com.aurora.admin.mobile.mapper.SysMobileTabbarMapper;
import com.aurora.admin.mobile.service.ISysMobileTabbarService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 移动端底部导航配置 Service 实现类
 */
@Service
public class SysMobileTabbarServiceImpl extends ServiceImpl<SysMobileTabbarMapper, SysMobileTabbar>
        implements ISysMobileTabbarService {

    @Override
    public List<SysMobileTabbar> listAllOrderBySort() {
        LambdaQueryWrapper<SysMobileTabbar> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMobileTabbar::getSort);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editEditable(SysMobileTabbar body) {
        if (body.getVisible() != null && body.getVisible() != 0 && body.getVisible() != 1) {
            throw new ServiceException("启用状态只能为 0 或 1");
        }
        List<SysMobileTabbar> lockedTabs = baseMapper.selectAllForUpdate();
        SysMobileTabbar existing = lockedTabs.stream()
                .filter(tab -> tab.getTabId().equals(body.getTabId()))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            return false;
        }
        if (Integer.valueOf(0).equals(body.getVisible())
                && Integer.valueOf(1).equals(existing.getVisible())) {
            long enabledCount = lockedTabs.stream()
                    .filter(tab -> Integer.valueOf(1).equals(tab.getVisible()))
                    .count();
            if (enabledCount <= 1) {
                throw new ServiceException("底部导航至少保留一项启用");
            }
        }
        // 仅按主键更新可改字段(含 sort,供拖拽排序);tab_key、page_path 只读不入更新集合,避免被前端篡改。
        LambdaUpdateWrapper<SysMobileTabbar> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysMobileTabbar::getTabId, body.getTabId())
                .set(SysMobileTabbar::getTabName, body.getTabName())
                .set(SysMobileTabbar::getIconNormal, body.getIconNormal())
                .set(SysMobileTabbar::getIconActive, body.getIconActive())
                .set(SysMobileTabbar::getVisible, body.getVisible())
                .set(SysMobileTabbar::getSort, body.getSort());
        return update(wrapper);
    }
}
