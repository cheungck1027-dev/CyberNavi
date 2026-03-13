"""
CyberNavi Thunder API 路由

端點：
  POST /api/v1/translate   — 截獲文字 → 廣東話翻譯（主要端點）
  POST /api/v1/memory/add  — 添加記憶
  GET  /api/v1/memory/search — 搜索相閘記憶
  GET  /api/v1/persona/status — 雷霸人格狀態
"""

from fastapi import APIRouter, Request, HTTPException
from pydantic import BaseModel
from typing import Optional
import os

from persona.thunder_llm import ThunderLLM
from memory.chroma_store import MemoryEntry

router = APIRouter()
llm = ThunderLLM()


# ════════════════════════════════════════════
# 資料模型
# ════════════════════════════════════════════

class TranslateRequest(BaseModel):
    text: str
    source: str = "unknown"     # galaxy_ai | translation | notification | unknown
    context_id: Optional[str] = None

class TranslateResponse(BaseModel):
    thunder_response: str
    emotion: str
    memories_used: int

class MemoryAddRequest(BaseModel):
    content: str
    metadata: Optional[dict] = {}

class MemorySearchRequest(BaseModel):
    query: str
    n_results: int = 5


# ════════════════════════════════════════════
# 主要翻譯端點
# ════════════════════════════════════════════

@router.post("/translate", response_model=TranslateResponse)
async def translate_text(req: TranslateRequest, request: Request):
    """
    截獲文字 → 雷霸廣東話翻譯（核心端點）

    Android App 將截獲的文字 POST 到此端點，
    後端用 LLM 生成雷霸風格廣東話回應。
    """
    memory_store = request.app.state.memory

    # 1. 搜索相閘記憶，提供上下文
    relevant_memories = []
    if memory_store:
        relevant_memories = await memory_store.search(req.text, n_results=3)

    # 2. 調用 LLM 生成雷霸回應
    result = await llm.translate(
        text=req.text,
        source=req.source,
        memories=relevant_memories
    )

    # 3. 將本次交互保存到記憶
    if memory_store and len(req.text) > 20:
        await memory_store.add(MemoryEntry(
            content=f"[{req.source}] {req.text[:200]}",
            metadata={
                "source": req.source,
                "thunder_response": result["response"][:100],
                "emotion": result["emotion"]
            }
        ))

    return TranslateResponse(
        thunder_response=result["response"],
        emotion=result["emotion"],
        memories_used=len(relevant_memories)
    )


# ════════════════════════════════════════════
# 記憶管理端點
# ════════════════════════════════════════════

@router.post("/memory/add")
async def add_memory(req: MemoryAddRequest, request: Request):
    """手動添加一條記憶"""
    memory_store = request.app.state.memory
    if not memory_store:
        raise HTTPException(status_code=503, detail="記憶庫未初始化")

    entry_id = await memory_store.add(MemoryEntry(
        content=req.content,
        metadata=req.metadata
    ))
    return {"status": "added", "id": entry_id}


@router.get("/memory/search")
async def search_memory(q: str, n: int = 5, request: Request = None):
    """語義搜索記憶"""
    memory_store = request.app.state.memory
    if not memory_store:
        raise HTTPException(status_code=503, detail="記憶庫未初始化")

    results = await memory_store.search(q, n_results=n)
    return {"results": results, "count": len(results)}


@router.delete("/memory/clear")
async def clear_memory(request: Request):
    """清空所有記憶（慎用！）"""
    memory_store = request.app.state.memory
    if not memory_store:
        raise HTTPException(status_code=503, detail="記憶庫未初始化")

    await memory_store.clear()
    return {"status": "cleared"}


# ════════════════════════════════════════════
# 人格狀態端點
# ════════════════════════════════════════════

@router.get("/persona/status")
async def persona_status():
    """查詢雷霸人格狀態"""
    return {
        "name": "雷霸 (Thunder)",
        "version": "Phase 2",
        "llm_provider": os.getenv("LLM_PROVIDER", "anthropic"),
        "llm_model": os.getenv("LLM_MODEL", "claude-3-5-sonnet-20241022"),
        "persona": "廣東話 AI 領航員 — 霸氣、精準、忠誠"
    }
