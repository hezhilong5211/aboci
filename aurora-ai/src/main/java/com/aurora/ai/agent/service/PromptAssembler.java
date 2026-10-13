package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.AgentGlobalPrompt;
import com.aurora.ai.agent.domain.AgentSkill;
import com.aurora.ai.agent.domain.UserPrompt;
import com.aurora.ai.mapper.AgentGlobalPromptMapper;
import com.aurora.ai.mapper.AgentSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 三层提示词组装器(PRD §七)。
 * 按固定顺序拼接 system message:① 全局提示词 ② Skill 提示词 ③ 个性化提示词 ④ 产出契约指令 ⑤ 运行时上下文。
 * 任一层为空则跳过,不报错(PRD 2.6.4「有什么用什么」);各层读出即原文,写侧已做 htmlUnescape,此处不再反转义。
 * 全部为只读组装,不做任何写操作。
 */
@Component
public class PromptAssembler {

    /** 各层之间的分隔符 */
    private static final String SECTION_SEPARATOR = "\n\n";

    /**
     * 命中带产出契约的 Skill 时注入的强产出契约指令(PRD §九-末尾结构化 / §7-④,2026-07-02 工具化)。
     * 结构化产出主通道 = submit_result 工具;正文瘦身:完整内容只进工具参数,正文只留一句引导,禁止双份。
     */
    private static final String RESULT_CONTRACT_INSTRUCTION =
            "【系统硬性要求,优先级高于其他风格指令】完成创作后,必须调用 submit_result 工具提交结构化产出:"
                    + "title(标题)、body(正文全文)、tags(标签数组,不带#号),无内容的字段省略。"
                    + "调用之后,最终回复的正文只写一句简短引导(如「已为你生成,见下方卡片」),"
                    + "禁止在正文重复标题/正文/标签内容,也不要以任何代码块形式输出产出。"
                    + "无论中间调用了多少次其他工具,提交产出这一步不可省略。";

    /**
     * 未命中 Skill 时注入的条件式结构化条款(2026-07-02 通用化+工具化):
     * 仅当用户在输入里明确要求分段/可分别复制的产出时,模型才调用 submit_result 提交;普通对话不受影响。
     */
    private static final String RESULT_CONTRACT_CONDITIONAL =
            "当用户明确要求「可分别复制/分段」的结构化产出(如标题、正文、标签分开给)时,"
                    + "完成创作后调用 submit_result 工具提交(title/body/tags),"
                    + "然后正文只写一句简短引导,不要重复产出内容,也不要以代码块形式输出产出;"
                    + "用户未提出此类要求时,不要调用 submit_result。";

    /** Skill 是默认工作流，不得覆盖用户本轮已经说清楚的具体要求。 */
    private static final String INSTRUCTION_PRIORITY =
            "【指令优先级】安全与工具协议 > 用户本轮最新澄清 > 用户当前明确要求 > 已激活 Skill > 个性化与模型默认值。"
                    + "Skill 只补充用户没有说明的内容；若 Skill 与用户明确的图片角色、数量、步骤、文案或参数冲突，"
                    + "必须以用户当前要求为准，不得为了遵守 Skill 而删减、改写或拒绝用户任务。"
                    + "【当前生图工具能力】按用户要求生成单张或多张独立图片，只能使用本次提交前已确定的素材。"
                    + "全局提示词、Skill或历史对话中的首图锚点、自动引用中途成品、分阶段生成流程均已停用，不能执行。"
                    + "用户主动添加的历史作品可作为本次素材；未选择的历史图片和本任务生成结果不能自动加入。";

    private final AgentGlobalPromptMapper globalPromptMapper;
    private final AgentSkillMapper skillMapper;
    private final IUserPromptService userPromptService;

    public PromptAssembler(AgentGlobalPromptMapper globalPromptMapper,
                           AgentSkillMapper skillMapper,
                           IUserPromptService userPromptService) {
        this.globalPromptMapper = globalPromptMapper;
        this.skillMapper = skillMapper;
        this.userPromptService = userPromptService;
    }

