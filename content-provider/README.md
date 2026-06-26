# ReelShort Content Provider

Flask 内容源适配服务。当前阶段只提供健康检查，后续接入第三方 ReelShort API。

## 本地运行

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python app.py
```

健康检查：

```http
GET http://localhost:5000/health
```

