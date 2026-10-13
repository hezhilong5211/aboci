package com.aurora.ai.mq;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.GptsImageClient;
import com.aurora.ai.provider.YunwuImageClient;
import com.aurora.ai.provider.support.ImageConcurrencyGate;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskMessage;
import com.aurora.framework.mq.TaskProducer;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationTaskConsumerReferenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final IGenerationTaskService tasks = mock(IGenerationTaskService.class);
    private final YunwuImageClient yunwu = mock(YunwuImageClient.class);
    private final GptsImageClient gpts = mock(GptsImageClient.class);
    private final ProfileStorage storage = mock(ProfileStorage.class);
    private final IBeanDeductPort beans = mock(IBeanDeductPort.class);
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, byte[]> generated = new ConcurrentHashMap<>();
    private final Map<String, List<byte[]>> sent = new ConcurrentHashMap<>();
    private GenerationTaskConsumer consumer;
    private GenerationTask task;

    @BeforeEach
    void setUp() throws Exception {
        SysAiModelMapper models = mock(SysAiModelMapper.class);
        EncryptUtils encryption = mock(EncryptUtils.class);
        SysAiModel model = new SysAiModel();
        model.setId(14L);
        model.setProvider("yunwu");
        model.setModelDeployment("gpt-image-2");
        model.setBaseUrl("https://provider.invalid");
        model.setApiKey("encrypted");
        model.setBeanPerImage(10L);
        when(models.selectById(14L)).thenReturn(model);
        when(encryption.decrypt("encrypted")).thenReturn("private-test-key");
        when(storage.read(anyString())).thenAnswer(invocation -> {
            byte[] bytes = files.get(invocation.getArgument(0));
            if (bytes == null) throw new ServiceException("图片文件不存在: private-path");
            return bytes;
        });
        when(storage.write(anyString(), any(byte[].class))).thenAnswer(invocation -> {
            String url = "/profile/" + invocation.getArgument(0);
            files.put(url, invocation.getArgument(1));
            return url;
        });
        when(yunwu.imageToImage(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(3);
            sent.put(prompt, List.copyOf(invocation.getArgument(6)));
            return generated.get(prompt);
        });
        when(yunwu.textToImage(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenAnswer(invocation -> generated.get(invocation.getArgument(3)));
        consumer = new GenerationTaskConsumer(mock(StringRedisTemplate.class), MAPPER,
                mock(TaskProducer.class), tasks, yunwu, gpts, models, encryption,
                new BillingCalculator(), mock(ImageConcurrencyGate.class), storage);
        ReflectionTestUtils.setField(consumer, "beanDeductPort", beans);
        task = new GenerationTask();
        task.setId(101L);
        task.setTaskId("reference-task");
        task.setUserId(7L);
        task.setModelId(14L);
        task.setPrompt("legacy");
        task.setResolution("1K");
        task.setAspectRatio("3:4");
        when(tasks.getById(101L)).thenReturn(task);
        for (int i = 1; i <= 3; i++) files.put("/profile/ref-" + i + ".png", png(i));
        generated.put("first", png(11));
        generated.put("second", png(12));
        generated.put("third", png(13));
        generated.put("legacy", png(14));
    }

    @Test
    void shouldSendOnlyOriginalIndexesInDeclaredOrderAndLogHashes() throws Exception {
        batch(List.of(
                item(1, "first", List.of(1), List.of()),
                item(2, "second", List.of(3), List.of()),
                item(3, "third", List.of(3, 1), List.of())));
        Logger logger = (Logger) LoggerFactory.getLogger(GenerationTaskConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            run();
            assertThat(sent.get("third")).containsExactly(files.get("/profile/ref-3.png"), files.get("/profile/ref-1.png"));
            List<String> manifest = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.startsWith("出图实际输入:") && message.contains("seq=3,"))
                    .toList();
            assertThat(manifest).hasSize(2);
            assertThat(manifest.get(0)).contains("input=1, source=UPLOADED, sourceIndex=3",
                    "sha256=" + hash(files.get("/profile/ref-3.png")));
            assertThat(manifest.get(1)).contains("input=2, source=UPLOADED, sourceIndex=1");
            assertThat(String.join("\n", manifest)).doesNotContain("/profile/", "private-test-key", "provider.invalid");
            verify(tasks).setBeanCost("reference-task", 30L);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldFailMissingMiddleInputWithoutShiftingReferencesOrUnrelatedResultSlots() throws Exception {
        files.remove("/profile/ref-2.png");
        batch(List.of(
                item(1, "first", List.of(1, 2, 3), List.of()),
                item(2, "second", List.of(3), List.of()),
                item(3, "third", List.of(1), List.of())));

        run();

        assertThat(sent.keySet()).containsExactlyInAnyOrder("second", "third");
        assertThat(sent.get("second")).containsExactly(files.get("/profile/ref-3.png"));
        ArgumentCaptor<List<String>> results = listCaptor();
        verify(tasks).markStatus(eq("reference-task"), eq("SUCCEEDED"), results.capture(), eq(2), anyString());
        assertThat(results.getValue()).hasSize(3);
        assertThat(results.getValue().get(0)).isEmpty();
        assertThat(results.getValue().get(1)).startsWith("/profile/generated/");
        assertThat(results.getValue().get(2)).startsWith("/profile/generated/");
        verify(yunwu, never()).textToImage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(beans).refund(7L, 10L, "img:reference-task", "出图失败退还");
        verify(tasks).setBeanCost("reference-task", 20L);
    }

    @Test
    void shouldRejectLegacyDependencyAndRefundWithoutAnyGeneration() throws Exception {
        batch(List.of(item(1, "first", List.of(1), List.of()), item(2, "second", List.of(3), List.of(1))));
        doThrow(new ServiceException("deleted after generation"))
                .when(storage).read(startsWith("/profile/generated/"));

        run();

        assertThat(sent).isEmpty();
        verifyNoInteractions(yunwu, gpts, storage);
        verify(yunwu, never()).textToImage(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(beans).refund(7L, 20L, "img:reference-task", "出图失败退还");
    }

    @Test
    void shouldStartLaterImageBeforeFirstFinishesAndKeepItWhenFirstFails() throws Exception {
        batch(List.of(item(1, "first", List.of(1), List.of()),
                item(2, "second", List.of(2), List.of())));
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            String prompt = invocation.getArgument(3);
            if ("first".equals(prompt)) {
                if (!secondStarted.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("第二张错误地等待首张完成");
                }
                throw new ServiceException("模拟首张供应商拒绝");
            }
            secondStarted.countDown();
            sent.put(prompt, List.copyOf(invocation.getArgument(6)));
            return generated.get(prompt);
        }).when(yunwu).imageToImage(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
        run();
        assertThat(secondStarted.getCount()).isZero();
        assertThat(sent.get("second")).containsExactly(files.get("/profile/ref-2.png"));
        ArgumentCaptor<List<String>> results = listCaptor();
        verify(tasks).markStatus(eq("reference-task"), eq("SUCCEEDED"), results.capture(), eq(1), anyString());
        assertThat(results.getValue().get(0)).isEmpty();
        assertThat(results.getValue().get(1)).startsWith("/profile/generated/");
        verify(beans).refund(7L, 10L, "img:reference-task", "出图失败退还");
    }

    @Test
    void shouldKeepLegacyAndSingleRetryFrozenOrderIncludingFormerGeneratedReference() throws Exception {
        task.setN(1);
        files.put("/profile/generated/previous.png", png(25));
        task.setRefImages(MAPPER.writeValueAsString(List.of("/profile/generated/previous.png",
                "/profile/ref-3.png", "/profile/ref-1.png")));

        run();

        assertThat(sent.get("legacy")).containsExactly(files.get("/profile/generated/previous.png"),
                files.get("/profile/ref-3.png"), files.get("/profile/ref-1.png"));
        verify(tasks).setBeanCost("reference-task", 10L);
    }

    @Test
    void shouldFailLegacyMissingReferenceInsteadOfFallingBackToTextGeneration() throws Exception {
        task.setN(1);
        task.setRefImages("[\"/profile/absent.png\"]");

        run();

        verifyNoInteractions(yunwu, gpts);
        verify(tasks).markStatus("reference-task", "FAILED", null, 0, "第1张参考图不可用，请检查素材后重试");
        verify(beans).refund(7L, 10L, "img:reference-task", "出图失败退还");
    }

    @Test
    void shouldRejectMismatchBetweenFrozenOriginalIndexesAndFileOrder() throws Exception {
        batch(List.of(Map.of("seq", 1, "prompt", "first", "referenceImageIndexes", List.of(1, 3),
                "refImages", List.of("/profile/ref-3.png", "/profile/ref-1.png"), "generatedReferenceSeqs", List.of())));

        run();

        verifyNoInteractions(yunwu, gpts, storage);
        verify(tasks).markStatus("reference-task", "FAILED", null, 0, "出图参考图编号与冻结素材不一致");
        verify(beans).refund(7L, 10L, "img:reference-task", "出图失败退还");
    }

    @Test
    void shouldAcknowledgeMalformedFrozenJsonAsFailedWithoutProviderCall() throws Exception {
        task.setN(1);
        task.setRefImages("{broken");

        run();

        verifyNoInteractions(yunwu, gpts);
        verify(tasks).markStatus("reference-task", "FAILED", null, 0, "出图参考图列表格式错误");
        verify(beans).refund(7L, 10L, "img:reference-task", "出图失败退还");
    }

    @Test
    void shouldContinueLegacyBatchWithoutOriginalIndexMetadata() throws Exception {
        task.setN(1);
        task.setRequestItems("[{\"seq\":1,\"prompt\":\"first\",\"refImages\":[\"/profile/ref-3.png\",\"/profile/ref-1.png\"]}]");

        run();

        assertThat(sent.get("first")).containsExactly(files.get("/profile/ref-3.png"), files.get("/profile/ref-1.png"));
    }

    @Test
    void shouldStillAllowExplicitTextOnlyLegacyTask() throws Exception {
        task.setN(1);

        run();

        verify(yunwu).textToImage("https://provider.invalid", "private-test-key", "gpt-image-2", "legacy", "1K", "3:4");
        assertThat(sent).isEmpty();
        verify(tasks).setBeanCost("reference-task", 10L);
    }

    private void batch(List<Map<String, Object>> items) throws Exception {
        task.setN(items.size());
        task.setRefImages(MAPPER.writeValueAsString(List.of("/profile/ref-1.png", "/profile/ref-2.png", "/profile/ref-3.png")));
        task.setRequestItems(MAPPER.writeValueAsString(items));
    }

    private static Map<String, Object> item(int seq, String prompt, List<Integer> indexes, List<Integer> generated) {
        return Map.of("seq", seq, "prompt", prompt, "referenceImageIndexes", indexes,
                "refImages", indexes.stream().map(index -> "/profile/ref-" + index + ".png").toList(),
                "generatedReferenceSeqs", generated);
    }

    private void run() throws Exception {
        consumer.processTask(TaskMessage.create("generation-task", Map.of("generationTaskId", 101L)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<List<String>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private static byte[] png(int color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
