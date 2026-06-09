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
RUN pip install --no-cache-dir --timeout 600 --retries 5 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    --extra-index-url https://mirror.sjtu.edu.cn/pytorch-wheels/cpu \
    "torch==2.7.1+cpu"
RUN pip install --no-cache-dir --timeout 300 \
    -i https://mirrors.aliyun.com/pypi/simple/ \
    -r requirements.txt

COPY app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
