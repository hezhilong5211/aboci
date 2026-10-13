package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.mapper.BeanRechargePackageMapper;
import com.aurora.admin.mobile.mapper.MembershipLevelMapper;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 会员等级配置 Service 实现
 * 逻辑删走 removeById(@TableLogic delFlag);删除仅从配置列表消失,历史快照不受影响。
 */
@Service
@RequiredArgsConstructor
public class MembershipLevelServiceImpl extends ServiceImpl<MembershipLevelMapper, MembershipLevel>
        implements IMembershipLevelService {

    private static final String STATUS_ON_SALE = "0";
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = new BigDecimal("10000.00");
    private static final Pattern VIRTUAL_PRODUCT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,20}$");

    private final BeanRechargePackageMapper beanRechargePackageMapper;
    private final ISysWxConfigService sysWxConfigService;

    @Override
    public boolean save(MembershipLevel entity) {
        normalize(entity);
        validateForWrite(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(MembershipLevel entity) {
        if (entity == null || entity.getLevelId() == null) {
            throw new ServiceException("等级ID不能为空");
        }
        MembershipLevel existing = getById(entity.getLevelId());
        if (existing == null) {
            throw new ServiceException("会员等级不存在或已删除");
        }
        normalize(entity);
        validateForWrite(mergeForValidation(existing, entity));
        return super.updateById(entity);
    }

    @Override
    public List<MembershipLevel> listBySort() {
        return list(new LambdaQueryWrapper<MembershipLevel>().orderByAsc(MembershipLevel::getSort));
    }

    @Override
    public List<MembershipLevel> listOnSale() {
        LambdaQueryWrapper<MembershipLevel> wrapper = new LambdaQueryWrapper<MembershipLevel>()
                .eq(MembershipLevel::getStatus, STATUS_ON_SALE);
        if (sysWxConfigService.getActiveByType("virtual_pay") != null) {
            wrapper.isNotNull(MembershipLevel::getVirtualProductId)
                    .ne(MembershipLevel::getVirtualProductId, "");
        }
        return list(wrapper.orderByAsc(MembershipLevel::getSort));
    }

    /**
     * 后台写入统一校验。上架商品必须完成虚拟道具映射,价格精确到分。
     */
    private void validateForWrite(MembershipLevel level) {
        if (level == null) {
            throw new ServiceException("会员等级不能为空");
        }
        if (isBlank(level.getName())) {
            throw new ServiceException("会员等级名称不能为空");
        }
        if (level.getName().length() > 64) {
            throw new ServiceException("会员等级名称不能超过64个字符");
        }
        validateStatus(level.getStatus());
        validatePrice(level.getPrice());
        if (!MembershipPolicy.isValidDurationDays(level.getDurationDays())) {
            throw new ServiceException("会员时长必须在1到3650天之间");
        }
        if (level.getGrantBeans() == null || level.getGrantBeans() < 0) {
            throw new ServiceException("会员赠豆数不能小于0");
        }

        String virtualProductId = level.getVirtualProductId();
        if (STATUS_ON_SALE.equals(level.getStatus()) && isBlank(virtualProductId)) {
            throw new ServiceException("上架会员必须填写微信道具ID");
        }
        if (!isBlank(virtualProductId)) {
            validateVirtualProductId(virtualProductId);
            ensureVirtualProductIdUnique(level.getLevelId(), virtualProductId);
        }
    }

    private void validateStatus(String status) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException("会员状态只能为0或1");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new ServiceException("会员价格必须在0.01到10000元之间");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("会员价格最多保留2位小数");
        }
    }

    private void validateVirtualProductId(String virtualProductId) {
        if (!VIRTUAL_PRODUCT_ID_PATTERN.matcher(virtualProductId).matches()) {
            throw new ServiceException("微信道具ID仅支持英文字母、数字和下划线，且不能超过20位");
        }
    }

    /**
     * 微信道具ID在会员与豆子套餐两个本地商品表之间也必须全局唯一。
     */
    private void ensureVirtualProductIdUnique(Long levelId, String virtualProductId) {
        Long sameTypeCount = baseMapper.selectCount(new LambdaQueryWrapper<MembershipLevel>()
                .eq(MembershipLevel::getVirtualProductId, virtualProductId)
                .ne(levelId != null, MembershipLevel::getLevelId, levelId));
        if (sameTypeCount != null && sameTypeCount > 0) {
            throw new ServiceException("微信道具ID已被其他会员等级使用");
        }
        Long beanPackageCount = beanRechargePackageMapper.selectCount(
                new LambdaQueryWrapper<BeanRechargePackage>()
                        .eq(BeanRechargePackage::getVirtualProductId, virtualProductId));
        if (beanPackageCount != null && beanPackageCount > 0) {
            throw new ServiceException("微信道具ID已被豆子套餐使用");
        }
    }

    private void normalize(MembershipLevel level) {
        if (level == null) {
            return;
        }
        if (level.getName() != null) {
            level.setName(level.getName().trim());
        }
        if (level.getVirtualProductId() != null) {
            String normalized = level.getVirtualProductId().trim();
            level.setVirtualProductId(normalized.isEmpty() ? null : normalized);
        }
    }

    /**
     * updateById 可能只携带部分字段;校验时与数据库现值合并,避免误判。
     */
    private MembershipLevel mergeForValidation(MembershipLevel existing, MembershipLevel update) {
        MembershipLevel merged = new MembershipLevel();
        merged.setLevelId(existing.getLevelId());
        merged.setName(update.getName() == null ? existing.getName() : update.getName());
        merged.setGrantBeans(update.getGrantBeans() == null ? existing.getGrantBeans() : update.getGrantBeans());
        merged.setPrice(update.getPrice() == null ? existing.getPrice() : update.getPrice());
        merged.setDurationDays(update.getDurationDays() == null
                ? existing.getDurationDays() : update.getDurationDays());
        merged.setStatus(update.getStatus() == null ? existing.getStatus() : update.getStatus());
        merged.setVirtualProductId(update.getVirtualProductId() == null
                ? existing.getVirtualProductId() : update.getVirtualProductId());
        return merged;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
