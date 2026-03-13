"""
Project Cyber-Navi - Phase 2 Home Server
FastAPI + ChromaDB + LLM backend for Thunder AI Navigator
"""

import os
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from rich import print as rprint

from api.routes import router
from memory.chroma_store import ChromaMemoryStore

load_dotenv()

memory_store = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global memory_store
    rprint("[bold cyan]⚡ 雷霆 AI 後端正在啟動...[/bold cyan]")
    memory_store = ChromaMemoryStore(
        persist_dir=os.getenv("CHROMA_PERSIST_DIR", "./chroma_db"),
        collection_name=os.getenv("CHROMA_COLLECTION", "thunder_memory")
    )
    await memory_store.initialize()
    rprint("[green]✅ ChromaDB 記憶庫已就緒[/green]")
    app.state.memory = memory_store
    rprint(f"[bold green]🚀 雷霆後端啟動成功！Port {os.getenv('SERVER_PORT', 8765)}[/bold green]")
    yield
    rprint("[yellow]⚡ 雷霆後端正在關閉...[/yellow]")


app = FastAPI(
    title="CyberNavi Thunder API",
    description="雷霆 AI 領航員後端 — 長期記憶 + 廣東話 LLM 推理",
    version="0.2.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    return {
        "status": "online",
        "service": "CyberNavi Thunder",
        "version": "0.2.0",
        "memory": "connected" if app.state.memory else "disconnected"
    }


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=os.getenv("SERVER_HOST", "0.0.0.0"),
        port=int(os.getenv("SERVER_PORT", 8765)),
        reload=True,
        log_level="info"
    )
