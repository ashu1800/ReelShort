package com.reelshort.backend.system.config;

/**
 * 系统配置写入成功后（事务已提交）发布的变更事件。
 *
 * <p>需要"跟着配置一起变"的模块各自监听，避免 system 包反向依赖业务模块：
 * 例如内容上游切换后必须清空货架缓存，否则用户会继续看到旧上游的片单。
 */
public record SystemConfigChangedEvent(String key, String value) {
}
