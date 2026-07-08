# ShortLink App 品牌与图标设计

## 目标
将 Android App 的用户可见品牌从 `ReelShort` 改为 `ShortLink`，并补齐一套专属 launcher icon，使启动器、认证页和账户页呈现统一品牌。

## 设计结论
- 品牌名：`ShortLink`。
- 图标方向：竖屏短剧播放卡作为第一识别点，居中大号播放三角明确表达视频 App 属性，右下角链环作为 `ShortLink` 的品牌记忆点。
- 色彩：延续现有暗色影院感与金色强调，使用深墨背景、金色主体、少量暖色高光。
- 形式：Android adaptive icon，背景和前景均用项目内 vector drawable 实现，避免引入外部版权资产。

## 图标结构
- 背景：深色影院暗场，叠加低调金色光晕和中心聚焦暗面。
- 前景：9:16 竖屏视频卡片、中心播放三角、底部进度条和右下角链环徽标。
- 小尺寸识别：优先保留视频卡轮廓与播放三角，不依赖文字；链环只做辅助品牌记忆，不抢短视频识别。

## 范围
- 修改 Android `app_name` 为 `ShortLink`。
- 接入 `android:icon` 与 `android:roundIcon`。
- 更新 App 内用户可见品牌文案中的 `ReelShort`。
- 更新 `AGENTS.md` 变更历史。

## 非目标
- 不改 Android package/applicationId。
- 不改后端、域名、API 名称或数据库命名。
- 不引入第三方图片素材。
