# 卡密服务管理后台（card-admin-web）

`card-service` 的管理后台前端，Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios。
接口契约见 `../docs/API.md` 第 3 节「管理后台接口」，本工程严格按契约实现，不做字段臆造。

## 命令

```bash
npm install                 # 默认源慢时可加 --registry=https://registry.npmmirror.com
npm run dev                 # 开发服务器，/api 代理到 http://127.0.0.1:8090（changeOrigin）
npm run build               # vue-tsc -b && vite build，产物在 dist/
npm run preview             # 预览 dist
npm run test:contracts      # node --test tests/*.test.mjs（纯逻辑契约测试）
```

## 目录

```
src/api       axios 实例（baseURL /api/admin、withCredentials、写操作 X-Requested-With）与接口函数
src/types     与契约 3.2 节一致的数据对象类型
src/utils     纯函数（.mjs，可被 node --test 直接 import）：标签映射/格式化/CSV/错误包络/设置 diff
src/stores    auth（管理员会话）、settings（系统设置）
src/router    路由与守卫（未登录跳登录、必须改密强制跳改密页）
src/components 布局与公共组件（分页、异步状态、状态标签、打码文本、SVG 趋势图等）
src/views     每个页面一个组件
```

## 约定

- 写操作（POST/PUT/PATCH/DELETE）自动带 `X-Requested-With: card-admin`，缺失会被后端 403 拒绝。
- 失败响应统一解析 `{"error":{"code","message"}}` 为带 `code` 的 `ApiError`；401 清理登录态并跳登录页，
  403 且 `code=password_change_required` 跳改密页。
- 视觉：中性灰底 + 白色内容卡 + 单一强调色（墨绿 #2f6f5e），圆角 6px，间距 4/8/12/16/24/32 阶梯，
  侧边栏深色 #1f2430，表格紧凑（约 40px 行高）。
