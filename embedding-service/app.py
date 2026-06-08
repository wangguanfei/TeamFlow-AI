import os
from typing import Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


MODEL_NAME = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
MAX_TEXTS = int(os.getenv("EMBEDDING_BATCH_SIZE", "1"))
MAX_TEXT_CHARS = int(os.getenv("EMBEDDING_MAX_TEXT_CHARS", "4000"))
QUERY_INSTRUCTION = os.getenv(
    "EMBEDDING_QUERY_INSTRUCTION",
    "为这个句子生成表示以用于检索相关文章：",
)

app = FastAPI(title="TeamFlow AI Embedding Service")
model: SentenceTransformer | None = None


class EmbedRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)
    mode: Literal["query", "document"] = "document"
    model: str | None = None


class EmbedResponse(BaseModel):
    model: str
    dim: int
    vectors: list[list[float]]


def get_model() -> SentenceTransformer:
    global model
    if model is None:
        model = SentenceTransformer(MODEL_NAME, device="cpu")
    return model


def normalize_text(text: str, mode: str) -> str:
    compact = " ".join((text or "").split())
    if len(compact) > MAX_TEXT_CHARS:
        compact = compact[:MAX_TEXT_CHARS]
    if mode == "query":
        return QUERY_INSTRUCTION + compact
    return compact


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {"ok": True, "model": MODEL_NAME, "loaded": model is not None}


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    if request.model and request.model != MODEL_NAME:
        raise HTTPException(status_code=400, detail=f"configured model is {MODEL_NAME}")
    texts = [normalize_text(text, request.mode) for text in request.texts if text and text.strip()]
    if not texts:
        raise HTTPException(status_code=400, detail="texts is required")
    if len(texts) > MAX_TEXTS:
        raise HTTPException(status_code=400, detail=f"batch size must be <= {MAX_TEXTS}")
    encoder = get_model()
    vectors = encoder.encode(
        texts,
        normalize_embeddings=True,
        batch_size=1,
        convert_to_numpy=True,
        show_progress_bar=False,
    )
    payload = [[float(value) for value in vector.tolist()] for vector in vectors]
    dim = len(payload[0]) if payload else 0
    return EmbedResponse(model=MODEL_NAME, dim=dim, vectors=payload)
