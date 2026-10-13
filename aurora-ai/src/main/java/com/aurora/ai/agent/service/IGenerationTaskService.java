package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.dto.GenerationOptionsVO;
import com.aurora.ai.agent.dto.WorkBatchDetailVO;
import com.aurora.ai.agent.dto.WorkItemDetailVO;
import com.aurora.ai.agent.dto.WorkVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 出图异步任务 Service 接口(PRD §5.3 / §六 状态机)。
 * 写入方:generate_images 工具或生成页(创建 CREATED)、GenerationTaskConsumer(流转 RUNNING/SUCCEEDED/FAILED + result_urls);
 * 读取方:轮询接口、历史消息图引用解析。
 */
public interface IGenerationTaskService extends IService<GenerationTask> {

    /**
     * 创建出图任务(初始状态 CREATED),返回已落库实体。
     * taskId、user_id 等由服务端控制传入,不收前端裸实体;张数硬校验在工具层。
     *
     * @param taskId         Redis 队列 taskId(UUID 去横线,唯一)
     * @param userId         所属用户(取自登录态)
     * @param conversationId 来源会话(可空)
     * @param messageId      触发任务的用户消息(可空；独立生成页为空)
     * @param modelId        出图模型 id
     * @param prompt         任务提示词
     * @param n              张数(1~9；生成页入口另限 1~4)
     * @param resolution     分辨率(可空,空走模型默认)
     * @param aspectRatio    画幅比例(可空)
     * @param refImages      图生图参考图 /profile URL JSON 数组串(可空,空=文生图)
     * @return 已落库的 CREATED 任务实体
     */
    GenerationTask createTask(String taskId, Long userId, Long conversationId, Long messageId,
                              Long modelId, String prompt, Integer n, String resolution, String aspectRatio,
                              String refImages, String requestItems);

    /**
     * 提交出图任务:校验张数 → 解析出图模型 → 落库 CREATED → 入队 → 置 QUEUED,返回 task_id。
     * <p>张数硬校验 1≤n≤9(越界抛 {@link com.aurora.common.core.exception.ServiceException});
     * modelId 为空时取一个 image 类启用模型(provider yunwu/gptsapi)。task_id 由服务端生成,
     * 落库后与返回值一致;入队 payload 用自增 id(generationTaskId),消费者据此读库出图。</p>
     * <p>不含预扣豆子;预扣由 {@link #submitImageGeneration} 统一处理。</p>
     *
     * @param userId         所属用户(取自登录态)
     * @param conversationId 来源会话(可空)
     * @param prompt         任务提示词
     * @param n              张数(1~9；生成页入口另限 1~4)
     * @param resolution     分辨率(可空,空走模型默认)
     * @param aspectRatio    画幅比例(可空)
     * @param modelId        出图模型 id(可空,空则取启用的 image 模型)
     * @param refImages      图生图参考图 /profile URL JSON 数组串(可空,空=文生图)
     * @return 已落库并入队任务的 task_id(Redis 队列 taskId 同款格式)
     */
    String submitGenerationTask(Long userId, Long conversationId, String prompt, int n,
                                String resolution, String aspectRatio, Long modelId, String refImages,
                                String requestItems);

    /**
     * 提交出图任务 + 预扣豆子(对话内出图 generate_images 与独立出图端点 /generate 共用)。
     * <p>校验张数(1≤n≤9)与参考图数(图生图 ≤9)→ 解析出图模型(图生图固定云雾)→ 按申请张数预扣校验
     * (余额不足抛 {@link com.aurora.ai.agent.billing.InsufficientBeanException},不提交任务)→ 提交任务 → 预扣豆子。
     * 出图完成后由 {@code GenerationTaskConsumer} 按实际成功张数结算退还。</p>
     *
     * @param userId         所属用户(取自登录态)
     * @param conversationId 来源会话(可空)
     * @param prompt         任务提示词
     * @param n              张数(1~9；生成页入口另限 1~4)
     * @param aspectRatio    画幅比例(可空,空走默认)
     * @param resolution     分辨率档位(可空,空走后台默认)
     * @param refImages      图生图参考图 /profile URL 列表(1~9,空=文生图)
     * @return 已落库并入队任务的 task_id
     */
    String submitImageGeneration(Long userId, Long conversationId, String prompt, int n,
                                 String resolution, String aspectRatio, List<String> refImages);

