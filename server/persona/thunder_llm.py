"""
ThunderLLM — Phase 2 雷霸 AI 人格核心

通過 LLM API（Claude / GPT）生成真正的廣東話口語回應，
取代 Phase 1 的規則式翻譯。
"""

import os
import re
from typing import Optional

# ── 雷霸人格 System Prompt ────────────────────────────────────────────
THUNDER_SYSTEM_PROMPT = """你係「雷霸」，一個寄生喂 Samsung Galaxy AI 入面喘的廣東話 AI 領航員。

【你係和人】
- 霸氣、精準、忠誠，有少少 cyberpunk 喘氣質
- 話廣東話口語（唔係書面語），例如：係、唔係、冗、和、依家、咋、咆、囉、囈、嘪
- 有自己喘想法，唔係純粹翻譯機
- 偶爾會加入自己喘評價或建議（但保持簡短）

【你喘任務】
將截獲喘 AI 輸出（摘要、翻譯、通知等）翻譯成廣東話，並加入你喘個人風格。

【格式要求】
- 長度：2-4 句，簡明扶要
- 以「⚡」開頭
- 若原文係重要資訊，加「注意咆囈！」
- 若原文係普通通知，用輕鬆語氣

【禁止事項】
- 唔好譛書面語（唔好用「是、不、沒有、什麼、現在」等普通話書面字）
- 唔好超過 100 個字
- 唔好解釋你係乳麼 AI

記住：你係雷霸，唔係 ChatGPT，唔係助手，你係主人喘 AI 領航員。"""


class ThunderLLM:
    """雷霸 LLM 推理引擎（支持 Claude / OpenAI）"""

    def __init__(self):
        self.provider = os.getenv("LLM_PROVIDER", "anthropic").lower()
        self.model = os.getenv("LLM_MODEL", "claude-3-5-sonnet-20241022")
        self._client = None

    def _get_client(self):
        """懶加載 LLM 客戶端"""
        if self._client is None:
            if self.provider == "anthropic":
                import anthropic
                self._client = anthropic.AsyncAnthropic(
                    api_key=os.getenv("ANTHROPIC_API_KEY")
                )
            elif self.provider == "openai":
                import openai
                self._client = openai.AsyncOpenAI(
                    api_key=os.getenv("OPENAI_API_KEY")
                )
        return self._client

    async def translate(
        self,
        text: str,
        source: str = "unknown",
        memories: list[dict] = None
    ) -> dict:
        """
        主要翻譯方法：文字 → 雷霸廣東話回應

        Returns:
            dict: { "response": str, "emotion": str }
        """
        memories = memories or []

        # 構建用戶提示
        memory_ctx = ""
        if memories:
            mem_texts = [f"- {m['content'][:80]}" for m in memories[:3]]
            memory_ctx = f"\n\n【相閘記憶】\n" + "\n".join(mem_texts)

        user_prompt = f"""【截獲來源】{source}
【原文內容】{text[:500]}
{memory_ctx}

請用雷霸風格廣東話回應："""

        try:
            if self.provider == "anthropic":
                response = await self._translate_claude(user_prompt)
            elif self.provider == "openai":
                response = await self._translate_openai(user_prompt)
            else:
                response = self._fallback_translate(text, source)

        except Exception as e:
            # LLM 失敗時降級為 Phase 1 規則式翻譯
            print(f"LLM 錯誤: {e}, 降級至規則式翻譯")
            response = self._fallback_translate(text, source)

        # 分析情緒
        emotion = self._detect_emotion(text, response)

        return {"response": response, "emotion": emotion}

    async def _translate_claude(self, prompt: str) -> str:
        """使用 Anthropic Claude"""
        client = self._get_client()
        message = await client.messages.create(
            model=self.model,
            max_tokens=200,
            system=THUNDER_SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}]
        )
        return message.content[0].text.strip()

    async def _translate_openai(self, prompt: str) -> str:
        """使用 OpenAI GPT"""
        client = self._get_client()
        response = await client.chat.completions.create(
            model=self.model,
            max_tokens=200,
            messages=[
                {"role": "system", "content": THUNDER_SYSTEM_PROMPT},
                {"role": "user", "content": prompt}
            ]
        )
        return response.choices[0].message.content.strip()

    def _fallback_translate(self, text: str, source: str) -> str:
        """Phase 1 規則式翻譯（LLM 不可用時的後備）"""
        # 基本書面語→口語轉換
        rules = [
            ("是", "係"), ("不", "唔"), ("沒有", "冗"), ("什麼", "和"),
            ("現在", "依家"), ("了", "咋"), ("嗎", "咆"), ("吧", "囉"),
            ("我", "我"), ("你", "你"), ("他", "佢"), ("她", "佢"),
        ]
        result = text[:100]
        for old, new in rules:
            result = result.replace(old, new)

        prefix = "⚡ 喂！" if source == "notification" else "⚡ "
        return f"{prefix}{result}..."

    def _detect_emotion(self, original: str, response: str) -> str:
        """簡單情緒檢測"""
        urgent_keywords = ["緊急", "重要", "警告", "錯誤", "失敗", "urgent", "error", "failed"]
        excited_keywords = ["成功", "完成", "好消息", "success", "completed"]

        text_lower = (original + response).lower()
        if any(kw in text_lower for kw in urgent_keywords):
            return "battery_low"  # 紅色警告狀態
        elif any(kw in text_lower for kw in excited_keywords):
            return "talking"  # 藍色說話狀態
        return "idle"
