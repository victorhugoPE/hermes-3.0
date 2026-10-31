#!/usr/bin/env python3
"""
Hermes 3.0 — Turbovec Incremental Indexer
Atualiza cirurgicamente apenas os arquivos modificados no índice TurboQuant 4-bit e SQLite.
"""
import os
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")

import sys
import time
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
DIM = 384

def upsert_file(file_path, scope):
    if not os.path.exists(file_path):
        print(f"❌ Arquivo não encontrado: {file_path}")
        sys.exit(1)

    if not os.path.exists(INDEX_PATH) or not os.path.exists(DB_PATH):
        print(f"❌ Índice base não existe. Execute hermes_indexer.py primeiro.")
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # Obter ID existente ou criar novo ID sequencial
    cur.execute("SELECT id FROM chunks WHERE path = ?", (file_path,))
    existing = cur.fetchone()
    if existing:
        doc_id = existing[0]
    else:
        cur.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM chunks")
        doc_id = cur.fetchone()[0]

    # Ler snippet do arquivo
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            content_snippet = f.read(1500)
    except Exception as e:
        print(f"❌ Erro ao ler arquivo: {e}")
        sys.exit(1)

    ext = os.path.splitext(file_path)[1]
    filename = os.path.basename(file_path)
    title = f"{scope}: {filename}"
    full_content = f"Arquivo: {file_path}\nEscopo: {scope}\n{content_snippet}"
    size_bytes = os.path.getsize(file_path)

    # Gerar embedding único
    model = SentenceTransformer(MODEL_NAME)
    vec = model.encode([full_content], normalize_embeddings=True).astype(np.float32)
    vec = np.ascontiguousarray(vec)

    # Atualizar Turbovec
    index = IdMapIndex.load(INDEX_PATH)
    ids = np.array([doc_id], dtype=np.uint64)
    index.add_with_ids(vec, ids)
    index.sync(INDEX_PATH)

    # Atualizar SQLite
    cur.execute("""
        INSERT INTO chunks (id, scope, title, path, content, ext, size_bytes)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            scope = excluded.scope,
            title = excluded.title,
            path = excluded.path,
            content = excluded.content,
            ext = excluded.ext,
            size_bytes = excluded.size_bytes
    """, (doc_id, scope, title, file_path, full_content, ext, size_bytes))

    conn.commit()
    conn.close()
    print(f"✅ Arquivo '{filename}' (ID: {doc_id}) sincronizado no Turbovec com sucesso!")

def main():
    parser = argparse.ArgumentParser(description="Hermes 3.0 — Incremental Turbovec Updater")
    parser.add_argument("--file", required=True, help="Caminho do arquivo para upsert")
    parser.add_argument("--scope", default="general", help="Escopo de indexação")
    args = parser.parse_args()

    t0 = time.time()
    upsert_file(args.file, args.scope)
    print(f"⚡ Upsert concluído em {(time.time() - t0)*1000:.1f}ms")

if __name__ == "__main__":
    main()
