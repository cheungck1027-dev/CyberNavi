#!/bin/bash
# ╔══════════════════════════════════════════════╗
# ║  CyberNavi Server — 一鍵啟動腳本            ║
# ╚══════════════════════════════════════════════╝

set -e

echo "⚡ CyberNavi 家用伺服器啟動中..."

# 檢查 .env 文件
if [ ! -f .env ]; then
    echo "☁️ 複製 .env 配置文件..."
    cp .env.template .env
    echo "⚠️  請先編輯 .env 文件，填入你的 API Key！"
    echo "    nano .env"
    exit 1
fi

# 檢查 Python 環境
if [ ! -d "venv" ]; then
    echo "🐍 建立 Python 虛擬環境..."
    python3 -m venv venv
fi

# 激活虛擬環境
source venv/bin/activate

# 安裝依賴
echo "📦 安裝 Python 依賴..."
pip install -r requirements.txt -q

# 啟動服務器
echo "🚀 啟動 CyberNavi API 伺服器..."
echo "   API 地址: http://localhost:8765"
echo "   API 文檔: http://localhost:8765/docs"
echo ""

python main.py
