"""Машин сургалтын оноо өгөх сервис.

Одоогоор зөвхөн төлөвийн шалгалтын endpoint. POST /score болон POST /reload-ийг машин сургалтын
шатанд нэмнэ (зохиомжийн 3.4 хэсэг, "Машин сургалтын сервисийн API" хүснэгт).
"""
from fastapi import FastAPI

app = FastAPI(title="ML scoring service", version="0.1.0")

# Загвар хараахан ачаалаагүй; сервер дүрэмд суурилсан эрэмбэлэлтийг ашиглана (МГШ06).
MODEL_VERSION: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "modelVersion": MODEL_VERSION}
