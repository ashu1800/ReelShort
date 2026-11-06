package com.reelshort.backend.content.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台提交的 ReelShort 账号。密码留空表示沿用已保存的密码（表单不回显密码）。
 */
public record ReelshortAccountRequest(@NotBlank @Size(max = 190) String email, @Size(max = 64) String password) {
}
