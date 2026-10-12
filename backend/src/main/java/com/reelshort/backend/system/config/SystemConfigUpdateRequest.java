package com.reelshort.backend.system.config;

import jakarta.validation.constraints.NotNull;

/**
 * P2-17: 去掉 @NotBlank，把"能否为空"的判断收敛到 {@link SystemConfigDefinition#validate(String)}，
 * 因为多数配置项本身不接受空值（仍返回 400 bad request），而 vip.collection-address 需要支持清空。
 */
public record SystemConfigUpdateRequest(@NotNull String value) {
}
