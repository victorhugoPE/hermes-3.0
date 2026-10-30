#!/usr/bin/env python3
"""
Hermes 3.0 — High-Performance RAG Daemon (Unix Domain Socket)
Mantém o modelo de embedding (all-MiniLM-L6-v2) e o índice Turbovec aquecidos na memória.
Elimina o overhead de cold-start do PyTorch de ~6s para ~15ms por busca.
"""
import os
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

import sys
import time
import json
import socket
import signal
import sqlite3
import numpy as np
from turbovec import IdMapIndex
from sentence_transformers import SentenceTransformer

HERMES_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAG_DIR = os.path.join(HERMES_ROOT, ".hermes", "rag")
INDEX_PATH = os.path.join(RAG_DIR, "hermes_index.tvim")
DB_PATH = os.path.join(RAG_DIR, "hermes_map.db")
SOCKET_PATH = "/tmp/hermes_rag.sock"
PID_PATH = "/tmp/hermes_rag.pid"
MODEL_NAME = "all-MiniLM-L6-v2"

class RagDaemon:
    def __init__(self):
        self.running = True
        print(f"[RAG Daemon] 🧠 Carregando modelo de embeddings ({MODEL_NAME})...")
        t0 = time.time()
        self.model = SentenceTransformer(MODEL_NAME)
        print(f"[RAG Daemon]    → Modelo carregado em {time.time() - t0:.2f}s")

        print(f"[RAG Daemon] 📦 Carregando índice Turbovec ({INDEX_PATH})...")
        self.index = IdMapIndex.load(INDEX_PATH)

        self.conn = sqlite3.connect(DB_PATH, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        print(f"[RAG Daemon] ⚡ Pronto e aquecido na memória! Escutando em {SOCKET_PATH}")

    def search(self, query, top_k=5, scope=None, ext=None):
        t0 = time.time()
        cur = self.conn.cursor()
        allowlist = None
        sql_filter = []
        params = []
        if scope:
            sql_filter.append("scope = ?")
            params.append(scope)
        if ext:
            sql_filter.append("ext = ?")
            params.append(ext)

        if sql_filter:
            query_sql = f"SELECT id FROM chunks WHERE {' AND '.join(sql_filter)}"
            rows = cur.execute(query_sql, params).fetchall()
            if not rows: return {"results": [], "search_ms": 0.0, "total_ms": 0.0}
            allowlist = np.array([r["id"] for r in rows], dtype=np.uint64)

        q_vec = self.model.encode([query], normalize_embeddings=True).astype(np.float32)
        q_vec = np.ascontiguousarray(q_vec)

        t_search = time.time()
        if allowlist is not None:
            scores, ids = self.index.search(q_vec, k=top_k, allowlist=allowlist)
        else:
            scores, ids = self.index.search(q_vec, k=top_k)
        search_ms = (time.time() - t_search) * 1000

        results = []
        for score, doc_id in zip(scores[0], ids[0]):
            if doc_id == 0 and score == 0.0: continue
            row = cur.execute("SELECT * FROM chunks WHERE id = ?", (int(doc_id),)).fetchone()
            if row:
                results.append({
                    "score": float(score),
                    "id": int(doc_id),
                    "scope": row["scope"],
                    "title": row["title"],
                    "path": row["path"],
                    "content": row["content"],
                    "ext": row["ext"]
                })
        total_ms = (time.time() - t0) * 1000
        return {"results": results, "search_ms": search_ms, "total_ms": total_ms}

def cleanup():
    if os.path.exists(SOCKET_PATH):
        try: os.unlink(SOCKET_PATH)
        except: pass
    if os.path.exists(PID_PATH):
        try: os.unlink(PID_PATH)
        except: pass

def signal_handler(signum, frame):
    print("\n[RAG Daemon] 🛑 Finalizando daemon...")
    cleanup()
    sys.exit(0)

def main():
    cleanup()
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    with open(PID_PATH, "w") as f:
        f.write(str(os.getpid()))

    daemon = RagDaemon()
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(SOCKET_PATH)
    server.listen(16)

    while daemon.running:
        try:
            conn, _ = server.accept()
            data = conn.recv(65536)
            if not data: continue

            req = json.loads(data.decode("utf-8"))
            query = req.get("query", "")
            top_k = req.get("top_k", 5)
            scope = req.get("scope")
            ext = req.get("ext")

            res = daemon.search(query, top_k=top_k, scope=scope, ext=ext)
            conn.sendall(json.dumps(res).encode("utf-8"))
            conn.close()
        except Exception as e:
            print(f"[RAG Daemon] Erro na requisição: {e}")

if __name__ == "__main__":
    main()
