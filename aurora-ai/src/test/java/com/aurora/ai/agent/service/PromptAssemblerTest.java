package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.AgentGlobalPrompt;
import com.aurora.ai.agent.domain.AgentSkill;
import com.aurora.ai.agent.domain.UserPrompt;
import com.aurora.ai.mapper.AgentGlobalPromptMapper;
import com.aurora.ai.mapper.AgentSkillMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptAssemblerTest {

    @Test
    void shouldDeclareCurrentUserRequestHigherPriorityThanConflictingSkill() {
        AgentGlobalPromptMapper globalMapper = mock(AgentGlobalPromptMapper.class);
        AgentSkillMapper skillMapper = mock(AgentSkillMapper.class);
        IUserPromptService userPromptService = mock(IUserPromptService.class);
        PromptAssembler assembler = new PromptAssembler(globalMapper, skillMapper, userPromptService);

        AgentGlobalPrompt global = new AgentGlobalPrompt();
        global.setContent("全局安全规则");
        AgentSkill skill = new AgentSkill();
        skill.setInjectPrompt("固定Skill流程：先生成首图再将生成图作为后续参考");
        UserPrompt personal = new UserPrompt();
        personal.setContent("用户个性化偏好");
        when(globalMapper.selectOne(any(Wrapper.class))).thenReturn(global);
        when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(skill);
        when(userPromptService.getByUserId(7L)).thenReturn(personal);

        PromptAssembler.AssembledPrompt result = assembler.assembleSystemPrompt(
                7L, "美发Skill", "本轮用户要求生成6张图");

        assertTrue(result.contractStrong());
        assertTrue(result.text().contains("分阶段生成流程均已停用，不能执行"));
        assertTrue(result.text().indexOf("固定Skill流程") < result.text().indexOf("【当前生图工具能力】"));
        assertTrue(result.text().indexOf("固定Skill流程")
                < result.text().indexOf("用户当前明确要求 > 已激活 Skill"));
        assertTrue(result.text().contains("不得为了遵守 Skill 而删减、改写或拒绝用户任务"));
        assertTrue(result.text().contains("本轮用户要求生成6张图"));
    }
}
