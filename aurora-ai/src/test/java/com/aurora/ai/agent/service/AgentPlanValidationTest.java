package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import com.aurora.common.core.exception.ServiceException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 格式错误的引用字段必须立即拒绝，不能降级成收费的纯文生图。 */
class AgentPlanValidationTest {

    @ParameterizedTest
    @CsvSource({
            "reference_attachment_ids, MISSING",
            "reference_attachment_ids, STRING",
            "reference_attachment_ids, NUMBER",
            "generated_reference_seqs, STRING",
            "generated_reference_seqs, NUMBER",
            "generated_reference_seqs, NONEMPTY",
            "reference_bindings, GENERATED"
    })
    void shouldRejectMalformedReferenceFieldsWithoutChargingOrSubmitting(String field, String invalidType)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        IGenerationTaskService generationService = mock(IGenerationTaskService.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        AgentService service = new AgentService(
                mock(SysAiModelMapper.class), mock(IConversationService.class), mock(IMessageService.class),
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class), generationService,
                mock(PromptAssembler.class), mock(ProfileStorage.class), mock(EncryptUtils.class), mapper,
                new BillingCalculator(), mock(ResponsesApiClient.class), new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);

        Object referenceImage = privateInstance("ReferenceImage", "product-attachment",
                "/profile/agent_reference/7/product.png");
        Object context = privateInstance("ReferenceContext", 21L, "按上传的商品参考生成1张图片。",
                List.of(referenceImage), false, null, 0, Set.of(), null, null);
        Object state = privateInstance("ToolExecutionState");
        ObjectNode args = mapper.createObjectNode();
        args.put("request_update", "REPLACE");
        args.put("reference_update", "REPLACE");
        args.put("effective_request", "最终输出1张商品正面照片，使用上传素材的商品形态。");
        args.put("expected_count", 1);
        ObjectNode job = args.putArray("jobs").addObject();
        job.put("seq", 1);
        job.put("prompt", "商品正面照片，保持指定商品形态。");
        job.putArray("reference_attachment_ids");
        job.putArray("generated_reference_seqs");
        job.putArray("reference_bindings");
        switch (invalidType) {
            case "MISSING" -> job.remove(field);
            case "STRING" -> job.put(field, "product-attachment");
            case "NUMBER" -> job.put(field, 1);
            case "NONEMPTY" -> job.putArray(field).add(1);
            case "GENERATED" -> {
                var binding = job.putArray(field).addObject();
                binding.put("source_type", "generated");
                binding.put("source_id", "1");
                binding.put("usage", "primary");
                binding.putArray("attributes").add("identity");
            }
            default -> throw new AssertionError("未知测试输入");
        }

        assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(service, "doGenerateImages", args,
                7L, 33L, 21L, context, new ArrayList<>(), state));

        verifyNoInteractions(generationService, beanDeductPort);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            MISSING | 1
            null | 1
            "1" | 1
            1.0 | 1
            1.5 | 1
            true | 1
            4294967297 | 1
            0 | 1
            10 | 10
            1 | 0
            1 | 2
            2 | 1
            1 | -1
            1 | -2
            """)
    void shouldRejectInvalidCountContractBeforeChargingSubmittingOrClarifying(String countJson, int jobCount)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        IGenerationTaskService generationService = mock(IGenerationTaskService.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        AgentService service = new AgentService(
                mock(SysAiModelMapper.class), mock(IConversationService.class), mock(IMessageService.class),
                mock(IMessageAttachmentService.class), scopeService, generationService,
                mock(PromptAssembler.class), mock(ProfileStorage.class), mock(EncryptUtils.class), mapper,
                new BillingCalculator(), mock(ResponsesApiClient.class), new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        Object context = privateInstance("ReferenceContext", 21L, "拍一张商品照片。",
                List.of(), false, null, 0, Set.of(), null, null);
        ObjectNode args = mapper.createObjectNode();
        args.put("request_update", "REPLACE");
        args.put("effective_request", "拍一张商品照片。");
        if (!"MISSING".equals(countJson)) args.set("expected_count", mapper.readTree(countJson));
        if (jobCount == -2) {
            args.put("jobs", "商品照片");
        } else if (jobCount >= 0) {
            var jobs = args.putArray("jobs");
            for (int i = 0; i < jobCount; i++) {
                ObjectNode job = jobs.addObject();
                job.put("prompt", "商品照片");
                job.putArray("reference_attachment_ids");
                job.putArray("generated_reference_seqs");
                job.putArray("reference_bindings");
            }
        }

        assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(service, "doGenerateImages", args,
                7L, 33L, 21L, context, new ArrayList<>(), privateInstance("ToolExecutionState")));
        verifyNoInteractions(generationService, beanDeductPort, scopeService);
    }

    private static Object privateInstance(String nestedType, Object... arguments) throws Exception {
        Class<?> type = Class.forName(AgentService.class.getName() + "$" + nestedType);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }
}
