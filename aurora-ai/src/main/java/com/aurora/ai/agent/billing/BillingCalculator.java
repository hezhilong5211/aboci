package com.aurora.ai.agent.billing;

import com.aurora.ai.domain.SysAiModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 算费器(纯函数,无外部依赖)。
 *
 * <p>职责:把模型计费单价(豆子/单位,{@link SysAiModel} 上的<b>整数</b>字段)与实际用量
 * (token 数 / 图片张数 / 视频秒数)换算成应扣「豆子」整数。
 *
 * <p>统一口径(已确认决策):
 * <ul>
 *   <li>豆子与单价均为<b>整数</b>;为按百万 token 折算精确并 ceil,内部用 {@link BigDecimal} 计算。</li>
 *   <li>取整一律 <b>向上取整 ceil</b>({@link RoundingMode#CEILING})。</li>
 *   <li>同一次扣费动作合并后只 ceil 一次(不逐项 ceil,避免取整误差累积放大)。
 *       因此文本各档先算出「豆子小数」再交给 {@link #ceilBeans} 合并取整。</li>
 *   <li>缓存命中单价未配置时回退普通输入单价，避免上游返回缓存 token 后被错误按 0 计费；
 *       其他单价为 {@code null} 仍视为 0。</li>
 * </ul>
 */
@Component
public class BillingCalculator {

    /** 每百万 token 计价的分母(单价单位为「豆子 / 百万 token」)。 */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    /**
     * 文本(chat / vision)单轮的「豆子小数」(未取整)。
     *
     * <p>计费分三档(单价均为「豆子 / 百万 token」):
     * <ul>
     *   <li>缓存命中输入:{@code cachedTokens × beanPerMtokenInputCached}</li>
     *   <li>缓存未命中输入:{@code (inputTokens − cachedTokens) × beanPerMtokenInput}</li>
     *   <li>输出:{@code outputTokens × beanPerMtokenOutput}</li>
     * </ul>
     * 三档之和再除以 1_000_000,得到该轮消耗的「豆子小数」。
     *
     * <p>说明:
     * <ul>
     *   <li>{@code inputTokens} 是<b>总输入</b>(含缓存命中部分),故「未命中 = 总输入 − 命中」。</li>
     *   <li>对无缓存档的模型(如视觉 vision,只填 input/output 单价),调用方将
     *       {@code cachedTokens} 传 0:命中档 0 贡献,未命中档退化为「全部输入 × input 单价」,
     *       自然退化为两档计费。</li>
     *   <li>缓存命中单价 {@code null} 时按普通输入单价计费；显式配置后才使用优惠价。</li>
     * </ul>
     *
     * <p>举例(DeepSeek 三档整数单价:input=2 / inputCached=1 / output=8 豆/百万 token):
     * 总输入 1000、命中 400、输出 500 →
     * 命中 400×1 + 未命中 (1000−400)×2 + 输出 500×8 = 400 + 1200 + 4000 = 5600,
     * 除以 1e6 = 0.0056(豆子小数,未取整;按百万 token 折算后仍可能为小数,故需后续 ceil)。
     *
     * @param model        模型配置(取三档单价)
     * @param inputTokens  总输入 token 数(含缓存命中)
     * @param cachedTokens 缓存命中的输入 token 数(无缓存模型传 0)
     * @param outputTokens 输出 token 数
     * @return 该轮消耗的豆子小数(未取整);用量为 0 或单价全空时返回 0
     */
    public BigDecimal textRaw(SysAiModel model, long inputTokens, long cachedTokens, long outputTokens) {
        BigDecimal inputPrice = nz(model.getBeanPerMtokenInput());
        BigDecimal cachedPrice = model.getBeanPerMtokenInputCached() == null
                ? inputPrice : nz(model.getBeanPerMtokenInputCached());
        BigDecimal outputPrice = nz(model.getBeanPerMtokenOutput());

        // 未命中输入 = 总输入 − 命中输入
        long normalizedCachedTokens = Math.max(0L, Math.min(inputTokens, cachedTokens));
        long missTokens = inputTokens - normalizedCachedTokens;

        BigDecimal cachedCost = cachedPrice.multiply(BigDecimal.valueOf(normalizedCachedTokens));
        BigDecimal missCost = inputPrice.multiply(BigDecimal.valueOf(missTokens));
        BigDecimal outputCost = outputPrice.multiply(BigDecimal.valueOf(outputTokens));

        // 三档求和后按百万 token 折算(不在此处取整,留给 ceilBeans 合并 ceil)。
        // 分子 = 单价(最多 4 位小数)× 整数 token,分母 1_000_000 的质因子仅含 2、5,
        // 故除法结果必为有限小数,divide 不指定 scale 也不会抛 ArithmeticException(无损保留全部精度)。
        return cachedCost.add(missCost).add(outputCost).divide(MILLION);
    }

    /**
     * 把若干「豆子小数」相加后<b>一次性向上取整</b>为豆子整数。
     *
     * <p>用于把一次扣费动作内的多个分项(如 DeepSeek 多轮累加的小数 + 视觉小数)合并后只 ceil 一次。
     *
     * <p>举例:{@code ceilBeans(0.0054, 0.0021)} = ceil(0.0075) = 1。
     *
     * @param raws 各分项的豆子小数(可为 0;不应传 null)
     * @return 合并后向上取整的豆子整数
     */
    public long ceilBeans(BigDecimal... raws) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal raw : raws) {
            if (raw != null) {
                sum = sum.add(raw);
            }
        }
        // 合并后只 ceil 一次;longValueExact 保证不丢精度(超界会抛异常,豆子量级不会触发)
        return sum.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /**
     * 图片应扣豆子 = ceil(张数 × 每张单价)。
     *
     * <p>举例:{@code beanPerImage=20}(整数单价),count=3 → ceil(60) = 60。
     *
     * @param model 模型配置(取 {@code beanPerImage})
     * @param count 图片张数(预扣传 n,结算传成功张数;不应为负)
     * @return 向上取整的豆子整数;单价为 null 或 count 为 0 时返回 0
     */
    public long imageBeans(SysAiModel model, int count) {
        return imageBeans(model, count, null);
    }

    /**
     * 按实际分辨率计算图片豆子；分档单价未配置时兼容沿用旧统一单价。
     *
     * @param model      模型配置
     * @param count      图片张数
     * @param resolution 分辨率；为空时使用模型默认分辨率
     * @return 应扣豆子整数
     */
    public long imageBeans(SysAiModel model, int count, String resolution) {
        BigDecimal price = BigDecimal.valueOf(imagePrice(model, resolution));
        BigDecimal raw = price.multiply(BigDecimal.valueOf(count));
        return raw.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /** 返回指定分辨率每张单价；分档值为空时回退旧统一单价。 */
    public long imagePrice(SysAiModel model, String resolution) {
        String resolved = resolution;
        if (resolved == null || resolved.isBlank()) {
            resolved = model.getDefaultResolution();
        }
        Long price = switch (resolved == null ? "" : resolved.trim().toUpperCase(Locale.ROOT)) {
            case "1K" -> model.getBeanPerImage1k();
            case "2K" -> model.getBeanPerImage2k();
            case "4K" -> model.getBeanPerImage4k();
            default -> null;
        };
        Long fallback = price != null ? price : model.getBeanPerImage();
        return fallback == null ? 0L : fallback;
    }

    /**
     * 视频应扣豆子 = ceil(秒数 × 每秒单价)。
     *
     * <p>本期仅算费函数就绪,扣费链路未接入。
     *
     * <p>举例:{@code beanPerSecond=5}(整数单价),seconds=10 → ceil(50) = 50。
     *
     * @param model   模型配置(取 {@code beanPerSecond})
     * @param seconds 视频时长(秒;不应为负)
     * @return 向上取整的豆子整数;单价为 null 或 seconds 为 0 时返回 0
     */
    public long videoBeans(SysAiModel model, int seconds) {
        BigDecimal price = nz(model.getBeanPerSecond());
        BigDecimal raw = price.multiply(BigDecimal.valueOf(seconds));
        return raw.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /** 单价(整数,豆子/单位)空值归一:null 视为 0;转 BigDecimal 供后续按百万 token 精确折算。 */
    private static BigDecimal nz(Long price) {
        return price == null ? BigDecimal.ZERO : BigDecimal.valueOf(price);
    }
}
