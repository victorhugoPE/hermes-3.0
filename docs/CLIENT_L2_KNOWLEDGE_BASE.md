# Base de Conhecimento: Client L2 — Hermes 3.0 v0.0

> Documento de referência arquitetural e forense do projeto `Client L2` para ingestão no **Turbovec RAG (TurboQuant 4-bit)**.

---

## 1. Visão Geral do Projeto
- **Caminho Local:** `/Users/victorhugo/Documents/Client L2`
- **Objetivo:** Recriação moderna, de alta performance e modular da engine do cliente de **Lineage II (Chronicle 6: Interlude / Protocol 746)** escrita em **Rust (2024 Edition)**.
- **Substituição:** Substitui o runtime legado Direct3D 8 / Unreal Engine 2 da NCSoft por uma arquitetura nativa com suporte a **WGPU / WGSL**, interface **Fermata UI** (Lua 5.4 + Taffy + Glyphon) e rede assíncrona **Tokio**.
- **Volume Técnico:** 4.589 arquivos de código e documentação técnica (`.rs`, `.wgsl`, `.kdl`, `.toml`, `.json`, `.md`).

---

## 2. Mapa das Crates (Monorepo Cargo)

### Aplicações (`apps/`)
1. **`apps/l2-client`**: Executável principal do jogo. Integra o loop de renderização WGPU, streaming de terreno, física de colisão, HUD Fermata UI e conexões de rede.
2. **`apps/l2-studio`**: Bancada visual de desenvolvimento para inspeção forense de pacotes `.utx`/`.ukx`, pré-visualização de meshes, testes de iluminação e edição de UI ao vivo.
3. **`apps/l2encdec`**: Utilitário nativo de criptografia/decriptografia de arquivos do jogo.

### Bibliotecas de Domínio Lineage II (`crates/`)
1. **`l2-net`**: Cliente assíncrono para o LoginServer (porta 2106) e GameServer (porta 7777) com controle estrito de fluxo (*Traffic Governor*).
2. **`l2-crypto`**: Criptografia de rede: Blowfish 8-byte blocks, RSA 1024 (padding PKCS#1 v1.5), NewCrypt e verificação Adler32.
3. **`l2-dat-c6`**: Parsers e decriptadores de tabelas `.dat` do Interlude (`itemname-e.dat`, `weapongrp.dat`, `armorgrp.dat`, etc.).
4. **`l2-catalog`**: Registro central de assets que vincula IDs numéricos do jogo a texturas e modelos da UE2.
5. **`l2-env`**: Descoberta determinística de diretórios do cliente (`system/`, `Textures/`, `StaticMeshes/`).
6. **`l2-ui`**: Sistema de interface reativo construído com Taffy (Flexbox), Glyphon (rasterização TrueType de alto desempenho) e bindings para Lua 5.4.
7. **`l2-weather`**: Motor unificado de clima, ciclo 24h, posições celestes e parâmetros minerados do Ultra Dynamic Sky (UDS) do LU4.
8. **`l2-world`**: Gerenciador de mundo, tiles de mapa e streaming de terreno.
9. **`l2-fpak`**: Gerenciamento de pacotes virtuais e extração rápida.

### Parsers e Emulação da Unreal Engine 2 (`crates/ue2-*`)
1. **`ue2-core`**: Fundamentos binários da UE2: compact indexes, FGuid, FVector, FMatrix e serializadores primitivos.
2. **`ue2-package`**: Leitor de tabelas de nomes, exportações e importações de pacotes da NCSoft (`.u`, `.utx`, `.ukx`, `.usx`, `.unr`).
3. **`ue2-assets`**: Decodificador de texturas DXT1/DXT3/DXT5/P8/RGBA e estruturas de StaticMesh/SkeletalMesh.
4. **`ue2-level`**: Carregador de cenários e mundos `.unr`, mapeando layers de terreno e geo-props estáticas.
5. **`ue2-script-vm`**: Máquina virtual de execução de bytecodes UnrealScript (.u) desacoplada.
6. **`ue2-engine-physics`**: Port canônico da física C++ da UE2 (`APawn::physWalking`, `stepUp`, `UPrimitive::LineCheck`).
7. **`ue2-engine-actor`**: Hierarquia de atores, pawns, controladores e entidades interativas.
8. **`ue2-engine-render`**: Pipeline de renderização em WGPU e tradução de shaders.
9. **`ue2-engine-network`**: Protocolos de replicação e sincronização de atores.
10. **`ue2-engine-ui`**: Ponte de controles e janelas nativas da UE2.
11. **`ue2-media-audio` / `ue2-engine-audio`**: Sistema de som com suporte a Ogg Vorbis (OggS) e efeitos sonoros canônicos.

---

## 3. Diretrizes de Qualidade e RAG Existente
- **Skill Obrigatória:** `brproject-rust-lua-ffi` antes de editar qualquer código Rust/FFI.
- **Gate de Qualidade:** `cargo fmt`, `cargo clippy --all-targets -- -D warnings` e `cargo test` (262+ testes verdes).
- **Sentinel RAG Atual:** Base vetorial cobrindo o monorepo `Client L2` + `cliente-lu4` rodando via `.sentinel/sentinel-query.py`.