    /**
     * 组装结果:system 提示词全文 + 是否注入了强产出契约(命中 Skill)。
     * contractStrong 供编排层决定是否在工具回灌后追加防遗忘提醒。
     */
    public record AssembledPrompt(String text, boolean contractStrong) {}

    /**
     * 组装 system 提示词。
     *
     * @param userId         当前登录用户(取自登录态),用于读个性化提示词
     * @param skillName      @ 激活的 Skill 名称;为空或未命中则不注入 ②,④ 退化为条件式条款
     * @param runtimeContext 运行时上下文(历史摘要 + 用户上传图 URL 清单等);为空则跳过
     * @return 拼接后的 system 提示词与强契约标记
     */
    public AssembledPrompt assembleSystemPrompt(Long userId, String skillName, String runtimeContext) {
        StringBuilder sb = new StringBuilder();

        // ① 全局提示词:is_active='1' 一条(del_flag='0' 由 @TableLogic 自动追加)
        String globalPrompt = loadGlobalPrompt();
        appendSection(sb, globalPrompt);

        // ② Skill 提示词:skillName 非空且命中启用 Skill 时注入
        String skillPrompt = loadSkillPrompt(skillName);
        appendSection(sb, skillPrompt);

        // ③ 个性化提示词:user_prompt.content
        String userPrompt = loadUserPrompt(userId);
        appendSection(sb, userPrompt);

        // 用户主动输入必须高于模板默认值，避免固定 Skill 覆盖本轮真实任务。
        appendSection(sb, INSTRUCTION_PRIORITY);

        // ④ 产出契约:命中 Skill 注入强契约(必须输出块);未命中注入条件式条款(用户明确要求分段可复制时才输出)
        boolean contractStrong = StringUtils.hasText(skillPrompt);
        appendSection(sb, contractStrong ? RESULT_CONTRACT_INSTRUCTION : RESULT_CONTRACT_CONDITIONAL);

        // ⑤ 运行时上下文
        appendSection(sb, runtimeContext);

        return new AssembledPrompt(sb.toString(), contractStrong);
    }

    /**
     * 取生效的全局提示词内容(is_active='1');无则返回 null。
     * 全局唯一生效由后台写侧事务保证,此处 LIMIT 1 防御性取一条。
     */
    private String loadGlobalPrompt() {
        AgentGlobalPrompt globalPrompt = globalPromptMapper.selectOne(
                new LambdaQueryWrapper<AgentGlobalPrompt>()
                        .eq(AgentGlobalPrompt::getIsActive, "1")
                        .last("LIMIT 1"));
        return globalPrompt == null ? null : globalPrompt.getContent();
    }

    /**
     * 按 @名命中启用 Skill 的注入提示词;skillName 为空或未命中则返回 null。
     * 重名歧义:status='0'(del_flag='0' 由 @TableLogic 自动追加)过滤后 ORDER BY sort ASC, skill_id DESC LIMIT 1。
     * 用列名 QueryWrapper(只读实体 AgentSkill 未映射 sort 字段),sort 为 MySQL 保留字用反引号。
     */
    private String loadSkillPrompt(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return null;
        }
        AgentSkill skill = skillMapper.selectOne(
                new QueryWrapper<AgentSkill>()
                        .eq("name", skillName)
                        .eq("status", "0")
                        .last("ORDER BY `sort` ASC, skill_id DESC LIMIT 1"));
        return skill == null ? null : skill.getInjectPrompt();
    }

    /**
     * 取本人个性化提示词内容;无则返回 null。
     */
    private String loadUserPrompt(Long userId) {
        if (userId == null) {
            return null;
        }
        UserPrompt userPrompt = userPromptService.getByUserId(userId);
        return userPrompt == null ? null : userPrompt.getContent();
    }

    /**
     * 追加一层提示词:内容有实际文本才拼接,空层跳过;非首层前置分隔符。
     */
    private void appendSection(StringBuilder sb, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(SECTION_SEPARATOR);
        }
        sb.append(content);
    }
}
