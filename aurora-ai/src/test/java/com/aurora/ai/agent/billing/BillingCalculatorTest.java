package com.aurora.ai.agent.billing;

import com.aurora.ai.domain.SysAiModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingCalculatorTest {

    private final BillingCalculator calculator = new BillingCalculator();

    @Test
    void shouldUseResolutionSpecificImagePrice() {
        SysAiModel model = imageModel();

        assertEquals(600L, calculator.imageBeans(model, 2, "2k"));
        assertEquals(300L, calculator.imagePrice(model, "2K"));
    }

    @Test
    void shouldUseDefaultResolutionWhenCallerDoesNotSpecifyOne() {
        SysAiModel model = imageModel();
        model.setDefaultResolution("4K");

        assertEquals(900L, calculator.imageBeans(model, 1));
    }

    @Test
    void shouldFallbackToLegacyImagePriceForOldModelData() {
        SysAiModel model = new SysAiModel();
        model.setBeanPerImage(88L);
        model.setDefaultResolution("1K");

        assertEquals(176L, calculator.imageBeans(model, 2, "1K"));
    }

    @Test
    void shouldFallbackToNormalInputPriceWhenCachedPriceIsMissing() {
        SysAiModel model = new SysAiModel();
        model.setBeanPerMtokenInput(20L);
        model.setBeanPerMtokenOutput(40L);

        assertEquals(new BigDecimal("0.04"), calculator.textRaw(model, 1000, 500, 500));
    }

    @Test
    void shouldUseConfiguredCachedInputPriceAndClampInvalidUsage() {
        SysAiModel model = new SysAiModel();
        model.setBeanPerMtokenInput(20L);
        model.setBeanPerMtokenInputCached(5L);

        assertEquals(new BigDecimal("0.005"), calculator.textRaw(model, 1000, 2000, 0));
        assertEquals(new BigDecimal("0.02"), calculator.textRaw(model, 1000, -10, 0));
    }

    @Test
    void shouldRespectExplicitZeroCachedInputPrice() {
        SysAiModel model = new SysAiModel();
        model.setBeanPerMtokenInput(20L);
        model.setBeanPerMtokenInputCached(0L);

        assertEquals(new BigDecimal("0.01"), calculator.textRaw(model, 1000, 500, 0));
    }

    private SysAiModel imageModel() {
        SysAiModel model = new SysAiModel();
        model.setBeanPerImage(50L);
        model.setBeanPerImage1k(100L);
        model.setBeanPerImage2k(300L);
        model.setBeanPerImage4k(900L);
        model.setDefaultResolution("1K");
        return model;
    }
}
