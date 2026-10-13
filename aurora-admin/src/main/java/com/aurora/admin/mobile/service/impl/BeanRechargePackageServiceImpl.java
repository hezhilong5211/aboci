package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.mapper.BeanRechargePackageMapper;
import com.aurora.admin.mobile.mapper.MembershipLevelMapper;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 豆子充值套餐服务实现
 */
@Service
@RequiredArgsConstructor
public class BeanRechargePackageServiceImpl extends ServiceImpl<BeanRechargePackageMapper, BeanRechargePackage>
        implements IBeanRechargePackageService {

    /** 套餐状态:上架 */
    private static final String STATUS_ON_SHELF = "0";
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = new BigDecimal("10000.00");
    private static final Pattern VIRTUAL_PRODUCT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,20}$");

    private final IPayOrderBizService payOrderBizService;
    private final MembershipLevelMapper membershipLevelMapper;
    private final ISysWxConfigService sysWxConfigService;
    private final IUserMembershipService userMembershipService;

    @Override
    public boolean save(BeanRechargePackage entity) {
        normalize(entity);
        validateForWrite(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(BeanRechargePackage entity) {
        if (entity == null || entity.getPackageId() == null) {
            throw new ServiceException("套餐ID不能为空");
        }
        BeanRechargePackage existing = getById(entity.getPackageId());
        if (existing == null) {
            throw new ServiceException("套餐不存在或已删除");
        }
        normalize(entity);
        validateForWrite(mergeForValidation(existing, entity));
        return super.updateById(entity);
    }

    @Override
    public List<BeanRechargePackage> listOnShelf() {
        LambdaQueryWrapper<BeanRechargePackage> wrapper = new LambdaQueryWrapper<BeanRechargePackage>()
                .eq(BeanRechargePackage::getStatus, STATUS_ON_SHELF);
        if (sysWxConfigService.getActiveByType("virtual_pay") != null) {
            wrapper.isNotNull(BeanRechargePackage::getVirtualProductId)
                    .ne(BeanRechargePackage::getVirtualProductId, "");
        }
        return list(wrapper.orderByAsc(BeanRechargePackage::getSort));
    }

    @Override
    public CreateOrderVO createRecharge(Long userId, Long packageId) {
        userMembershipService.requireActiveMembership(userId);
        BeanRechargePackage pkg = getById(packageId);
        if (pkg == null || !STATUS_ON_SHELF.equals(pkg.getStatus())) {
            throw new ServiceException("套餐不存在或已下架");
        }
        if (!isBlank(pkg.getVirtualProductId())) {
            throw new ServiceException("该豆子套餐仅支持微信虚拟支付，请更新或重新打开小程序");
        }
        // 金额一律服务端计算:price(元)×100 转分,绝不信任前端
        int amount = pkg.getPrice().multiply(BigDecimal.valueOf(100)).intValueExact();
        String subject = isBlank(pkg.getName()) ? "充值" + pkg.getBeans() + "豆" : pkg.getName();
        return payOrderBizService.createOrder(userId, amount, subject,
                "BEAN_RECHARGE", packageId, subject);
    }

    /**
     * 后台写入统一校验。上架套餐必须完成虚拟道具映射,价格精确到分。
     */
    private void validateForWrite(BeanRechargePackage pkg) {
        if (pkg == null) {
            throw new ServiceException("豆子套餐不能为空");
        }
        if (isBlank(pkg.getName())) {
            throw new ServiceException("豆子套餐名称不能为空");
        }
        if (pkg.getName().length() > 64) {
            throw new ServiceException("豆子套餐名称不能超过64个字符");
        }
        if (!"0".equals(pkg.getStatus()) && !"1".equals(pkg.getStatus())) {
            throw new ServiceException("套餐状态只能为0或1");
        }
        if (pkg.getBeans() == null || pkg.getBeans() <= 0) {
            throw new ServiceException("套餐到账豆数必须大于0");
        }
        validatePrice(pkg.getPrice());

        String virtualProductId = pkg.getVirtualProductId();
        if (STATUS_ON_SHELF.equals(pkg.getStatus()) && isBlank(virtualProductId)) {
            throw new ServiceException("上架套餐必须填写微信道具ID");
        }
        if (!isBlank(virtualProductId)) {
            validateVirtualProductId(virtualProductId);
            ensureVirtualProductIdUnique(pkg.getPackageId(), virtualProductId);
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new ServiceException("套餐价格必须在0.01到10000元之间");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("套餐价格最多保留2位小数");
        }
    }

    private void validateVirtualProductId(String virtualProductId) {
        if (!VIRTUAL_PRODUCT_ID_PATTERN.matcher(virtualProductId).matches()) {
            throw new ServiceException("微信道具ID仅支持英文字母、数字和下划线，且不能超过20位");
        }
    }

    /**
     * 微信道具ID在豆子套餐与会员两个本地商品表之间也必须全局唯一。
     */
    private void ensureVirtualProductIdUnique(Long packageId, String virtualProductId) {
        Long sameTypeCount = baseMapper.selectCount(new LambdaQueryWrapper<BeanRechargePackage>()
                .eq(BeanRechargePackage::getVirtualProductId, virtualProductId)
                .ne(packageId != null, BeanRechargePackage::getPackageId, packageId));
        if (sameTypeCount != null && sameTypeCount > 0) {
            throw new ServiceException("微信道具ID已被其他豆子套餐使用");
        }
        Long membershipCount = membershipLevelMapper.selectCount(new LambdaQueryWrapper<MembershipLevel>()
                .eq(MembershipLevel::getVirtualProductId, virtualProductId));
        if (membershipCount != null && membershipCount > 0) {
            throw new ServiceException("微信道具ID已被会员等级使用");
        }
    }

    private void normalize(BeanRechargePackage pkg) {
        if (pkg == null) {
            return;
        }
        if (pkg.getName() != null) {
            pkg.setName(pkg.getName().trim());
        }
        if (pkg.getVirtualProductId() != null) {
            String normalized = pkg.getVirtualProductId().trim();
            pkg.setVirtualProductId(normalized.isEmpty() ? null : normalized);
        }
    }

    /**
     * updateById 可能只携带部分字段;校验时与数据库现值合并,避免误判。
     */
    private BeanRechargePackage mergeForValidation(BeanRechargePackage existing, BeanRechargePackage update) {
        BeanRechargePackage merged = new BeanRechargePackage();
        merged.setPackageId(existing.getPackageId());
        merged.setName(update.getName() == null ? existing.getName() : update.getName());
        merged.setVirtualProductId(update.getVirtualProductId() == null
                ? existing.getVirtualProductId() : update.getVirtualProductId());
        merged.setBeans(update.getBeans() == null ? existing.getBeans() : update.getBeans());
        merged.setPrice(update.getPrice() == null ? existing.getPrice() : update.getPrice());
        merged.setStatus(update.getStatus() == null ? existing.getStatus() : update.getStatus());
        return merged;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
