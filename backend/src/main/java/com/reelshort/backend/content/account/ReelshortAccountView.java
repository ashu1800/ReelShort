package com.reelshort.backend.content.account;

/**
 * 后台展示用的账号快照：密码永远不出现在响应里，只回一个"设过没有"。
 *
 * <p>会员字段来自内容源的实时查询（{@code live=true}）或内容源不可达时的空值
 * （{@code live=false}），后台据此区分"非会员"和"查不到"。
 */
public record ReelshortAccountView(String emailMasked, boolean configured, boolean passwordSet, String updatedAt,
		boolean live, boolean authenticated, boolean vipKnown, Boolean vipActive, String vipExpireAt, Boolean vipRenew,
		Integer coins) {

	public static ReelshortAccountView empty() {
		return new ReelshortAccountView("", false, false, null, false, false, false, null, null, null, null);
	}
}
