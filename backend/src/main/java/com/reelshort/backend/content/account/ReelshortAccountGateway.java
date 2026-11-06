package com.reelshort.backend.content.account;

import java.util.Optional;

/**
 * 内容源账号通道：把后台维护的 ReelShort 账号同步给 content-provider，并读回实时状态。
 *
 * <p>单独抽成一个接口而不是塞进 {@code ContentProvider}，是因为它属于"运营配置"而不是
 * "内容读取"：单元测试里的内容源假实现不需要为了编译器实现它，后台服务用
 * {@code ObjectProvider} 软依赖，取不到就只保存不同步。
 */
public interface ReelshortAccountGateway {

	/** 下发账号并返回同步结果；实现方负责真正去上游登录一次。 */
	ReelshortAccountPushResult updateAccount(String email, String password);

	/** 内容源当前的登录态与会员状态；内容源不可达时返回空。 */
	Optional<LiveStatus> liveStatus();

	record ReelshortAccountPushResult(boolean authenticated, String emailMasked) {
	}

	/**
	 * 内容源报回来的实时状态。
	 *
	 * <p>{@code vipKnown=false} 表示内容源这次没问出会员信息（未登录或上游不可用），
	 * 此时几个会员字段都可能是 null——后台要显示"未知"而不是"非会员"。
	 */
	record LiveStatus(boolean authenticated, String emailMasked, boolean vipKnown, boolean vipActive, String vipExpireAt,
			boolean vipRenew, Integer coins) {
	}
}
