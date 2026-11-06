package com.reelshort.backend.content.account;

/**
 * 保存账号后的同步结果：保存一定成功（已落库），这里说明内容源那边有没有登录上。
 *
 * @param authenticated 内容源是否用这套凭据登录成功
 * @param emailMasked   脱敏邮箱，用于确认下发的是哪个账号
 * @param message       给后台看的一句话结论
 */
public record ReelshortAccountSync(boolean authenticated, String emailMasked, String message) {
}
