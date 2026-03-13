"""
ChromaDB 長期記憶模組

雷霸的「靈魂記憶」— 用向量相似度搜索歷史交互，
讓雷霸記得用戶的習慣和過去的對話。
"""

import uuid
import asyncio
from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional
import chromadb
from chromadb.utils import embedding_functions


@dataclass
class MemoryEntry:
    content: str
    metadata: dict = field(default_factory=dict)
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: str = field(default_factory=lambda: datetime.now().isoformat())


class ChromaMemoryStore:
    """
    ChromaDB 向量記憶庫

    使用 sentence-transformers 的多語言嵌入模型，
    支持中文/廣東話語義搜索。
    """

    def __init__(self, persist_dir: str = "./chroma_db", collection_name: str = "thunder_memory"):
        self.persist_dir = persist_dir
        self.collection_name = collection_name
        self.client = None
        self.collection = None
        self._embed_fn = None

    async def initialize(self):
        """初始化 ChromaDB 客戶端和集合"""
        # 在後台線程初始化（避免阻塞事件循環）
        await asyncio.get_event_loop().run_in_executor(None, self._init_sync)

    def _init_sync(self):
        """同步初始化（在執行器中調用）"""
        # 使用多語言嵌入模型（支持廣東話/中文）
        self._embed_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
            model_name="paraphrase-multilingual-MiniLM-L12-v2"
        )

        # 創建持久化 ChromaDB 客戶端
        self.client = chromadb.PersistentClient(path=self.persist_dir)

        # 獲取或創建記憶集合
        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            embedding_function=self._embed_fn,
            metadata={"hnsw:space": "cosine"}  # 餘弦相似度
        )

    async def add(self, entry: MemoryEntry) -> str:
        """添加一條記憶"""
        def _add():
            full_metadata = {
                **entry.metadata,
                "timestamp": entry.timestamp
            }
            self.collection.add(
                ids=[entry.id],
                documents=[entry.content],
                metadatas=[full_metadata]
            )
            return entry.id

        return await asyncio.get_event_loop().run_in_executor(None, _add)

    async def search(self, query: str, n_results: int = 5) -> list[dict]:
        """語義搜索相閘記憶"""
        def _search():
            if self.collection.count() == 0:
                return []

            results = self.collection.query(
                query_texts=[query],
                n_results=min(n_results, self.collection.count()),
                include=["documents", "metadatas", "distances"]
            )

            memories = []
            for i, doc in enumerate(results["documents"][0]):
                similarity = 1 - results["distances"][0][i]  # 轉換為相似度
                if similarity > 0.3:  # 只返回相閘度 > 30% 的記憶
                    memories.append({
                        "content": doc,
                        "metadata": results["metadatas"][0][i],
                        "similarity": round(similarity, 3)
                    })
            return memories

        return await asyncio.get_event_loop().run_in_executor(None, _search)

    async def clear(self):
        """清空所有記憶"""
        def _clear():
            self.client.delete_collection(self.collection_name)
            self.collection = self.client.get_or_create_collection(
                name=self.collection_name,
                embedding_function=self._embed_fn
            )

        await asyncio.get_event_loop().run_in_executor(None, _clear)

    @property
    def count(self) -> int:
        """記憶總數"""
        return self.collection.count() if self.collection else 0
