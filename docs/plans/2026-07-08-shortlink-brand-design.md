# ShortLink App 品牌与图标设计

## 目标
将 Android App 的用户可见品牌从 `ReelShort` 改为 `ShortLink`，并补齐一套专属 launcher icon，使启动器、认证页和账户页呈现统一品牌。

## 设计结论
- 品牌名：`ShortLink`。
- 图标方向：抽象 `S` 形播放链路，表达短剧播放与内容源连接。
- 色彩：延续现有暗色影院感与金色强调，使用深墨背景、金色主体、少量暖色高光。
- 形式：Android adaptive icon，背景和前景均用项目内 vector drawable 实现，避免引入外部版权资产。

## 图标结构
- 背景：深色圆角视觉底，叠加低调金色径向光晕。
- 前景：两个链路节点和一条 `S` 形连接带，连接带中嵌入播放三角形。
- 小尺寸识别：保留粗笔画和高对比，不依赖文字。

## 范围
- 修改 Android `app_name` 为 `ShortLink`。
- 接入 `android:icon` 与 `android:roundIcon`。
- 更新 App 内用户可见品牌文案中的 `ReelShort`。
- 更新 `AGENTS.md` 变更历史。

## 非目标
- 不改 Android package/applicationId。
- 不改后端、域名、API 名称或数据库命名。
- 不引入第三方图片素材。
