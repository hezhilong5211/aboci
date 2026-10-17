package com.aurora.ai.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 批量生图中的单张任务项。
 * 图片编号从 1 开始，严格对应用户上传图片的当前数组顺序。
 */
@Data
public class GenerateImageItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结果顺序，从 1 开始 */
    private Integer seq;

    /** 按本张属性职责编译的执行提示词 */
    private String prompt;

    /** null/2/3 为历史快照；4 为职责表；5 编译附件标记；6 增加非职责属性隔离。 */
    private Integer contractVersion;

    /** 原始要求与历次澄清原文，仅供追溯，不混入其他输出的执行提示词。 */
    private String sourcePrompt;

    /** 本次规划采用的有效需求，保留与原文对照。 */
    private String effectiveRequest;

    private String requestUpdate;

    /** 编译前的本张语义目标。 */
    private String plannedPrompt;

    /** 需要逐字出现在图片中的文字，和参考坐标分开处理。 */
    private List<String> visibleTexts;

    /** 本次编号空间中的全部稳定附件ID，顺序与任务refImages一致。 */
    private List<String> referenceAttachmentIds;

    private List<ImageReferenceBinding> referenceBindings;

    /** 本张图使用的参考图片编号 */
    private List<Integer> referenceImageIndexes;

    /** 仅识别旧快照以拒绝重放，不保存、序列化或执行生成图依赖。 */
    @com.fasterxml.jackson.annotation.JsonSetter("generatedReferenceSeqs")
    public void setGeneratedReferenceSeqs(List<Integer> legacyReferences) {
        if (legacyReferences != null && !legacyReferences.isEmpty()) {
            throw new com.aurora.common.core.exception.ServiceException(
                    "当前不支持任务内生成图引用，请选择参考素材后重新提交");
        }
    }
}
