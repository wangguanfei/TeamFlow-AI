FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    OMP_NUM_THREADS=1 \
    OPENBLAS_NUM_THREADS=1 \
    MKL_NUM_THREADS=1 \
    TOKENIZERS_PARALLELISM=false

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --timeout 300 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    filelock typing-extensions sympy networkx jinja2 fsspec mpmath MarkupSafe
RUN pip install --no-cache-dir --timeout 600 --retries 5 --no-deps \
    --index-url https://mirror.sjtu.edu.cn/pytorch-wheels/cpu \
    "torch==2.7.1+cpu"
RUN pip install --no-cache-dir --timeout 300 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    -r requirements.txt

COPY app.py .

# 构建时通过 hf-mirror.com 下载模型，烧入镜像层，运行时无需联网
ARG EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
ENV HF_ENDPOINT=https://hf-mirror.com \
    HF_HOME=/app/models \
    TRANSFORMERS_CACHE=/app/models
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${EMBEDDING_MODEL}', device='cpu')"

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
