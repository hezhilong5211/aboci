package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.mapper.MessageAttachmentMapper;
import com.aurora.ai.agent.service.impl.MessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class MessageServiceImplTest {

    private MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);
        service = spy(new MessageServiceImpl(mock(MessageAttachmentMapper.class)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReadLatestMessagesBeforeCursorAndReturnAscending() {
        Page<Message> page = new Page<>(1, 3, false);
        page.setRecords(List.of(message(9L), message(8L), message(7L)));
        doReturn(page).when(service).page(any(Page.class), any(LambdaQueryWrapper.class));

        List<Message> result = service.listBefore(33L, 10L, 3);

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).page(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("conversation_id", "id", "<", "DESC");
        assertThat(result).extracting(Message::getId).containsExactly(7L, 8L, 9L);
    }

    private Message message(Long id) {
        Message message = new Message();
        message.setId(id);
        return message;
    }
}
