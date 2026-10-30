#!/usr/bin/env python3
"""
Hermes 3.0 — Turbovec Master RAG Indexer (TurboQuant 4-bit)
Indexador semântico unificado de todo o ecossistema Lineage 2:
  1. Skills Claude Opus 5.5 (285 skills)
  2. Client L2 (Base de conhecimento + 33 crates Rust)
  3. Interlude DLL Forensics (Ghidra C + Headers reconstruídos de todas as DLLs C6)
  4. L2Off C++ Sources (Vanganth Interlude Extension + NCSOFT C++ IOCP)
  5. Emuladores de Servidor (L2JLucera2, RUSaCis, l2kt - Protocolos e Handlers)
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
    if not os.path.exists(skills_path): return []
    with open(skills_path, "r", encoding="utf-8") as f:
        skills = json.load(f)

    chunks = []
    base_id = 0x10000000
    for idx, s in enumerate(skills):
        full_text = f"Skill: {s['name']}\nOrigem: {s['origin']}\nDescrição: {s['description']}"
        real_path = os.path.expanduser(s['path'])
        if os.path.exists(real_path):
            try:
                with open(real_path, "r", encoding="utf-8", errors="ignore") as sf:
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
    base_id = 0x20000000
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

    if os.path.exists(l2_manifest):
        with open(l2_manifest, "r", encoding="utf-8") as f:
            data = json.load(f)
        root_l2 = "/Users/victorhugo/Documents/Client L2"
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

def load_dll_forensics():
    root_l2 = "/Users/victorhugo/Documents/Client L2"
    target_dirs = [
        os.path.join(root_l2, "docs", "interlude_forensics"),
        os.path.join(root_l2, "docs", "interlude_forensics_v2"),
        os.path.join(root_l2, "docs", "ghidra_reverse_engineering")
    ]

    chunks = []
    base_id = 0x40000000
    c_idx = 0

    for d in target_dirs:
        if not os.path.exists(d): continue
        for dirpath, _, filenames in os.walk(d):
            for f in sorted(filenames):
                if f.endswith(('.h', '.c', '.json', '.txt')):
                    full_p = os.path.join(dirpath, f)
                    rel_p = os.path.relpath(full_p, root_l2)
                    ext = os.path.splitext(f)[1]
                    try:
                        with open(full_p, "r", encoding="utf-8", errors="ignore") as fl:
                            snippet = fl.read(1500)
                        chunks.append({
                            "id": base_id + c_idx,
                            "scope": "interlude-dll-forensics",
                            "title": f"DLL Forensics: {f}",
                            "path": full_p,
                            "content": f"Arquivo Forense: {rel_p}\n{snippet}",
                            "ext": ext,
                            "size_bytes": os.path.getsize(full_p)
                        })
                        c_idx += 1
                    except:
                        pass
    return chunks

def load_l2off_cpp():
    dirs = [
        "/Users/victorhugo/Downloads/test/Vanganth_Sources_IL",
        "/Users/victorhugo/Downloads/NCSOFT_sources"
    ]
    chunks = []
    base_id = 0x50000000
    c_idx = 0

    for root_dir in dirs:
        if not os.path.exists(root_dir): continue
        for dirpath, _, filenames in os.walk(root_dir):
            for f in sorted(filenames):
                if f.endswith(('.cpp', '.h')):
                    full_p = os.path.join(dirpath, f)
                    rel_p = os.path.relpath(full_p, "/Users/victorhugo/Downloads")
                    ext = os.path.splitext(f)[1]
                    try:
                        with open(full_p, "r", encoding="utf-8", errors="ignore") as fl:
                            snippet = fl.read(1500)
                        chunks.append({
                            "id": base_id + c_idx,
                            "scope": "l2off-cpp",
                            "title": f"L2Off C++: {f}",
                            "path": full_p,
                            "content": f"L2Off C++: {rel_p}\n{snippet}",
                            "ext": ext,
                            "size_bytes": os.path.getsize(full_p)
                        })
                        c_idx += 1
                    except:
                        pass
    return chunks

def load_server_emulators():
    root_servers = "/Users/victorhugo/Downloads/arquivos"
    if not os.path.exists(root_servers): return []

    chunks = []
    base_id = 0x60000000
    c_idx = 0

    # Focar em pacotes, handlers de rede e modelos chave
    for dirpath, _, filenames in os.walk(root_servers):
        for f in sorted(filenames):
            if f.endswith(('.java', '.kt')) and any(k in f.lower() for k in ["packet", "handler", "player", "network", "server", "crypt", "geodata", "world"]):
                full_p = os.path.join(dirpath, f)
                rel_p = os.path.relpath(full_p, root_servers)
                ext = os.path.splitext(f)[1]
                try:
                    with open(full_p, "r", encoding="utf-8", errors="ignore") as fl:
                        snippet = fl.read(1200)
                    chunks.append({
                        "id": base_id + c_idx,
                        "scope": "servers-java-kt",
                        "title": f"Server: {f}",
                        "path": full_p,
                        "content": f"Servidor Emulator: {rel_p}\n{snippet}",
                        "ext": ext,
                        "size_bytes": os.path.getsize(full_p)
                    })
                    c_idx += 1
                except:
                    pass
                if c_idx >= 500: # Limite seguro para os 500 arquivos nucleares mais importantes
                    break
        if c_idx >= 500: break
    return chunks

def main():
    print("=" * 70)
    print("🚀 Hermes 3.0 — Master Turbovec RAG Indexer (TurboQuant 4-bit)")
    print("=" * 70)

    os.makedirs(RAG_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    init_db(conn)

    print("📖 [1/5] Coletando Skills do Claude Opus 5.5...")
    skills_chunks = load_skills()
    print(f"   → {len(skills_chunks)} skills preparadas.")

    print("📖 [2/5] Coletando Client L2 (Dossiê + 33 Crates Rust)...")
    l2_chunks = load_client_l2_summary()
    print(f"   → {len(l2_chunks)} chunks do Client L2 preparados.")

    print("📖 [3/5] Coletando DLL Forensics (Ghidra C + Headers Interlude C6)...")
    dll_chunks = load_dll_forensics()
    print(f"   → {len(dll_chunks)} arquivos forenses de DLLs preparados.")

    print("📖 [4/5] Coletando L2Off C++ (Vanganth Interlude Extender + NCSOFT C++)...")
    l2off_chunks = load_l2off_cpp()
    print(f"   → {len(l2off_chunks)} arquivos C++ do L2Off preparados.")

    print("📖 [5/5] Coletando Servidores L2 (L2JLucera2, RUSaCis, l2kt)...")
    server_chunks = load_server_emulators()
    print(f"   → {len(server_chunks)} arquivos nucleares de servidores preparados.")

    all_chunks = skills_chunks + l2_chunks + dll_chunks + l2off_chunks + server_chunks
    total = len(all_chunks)
    print(f"\n🔥 TOTAL CONSOLIDADO PARA INDEXAÇÃO: {total} chunks")

    if total == 0:
        print("❌ Nenhum dado encontrado.")
        return

    print(f"🧠 Carregando modelo local {MODEL_NAME}...")
    t0 = time.time()
    model = SentenceTransformer(MODEL_NAME)

    print("⚡ Gerando embeddings semânticos (batch size 64)...")
    texts = [c["content"] for c in all_chunks]
    ids = np.array([c["id"] for c in all_chunks], dtype=np.uint64)

    embeddings = model.encode(texts, batch_size=64, show_progress_bar=True, normalize_embeddings=True)
    embeddings = np.ascontiguousarray(embeddings.astype(np.float32))

    print(f"   → Shape dos embeddings: {embeddings.shape}")
    print("💾 Gravando no Turbovec (TurboQuant 4-bit compressão)... e SQLite metadata...")

    index = IdMapIndex(dim=DIM, bit_width=4)
    index.add_with_ids(embeddings, ids)
    index.sync(INDEX_PATH)

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
    print(f"\n✅ MASTER INDEXAÇÃO CONCLUÍDA EM {time.time() - t0:.2f}s!")
    print(f"   📦 Índice Turbovec (.tvim): {INDEX_PATH} ({index_size_kb:.1f} KB)")
    print(f"   🗄️ Metadados SQLite (.db):   {DB_PATH} ({db_size_kb:.1f} KB)")

if __name__ == "__main__":
    main()
