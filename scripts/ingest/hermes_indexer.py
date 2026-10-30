#!/usr/bin/env python3
"""
Hermes 3.0 — Turbovec RAG Indexer (TurboQuant 4-bit)
Indexador semântico das 285 skills do Claude Opus 5.5 e dos 4.589 arquivos técnicos do Client L2.
"""
import os
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

import sys
import json
import time
import sqlite3
import numpy as np
from turbovec import IdMapIndex
from sentence_transformers import SentenceTransformer

HERMES_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAG_DIR = os.path.join(HERMES_ROOT, ".hermes", "rag")
MANIFESTS_DIR = os.path.join(HERMES_ROOT, "manifests")

INDEX_PATH = os.path.join(RAG_DIR, "hermes_index.tvim")
DB_PATH = os.path.join(RAG_DIR, "hermes_map.db")
MODEL_NAME = "all-MiniLM-L6-v2"
DIM = 384

def init_db(conn):
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS chunks (
            id INTEGER PRIMARY KEY,
            scope TEXT NOT NULL,
            title TEXT NOT NULL,
            path TEXT NOT NULL,
            content TEXT NOT NULL,
            ext TEXT,
            size_bytes INTEGER
        )
    """)
    c.execute("CREATE INDEX IF NOT EXISTS idx_chunks_scope ON chunks(scope)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_chunks_ext ON chunks(ext)")
    conn.commit()

def load_skills():
    skills_path = os.path.join(MANIFESTS_DIR, "skills_manifest.json")
    if not os.path.exists(skills_path):
        print(f"❌ Manifesto de skills não encontrado em {skills_path}")
        return []
    with open(skills_path, "r", encoding="utf-8") as f:
        skills = json.load(f)

    chunks = []
    # ID base de skills: 0x10000000 (268435456)
    base_id = 0x10000000
    for idx, s in enumerate(skills):
        full_text = f"Skill: {s['name']}\nOrigem: {s['origin']}\nDescrição: {s['description']}"
        # Tentar ler o conteúdo de SKILL.md se existir para dar profundidade semântica
        real_path = os.path.expanduser(s['path'])
        if os.path.exists(real_path):
            try:
                with open(real_path, "r", encoding="utf-8", errors="ignore") as sf:
                    # Pega os primeiros 1500 chars da skill para embedding
                    snippet = sf.read(1500)
                    full_text += f"\nConteúdo:\n{snippet}"
            except:
                pass

        chunks.append({
            "id": base_id + idx,
            "scope": "skills",
            "title": s["name"],
            "path": s["path"],
            "content": full_text,
            "ext": ".md",
            "size_bytes": len(full_text.encode('utf-8'))
        })
    return chunks

def load_client_l2_summary():
    l2_manifest = os.path.join(MANIFESTS_DIR, "client_l2_index.json")
    l2_kb = os.path.join(HERMES_ROOT, "docs", "CLIENT_L2_KNOWLEDGE_BASE.md")

    chunks = []
    # ID base do Client L2: 0x20000000 (536870912)
    base_id = 0x20000000

    # 1. Ingerir a base de conhecimento consolidada em seções
    if os.path.exists(l2_kb):
        with open(l2_kb, "r", encoding="utf-8") as f:
            kb_text = f.read()
        sections = kb_text.split("\n## ")
        for s_idx, sec in enumerate(sections):
            if not sec.strip(): continue
            lines = sec.split("\n")
            sec_title = lines[0].replace("#", "").strip()
            sec_body = "\n".join(lines[1:]).strip()[:1500]
            chunks.append({
                "id": base_id + s_idx,
                "scope": "client-l2-kb",
                "title": f"Client L2 — {sec_title}",
                "path": "docs/CLIENT_L2_KNOWLEDGE_BASE.md",
                "content": f"{sec_title}\n{sec_body}",
                "ext": ".md",
                "size_bytes": len(sec_body.encode('utf-8'))
            })

    # 2. Ingerir os arquivos de código mais relevantes da lista
    if os.path.exists(l2_manifest):
        with open(l2_manifest, "r", encoding="utf-8") as f:
            data = json.load(f)

        root_l2 = "/Users/victorhugo/Documents/Client L2"
        # Filtrar arquivos essenciais (crates lib.rs, apps main.rs, shaders, configs)
        key_files = []
        for item in data.get("files", []):
            p = item["path"]
            if (p.endswith("lib.rs") or p.endswith("main.rs") or
                p.endswith("Cargo.toml") or p.endswith(".wgsl") or
                p.startswith("docs/") or p.endswith("client.toml")):
                key_files.append(item)

        code_base_id = 0x30000000
        for c_idx, item in enumerate(key_files):
            rel_p = item["path"]
            full_p = os.path.join(root_l2, rel_p)
            ext = os.path.splitext(rel_p)[1]
            snippet = ""
            if os.path.exists(full_p):
                try:
                    with open(full_p, "r", encoding="utf-8", errors="ignore") as cf:
                        snippet = cf.read(1200)
                except:
                    pass

            content = f"Arquivo: {rel_p}\nExtensão: {ext}\nTamanho: {item['size_bytes']} bytes\n{snippet}"
            chunks.append({
                "id": code_base_id + c_idx,
                "scope": "client-l2-code",
                "title": rel_p,
                "path": full_p,
                "content": content,
                "ext": ext,
                "size_bytes": item["size_bytes"]
            })

    return chunks

def main():
    print("=" * 60)
    print("🚀 Hermes 3.0 — Turbovec RAG Indexer (TurboQuant 4-bit)")
    print("=" * 60)

    os.makedirs(RAG_DIR, exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    init_db(conn)

    print("📖 Coletando chunks de skills...")
    skills_chunks = load_skills()
    print(f"   → {len(skills_chunks)} skills preparadas.")

    print("📖 Coletando chunks do Client L2 (Dossiê + Código Canônico)...")
    l2_chunks = load_client_l2_summary()
    print(f"   → {len(l2_chunks)} chunks do Client L2 preparados.")

    all_chunks = skills_chunks + l2_chunks
    total = len(all_chunks)
    print(f"\n🔥 Total de chunks para indexação: {total}")

    if total == 0:
        print("❌ Nenhum dado encontrado para indexar.")
        return

    print(f"🧠 Carregando modelo local {MODEL_NAME}...")
    t0 = time.time()
    model = SentenceTransformer(MODEL_NAME)
    print(f"   → Modelo carregado em {time.time() - t0:.2f}s")

    print("⚡ Gerando embeddings semânticos...")
    texts = [c["content"] for c in all_chunks]
    ids = np.array([c["id"] for c in all_chunks], dtype=np.uint64)

    embeddings = model.encode(texts, batch_size=32, show_progress_bar=True, normalize_embeddings=True)
    embeddings = np.ascontiguousarray(embeddings.astype(np.float32))

    print(f"   → Shape dos embeddings: {embeddings.shape}")

    print("💾 Gravando no Turbovec (TurboQuant 4-bit compressão)... e SQLite metadata...")
    index = IdMapIndex(dim=DIM, bit_width=4)
    index.add_with_ids(embeddings, ids)
    index.sync(INDEX_PATH)

    # Salvar metadados no SQLite
    cur = conn.cursor()
    cur.execute("DELETE FROM chunks")
    for c in all_chunks:
        cur.execute("""
            INSERT INTO chunks (id, scope, title, path, content, ext, size_bytes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (c["id"], c["scope"], c["title"], c["path"], c["content"], c["ext"], c["size_bytes"]))
    conn.commit()
    conn.close()

    index_size_kb = os.path.getsize(INDEX_PATH) / 1024
    db_size_kb = os.path.getsize(DB_PATH) / 1024
    print(f"\n✅ Indexação concluída com sucesso em {time.time() - t0:.2f}s!")
    print(f"   📦 Índice Turbovec (.tvim): {INDEX_PATH} ({index_size_kb:.1f} KB)")
    print(f"   🗄️ Metadados SQLite (.db):   {DB_PATH} ({db_size_kb:.1f} KB)")

if __name__ == "__main__":
    main()
