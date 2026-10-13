package com.aurora.framework.config;

import com.aurora.framework.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 *
 * <p>对标注 {@link com.baomidou.mybatisplus.annotation.TableField} 且
 * fill 为 INSERT / INSERT_UPDATE 的字段在新增 / 更新时自动回填：
 * <ul>
 *   <li>createTime / updateTime：当前服务器时间</li>
 *   <li>createBy  / updateBy ：当前登录用户账号，未登录时降级为 "system"</li>
 * </ul>
 *
 * <p>使用 strictInsertFill / strictUpdateFill，仅在原值为 null 时才填充，
 * 不会覆盖调用方显式设置过的值，对既有代码无副作用。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(MybatisMetaObjectHandler.class);

    /** 无登录上下文时的兜底账号 */
    private static final String DEFAULT_USER = "system";

    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_CREATE_BY = "createBy";
    private static final String FIELD_UPDATE_BY = "updateBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String username = currentUsername();

        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_CREATE_BY, String.class, username);
        this.strictInsertFill(metaObject, FIELD_UPDATE_BY, String.class, username);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, FIELD_UPDATE_BY, String.class, currentUsername());
    }

    /**
     * 获取当前登录用户名，异常 / 无登录上下文场景统一降级为 system，
     * 防止定时任务 / 初始化流程因 Security 上下文缺失而中断
     */
    private String currentUsername() {
        try {
            String username = SecurityUtils.getUsername();
            return (username == null || username.isEmpty()) ? DEFAULT_USER : username;
        } catch (Exception e) {
            log.debug("获取登录用户失败，使用默认账号 system 进行填充: {}", e.getMessage());
            return DEFAULT_USER;
        }
    }
}
