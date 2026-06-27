FROM python:3.13-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 5000
ENV CONTENT_PROVIDER_HOST=0.0.0.0
ENV CONTENT_PROVIDER_PORT=5000
CMD ["python", "app.py"]
