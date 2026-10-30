#!/usr/bin/env python3
"""
Hermes 3.0 — Ultra-Fast Socket Client (Zero Dependencies, Zero Cold Start)
Conecta no Unix Socket do RAG Daemon e retorna em ~20 milissegundos.
"""
import sys
import json
import socket
import argparse

SOCKET_PATH = "/tmp/hermes_rag.sock"

def main():
    parser = argparse.ArgumentParser(description="Hermes 3.0 Ultra-Fast RAG Query")
    parser.add_argument("query", help="Texto da busca")
    parser.add_argument("--top", type=int, default=3, help="Quantidade de resultados (default: 3)")
    parser.add_argument("--scope", type=str, default=None, help="Filtrar por escopo")
    parser.add_argument("--ext", type=str, default=None, help="Filtrar por extensão")
    args = parser.parse_args()

    try:
        client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        client.settimeout(3.0)
        client.connect(SOCKET_PATH)

        payload = json.dumps({
            "query": args.query,
            "top_k": args.top,
            "scope": args.scope,
            "ext": args.ext
        })
        client.sendall(payload.encode("utf-8"))

        chunks = []
        while True:
            part = client.recv(65536)
            if not part: break
            chunks.append(part)
        client.close()

        raw = b"".join(chunks).decode("utf-8")
        data = json.loads(raw)
        results = data.get("results", [])
        total_ms = data.get("total_ms", 0.0)
        search_ms = data.get("search_ms", 0.0)

        print(f"\n🔎 Hermes 3.0 Fast RAG: '{args.query}'")
        print(f"⚡ Resposta do Daemon em: {total_ms:.1f} ms (SIMD Search: {search_ms:.2f} ms)\n")
        print("-" * 70)

        for i, res in enumerate(results, 1):
            print(f"[{i}] Score: {res['score']:.4f} | Escopo: {res['scope']} | {res['title']}")
            print(f"    Caminho: {res['path']}")
            preview = res['content'][:250].replace('\n', ' ') + "..."
            print(f"    Trecho:  {preview}")
            print("-" * 70)

    except Exception as e:
        # Fallback para o modo tradicional se o daemon cair
        import subprocess
        script_dir = sys.path[0]
        cmd = ["/Users/victorhugo/Downloads/BrProject-Native/.sentinel/venv/bin/python3", f"{script_dir}/hermes_query.py", args.query, "--top", str(args.top)]
        if args.scope: cmd.extend(["--scope", args.scope])
        if args.ext: cmd.extend(["--ext", args.ext])
        subprocess.run(cmd)

if __name__ == "__main__":
    main()
