package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 本人生成图片的当前下载地址。
 */
@Data
@AllArgsConstructor
public class GenerationDownloadVO {

    private String url;

    private boolean watermarked;
}
