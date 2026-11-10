"""内容源上游适配包。

每个模块暴露一个与 `app.ReelShortClient` 同形（search / shelf / episodes / video）
的客户端类，由 `app.create_app()` 注册并按请求头 `X-Content-Upstream` 选择。
"""
