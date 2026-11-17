"""Machine learning scoring service.

Skeleton version: only the health endpoint. POST /score and POST /reload are
added in the ML milestone (design section 3.4, table "Машин сургалтын сервисийн API").
"""
from fastapi import FastAPI

app = FastAPI(title="ML scoring service", version="0.1.0")

# No model is loaded yet; the backend falls back to rule-based ranking (МГШ06).
MODEL_VERSION: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "modelVersion": MODEL_VERSION}