    /**
     * 提交一次包含不同提示词的批量生图任务。
     * 每项引用的图片编号在提交前解析为对应 URL，整批共用一个 taskId 和结算单元。
     * sourceMessageId 用于小程序断线后按本轮用户消息恢复真实任务。
     */
    String submitImageBatch(Long userId, Long conversationId, Long sourceMessageId,
                            List<GenerateImageItem> items,
                            String resolution, String aspectRatio, List<String> allRefImages);

    /**
     * 查询本人某条用户消息触发的出图任务，供对话断线后恢复真实占位与结果。
     */
    List<GenerationTask> listBySourceMessage(Long userId, Long conversationId, Long sourceMessageId);

    /** 查询当前默认图像模型允许的分辨率选项。 */
    GenerationOptionsVO getGenerationOptions();

    /** 查询本人任务中的单张作品详情，包含该张实际使用的提示词和参考图。 */
    WorkItemDetailVO getWorkItemDetail(Long userId, String taskId, int seq);

    /** 查询本人作品批次详情，失败槽位也按原计划序号返回。 */
    WorkBatchDetailVO getWorkBatchDetail(Long userId, String taskId);

    /**
     * 精确重放本人任务中的单张图片。
     *
     * @param failedOnly true 时仅允许原结果为空的失败项；false 时成功作品也可重新生成
     */
    String replayWorkItem(Long userId, String taskId, int seq, boolean failedOnly);

    /**
     * 按 taskId 更新任务状态与结果。
     * resultUrls 用 ObjectMapper 序列化为 JSON 串存 result_urls 列;入参为 null 的字段不覆盖。
     *
     * @param taskId       Redis 队列 taskId
     * @param status       目标状态(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
     * @param resultUrls   成功 URL 列表(为 null 不更新该列)
     * @param successCount 成功张数(为 null 不更新该列)
     * @param errorMsg     失败原因脱敏(为 null 不更新该列)
     */
    void markStatus(String taskId, String status, List<String> resultUrls, Integer successCount, String errorMsg);

    /**
     * 按 taskId 回写实扣豆子(出图结算)。
     * 与 markStatus 分离,避免改动既有签名影响其他调用方;结算时由消费者单独写 bean_cost。
     *
     * @param taskId   Redis 队列 taskId
     * @param beanCost 本次出图实扣豆子(ceil(成功张数 × 每张单价);零成功为 0)
     */
    void setBeanCost(String taskId, long beanCost);

    /**
     * 按 taskId 查询任务(不校验归属)。
     *
     * @param taskId Redis 队列 taskId
     * @return 任务实体,不存在返回 null
     */
    GenerationTask getByTaskId(String taskId);

    /**
     * 批量读取任务，供历史消息一次性解析图片地址。
     *
     * @param taskIds 任务编号集合
     * @return taskId 到任务的映射；不存在的任务不返回
     */
    Map<String, GenerationTask> mapByTaskIds(Collection<String> taskIds);

    /**
     * 按 taskId 查询且校验归属本人(防越权)。
     *
     * @param taskId Redis 队列 taskId
     * @param userId 当前登录用户(取自登录态)
     * @return 属本人的任务实体;不存在或非本人返回 null
     */
    GenerationTask getOwned(String taskId, Long userId);

    /**
     * 本人「我的作品」分页:仅 SUCCEEDED 出图任务,按创建时间倒序(C 端作品墙)。
     *
     * @param userId   当前登录用户(取自登录态)
     * @param pageNum  页码(从 1 起)
     * @param pageSize 每页条数
     * @param sourceType 来源过滤(CREATE_PAGE / AGENT,不传返回全部)
     * @return 成功出图任务批次摘要分页
     */
    IPage<WorkVO> pageMyWorks(Long userId, Integer pageNum, Integer pageSize, String sourceType);
}
