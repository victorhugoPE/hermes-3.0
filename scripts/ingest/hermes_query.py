#!/usr/bin/env python3
"""
Hermes 3.0 — Turbovec RAG Query Engine (Sub-millisecond Search)
Consulta semântica ultrarrápida usando SIMD sobre o índice TurboQuant 4-bit.

Uso:
    python hermes_query.py "como usar a skill de rust e ffi"
    python hermes_query.py "render loop wgpu e shaders" --scope client-l2-code
    python hermes_query.py "interlude protocol 746 handshake" --top 3
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
import argparse
import sqlite3
import numpy as np
from turbovec import IdMapIndex
from sentence_transformers import SentenceTransformer

HERMES_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAG_DIR = os.path.join(HERMES_ROOT, ".hermes", "rag")
INDEX_PATH = os.path.join(RAG_DIR, "hermes_index.tvim")
DB_PATH = os.path.join(RAG_DIR, "hermes_map.db")
MODEL_NAME = "all-MiniLM-L6-v2"

def query_via_daemon(prompt, top_k=5, scope=None, ext=None):
    socket_path = "/tmp/hermes_rag.sock"
    if not os.path.exists(socket_path): return None

    try:
        client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        client.settimeout(2.0)
        client.connect(socket_path)
        payload = json.dumps({"query": prompt, "top_k": top_k, "scope": scope, "ext": ext})
        client.sendall(payload.encode("utf-8"))

        chunks = []
        while True:
            part = client.recv(65536)
            if not part: break
            chunks.append(part)
        client.close()

        raw = b"".join(chunks).decode("utf-8")
        data = json.loads(raw)
        return data.get("results", []), data.get("total_ms", 0.0)
    except Exception:
        return None

def query_rag(prompt, top_k=5, scope=None, ext=None):
    # 1. Tentar via Daemon em background (Modo Instantâneo ~15ms)
    daemon_res = query_via_daemon(prompt, top_k, scope, ext)
    if daemon_res is not None:
        return daemon_res

    # 2. Fallback caso o daemon não esteja rodando
    if not os.path.exists(INDEX_PATH) or not os.path.exists(DB_PATH):
        print(f"❌ Índice não encontrado em {INDEX_PATH}. Rode hermes_indexer.py primeiro.")
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # Filtrar allowlist se houver scope ou ext
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
        if not rows:
            print(f"⚠️ Nenhum item no escopo/extensão especificados ({scope}, {ext}).")
            return []
        allowlist = np.array([r["id"] for r in rows], dtype=np.uint64)

    # Carregar modelo e índice
    model = SentenceTransformer(MODEL_NAME)
    index = IdMapIndex.load(INDEX_PATH)

    # Gerar embedding da query
    q_vec = model.encode([prompt], normalize_embeddings=True).astype(np.float32)
    q_vec = np.ascontiguousarray(q_vec)

    t0 = time.time()
    if allowlist is not None:
        scores, ids = index.search(q_vec, k=top_k, allowlist=allowlist)
    else:
        scores, ids = index.search(q_vec, k=top_k)

    search_time_ms = (time.time() - t0) * 1000

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

    conn.close()
    return results, search_time_ms

def main():
    parser = argparse.ArgumentParser(description="Hermes 3.0 — Turbovec RAG Query")
    parser.add_argument("query", help="Texto da busca (linguagem natural ou termos técnicos)")
    parser.add_argument("--top", type=int, default=5, help="Quantidade de resultados (default: 5)")
    parser.add_argument("--scope", type=str, default=None, choices=["skills", "client-l2-kb", "client-l2-code", "interlude-dll-forensics", "l2off-cpp", "servers-java-kt"], help="Filtrar por escopo de busca")
    parser.add_argument("--ext", type=str, default=None, help="Filtrar por extensão (.rs, .md, .wgsl)")
    args = parser.parse_args()

    results, search_ms = query_rag(args.query, top_k=args.top, scope=args.scope, ext=args.ext)

    print(f"\n🔎 Hermes 3.0 Turbovec Search: '{args.query}'")
    print(f"⚡ Busca vetorial executada em: {search_ms:.3f} ms (SIMD TurboQuant 4-bit)\n")
    print("-" * 70)

    for i, res in enumerate(results, 1):
        print(f"[{i}] Score: {res['score']:.4f} | Escopo: {res['scope']} | {res['title']}")
        print(f"    Caminho: {res['path']}")
        preview = res['content'][:250].replace('\n', ' ') + "..."
        print(f"    Trecho:  {preview}")
        print("-" * 70)

if __name__ == "__main__":
    main()
