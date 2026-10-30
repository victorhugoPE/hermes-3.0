# Base de Conhecimento: Client L2 — Hermes 3.0 v0.0

> Documento de referência arquitetural e forense do projeto `Client L2` para ingestão no **Turbovec RAG (TurboQuant 4-bit)**.
> **Fonte:** verificação direta em disco do workspace `/Users/victorhugo/Documents/Client L2` (Cargo workspace, resolver "3", rust-version 1.98).
> **Disciplina de qualidade do workspace:** `unsafe_code = "forbid"` no nível do workspace; clippy `all = deny`, `pedantic = warn`.

---

## 1. Visão Geral do Projeto
- **Caminho Local:** `/Users/victorhugo/Documents/Client L2`
- **Objetivo:** Recriação moderna, performática e modular da engine do cliente de **Lineage II (Chronicle 6: Interlude / Protocol 746)** escrita em **Rust (2024 Edition)**.
- **Substituição:** Substitui o runtime legado Direct3D 8 / Unreal Engine 2 por uma arquitetura nativa com **WGPU / WGSL**, interface **Fermata UI** (Lua 5.4 + Taffy + Glyphon) e rede assíncrona **Tokio**.
- **Volume Técnico:** 4.589 arquivos de código e documentação (`.rs`, `.wgsl`, `.kdl`, `.toml`, `.json`, `.md`) catalogados em `manifests/client_l2_index.json`.
- **⚠️ Aviso de dados:** o `README.md` da raiz menciona "11 crates", mas o `Cargo.toml` do workspace declara **33 crates** em `crates/` — o número do README está desatualizado. O mapa abaixo reflete o disco.

---

## 2. Mapa das Crates (33 crates em `crates/`)

O `Cargo.toml` do workspace organiza os módulos em três grupos: **módulos internos**, **módulos reconstruídos de `engine.dll` (REA / Interlude C6)** e **módulos reconstruídos das demais DLLs do Interlude C6**.

### 2.1 Núcleo binário da Unreal Engine 2 (leitura in-place)
- **`ue2-core`** — Reader little-endian com bounds-check compartilhado por todos os formatos binários Unreal/L2 (FCompactIndex, FGuid, FVector, FMatrix, serializadores).
- **`ue2-package`** — Tabelas de pacotes UE2 (names, imports, exports) como usadas pelo Lineage 2; a entrada deve ser um pacote **já descriptografado**; todo índice é validado durante o parse (`.u`, `.utx`, `.ukx`, `.usx`, `.unr`).
- **`ue2-assets`** — Objetos UE2 lidos in-place de um pacote descriptografado: texturas (DXT1/3/5, P8, RGBA), static/skeletal meshes, animações; nada é convertido ou reescrito.
- **`ue2-level`** — Atores posicionados e câmeras de cena lidos de mapas UE2 (`.unr`); extrai apenas o que o cliente desenha.

### 2.2 Crates de domínio Lineage 2
- **`l2-catalog`** — Registro de assets: localiza assets do cliente instalado por caminho (`Package.Name`) e lê in-place: texturas, meshes, animações, texturas de material. Pacotes carregam no primeiro uso.
- **`l2-crypto`** — Descriptografia de arquivos protegidos por headers `Lineage2VerXXX` (Ver111/Ver121 XOR de 1 byte, Ver413 RSA-1024 sobre zlib). Puro: bytes entram, bytes saem. (README também cita Blowfish em blocos de 8 bytes, RSA 1024 com PKCS#1 v1.5, NewCrypt e Adler32.)
- **`l2-dat`** — Decodificador de tabelas `.dat` orientado a schema; nomes de campo seguem o layout em disco. Camada de migração das tabelas legadas de `system`.
- **`l2-dat-c6`** — Schemas `.dat` do Interlude Chronicle 6 (ProtocolVersion 746): weapongrp, armorgrp, npcgrp, itemname-e etc., espelhando as notas de layout do Lineage2Elbera.
- **`l2-env`** — **⚠️ Responsabilidade ambígua (reconciliar):** o `lib.rs` documenta ambiente de horário do dia do cliente (`system/Env.int` para início do dia e `system/TimeEnv0.int` para rampas de cor por hora: céu, nuvens, luz ambiente), enquanto o `README.md` o descreve como localizador canônico de diretórios de instalação (`system/`, `Textures/`, `StaticMeshes/`). As duas responsabilidades parecem coexistir.
- **`l2-fpak`** — Leitor read-only de contêineres `.fpak` usados pelo runtime do Client L2 (layout little-endian).
- **`l2-net`** — Protocolo completo cliente-servidor do L2 Interlude (C6 / Protocol 746). Cliente assíncrono para LoginServer `:2106` e GameServer `:7777` com controle estrito de fluxo (*Traffic Governor*).
- **`l2-ui`** — "Canastra UI core": markup + subconjunto de CSS lidos em uma draw list. Sem GPU, sem fontes — o tamanho de texto vem de um `TextMeasure` fornecido pelo renderer; produz comandos de desenho e áreas de clique, totalmente testável sem janela. (Taffy + Glyphon + mlua conforme README.)
- **`l2-weather`** — Clima e céu dinâmicos, baseados em engenharia reversa do Ultra Dynamic Sky (LU4 / UE5) e prática PBR.
- **`l2-world`** — Motor de simulação de mundo: atores, movimento e câmera 3D.

### 2.3 Reconstrução da `engine.dll` (grupo `ue2-engine-*`)
- **`ue2-engine-core`** — Runtime core UE2.5 / L2 C6: infraestrutura de objetos, serialização, nomes, tipos primitivos.
- **`ue2-engine-network`** — Subsistema de rede e protocolo: conexões, canais, pacotes.
- **`ue2-engine-render`** — Pipeline de renderização e shaders (FRenderInterface, FSceneNode, skeletal skinning `USkeletalMeshInstance`).
- **`ue2-engine-physics`** — Física, detecção de colisão e geodata (UE2.5).
- **`ue2-engine-actor`** — Hierarquia de entidades/atores (AActor, APawn, AL2Character, ALineagePlayerController).
- **`ue2-engine-ui`** — Subsistema de UI 2D, janelas canônicas nwindow, diálogos HTM (UE2.5 / L2).
- **`ue2-engine-audio`** — Áudio 3D e efeitos sonoros (UE2.5).
- **`ue2-engine-ffi`** — Camada de exportação binária FFI ("engine.dll compatível com L2.exe"): implementa os símbolos exportados esperados pelo executável mestre.

### 2.4 Reconstrução das demais DLLs do Interlude C6
- **`ue2-core-dll`** — Reconstrução da `Core.dll` (UE2.5 / L2 C6): subsistemas fundamentais.
- **`ue2-creature`** — Reconstrução da `LineageCreature.dll`.
- **`ue2-nwindow`** — Reconstrução da `nwindow.dll`: hierarquia oficial de janelas da NCSoft (motor de UI do L2 C6).
- **`ue2-lineage-env`** — Reconstrução da `lineageenv.dll`: motor de ambiente do L2 C6 / Interlude.
- **`ue2-window-drv`** — Reconstrução de `Window.dll` e `WinDrv.dll` (drivers de janela da UE2.5).
- **`ue2-d3d-drv`** — Reconstrução da `D3DDrv.dll`: pipeline de renderização Direct3D 9 de baixo nível.
- **`ue2-net-ipdrv`** — Reconstrução de `IpDrv.dll` e `L2KProtocolCore.dll` (transporte de sockets da UE2.5).
- **`ue2-media-audio`** — Reconstrução de `ALAudio.dll`, `openal32.dll`, `ogg.dll` e `vorbis.dll`.
- **`ue2-support`** — Bibliotecas de suporte: `Fire.dll` (texturas procedurais FireEngine/WaterTexture), `npkcrypt.dll` (criptografia de pacotes do GameGuard) e correlatas.
- **`ue2-script-vm`** — Port canônico da VM UnrealScript (UE2.5): executa sequências de bytecode embutidas em pacotes `.u` (`Core.u`, `Engine.u`).

---

## 3. Aplicações (`apps/` — 3 executáveis)
1. **`apps/l2-client`** — "Client L2 — Modern Lineage II Interlude Native Client". Binário principal do jogo: loop de renderização WGPU, streaming de terreno, HUD Fermata UI e conexões de rede. Contém `src/` (`canonical_engine`, `canonical_anim`, `dev_editor`, `fermata_windows`, `fermata_tools`, `audio`, `cursor`, `base_window`…), `assets/`, `tests/` e `fermata_ui/` (a pasta de specs Lua de UI produzida pelo transpilador XDAT).
2. **`apps/l2-studio`** — "L2 Studio — Interlude C6 Asset, Character and World Workspace". Bancada de inspeção e validação de assets/mundo (meshes, texturas, cenas), inspirada na ferramentaria Fermata Studio; editor de UI e testes de iluminação.
3. **`apps/l2encdec`** — Wrapper CLI da biblioteca `l2-crypto` seguindo a interface `l2lib`. Uso: `l2encdec -c decode -p <protocolo> [-f <arquivo_chave>] -o <saida> <entrada>`.

---

## 4. Mapa de `docs/` (principais pastas e documentos)

### 4.1 Documentos de direção (raiz de `docs/`)
- **`GUIA_MESTRE.md`** — Guia mestre de desenvolvimento / diretriz permanente: papel de engenheiro-sênior, disciplina de "nenhum valor chutado", contexto de dois projetos, formato de roadmap. **Deve ser relido no início de cada sessão.**
- **`ROADMAP.md`** — Roadmap mestre de desenvolvimento (Interlude C6), derivado de auditoria profunda + Sentinel RAG.
- **`ARCHITECTURE_FORENSIC.md`** — Relatório forense de arquitetura do cliente Fermata: arquitetura de crates, arquitetura do app `l2-client`, arquitetura de rede, ciclo exato do render-loop, fluxo de dados formato NCSoft → GPU, pontos de extensão de bridges, tabela-resumo de componentes.
- **`PLANO_FASES_PORT_CANONICO.md`** — Plano de fases do port canônico do Interlude: nomes exatos de fonte (headers UE2 + símbolos binários), zero chute.
- **`PLANO_SISTEMA_CORES_E_ATMOSFERA_LU4.md`** — Plano mestre de sistema de cores, iluminação dinâmica e atmosfera estilo UE5.
- **`COMPARACAO_INTERLUDE_X_LU4.md`** — Comparação Interlude (C6) × LU4 (UE5): o que foi extraído de cada fonte, para herança de comportamento.
- **`LU4_GLOBAL_ASSETS_CATALOG.md`** — Catálogo mestre da extração global de LU4 e Client L2 (consolidação forense + arquitetura de consumo).
- **`STUDIO_PROFESSIONAL_REVAMP.md`** — Especificação do revamp profissional do L2 Studio (TopBar global, parâmetros de sombra, gerenciador de tokens, editor).
- **`TEST_STRATEGY.md`** — Matriz de verificação de 5 níveis para o Fermata UI Bridge.
- **`POC_RESULTS.md`** — Resultados de prova de conceito: assets Silent Dark R2 no cliente Fermata.
- **Especificações Fermata / UI Bridge:** `ASSET_BRIDGE.md`, `BEHAVIOR_BRIDGE.md`, `FERMATA_UI_BRIDGE_ARCHITECTURE.md`, `UI_IR_SPEC.md` (Fermata UI Intermediate Representation / UI-IR), `UI_RUNTIME_FORENSIC.md`, `INTERFACE_U_FORENSIC.md` (dissecção completa do pacote UnrealScript `interface.u`), `XDAT_FORENSIC.md`, `DECOMPILER_INTEGRATION_FORENSIC.md`.

### 4.2 Pastas forenses e de pesquisa
- **`fermata/`** — Série de dossiês de engenharia reversa do cliente Fermata, 00–07 (`00_INVENTARIO`, `01_SIMBOLOS_E_TOPOLOGIA`, `02_PIPELINE_TEXTURAS`, `03_PIPELINE_UI_E_JANELAS`, `04_SISTEMA_INPUT_E_EVENTOS`, `05_SISTEMA_CAMERA`, `06_RENDER_GRAPH_E_PRE_RENDER`, `07_FORMATOS_DE_ARQUIVO`) + auditorias de fase, inventários (`inventario.json`, `macho_fermata_*.json`), CSVs de gap, `agentes/`, `fase4/`, `porte/`, `referencia/`.
- **`fermata-decompiled/`** — Notas numeradas de descompilação do binário Fermata: `01_SYMBOLS_MAP`, `02_NETWORK_PROTOCOL`, `03_LOBBY_AND_PAWNS_RENDERING`, `04_WORLD_STREAMING_AND_TERRAIN` + `symbols_dump.json`.
- **`fermata-extracted-v2/`** — Saída de extração Fermata v2: `FERMATA_ANALYSIS_REPORT.md`, `fpak/`, `kdl_windows/`, `network_packets/`, `shaders/`, `symbols/` + comentários de código minerados.
- **`fermata-old-extracted/`** — Conjunto de extração anterior (`engine_symbols.txt` + diversos `shader_*.wgsl`).
- **`forensics/`** — `ARCHITECTURE_FORENSIC_AUDIT.md` e `ARCHITECTURE_FORENSIC_VERIFIED_V2.md`.
- **`interlude_forensics/`** — Dumps forenses por DLL do cliente C6 original (Core, ALAudio, D3DDrv, defopenal32, …): `*_forensics.json` + `*_reconstructed.h` + `_master_forensics_index.json`.
- **`interlude_forensics_v2/`** — Dumps profundos v2 das mesmas DLLs (`*_deep.json`, `*_deep.h`, `_master_deep_index.json`).
- **`ghidra_reverse_engineering/`** — Descompilações Ghidra em C (`engine_decompiled_completo.c`, `core_decompiled.c`, `d3ddrv_decompiled.c`, `alaudio_decompiled.c`, `fire_decompiled.c`, `engine_fisica_colisao.c`, `engine_functions.txt`, `categorias/`).
- **`lineage2js_forensics/`** — Specs canônicas numeradas (`01_DAT_SCHEMAS_CANONICAL`, `02_BSP_AND_COLLISION_SPECS`, `03_MATERIAL_AND_SHADER_PIPELINE`, `04_SKILLS_AND_ANIMNOTIFY_DISPATCH`, `05_UI_DOM_XML_CSS_MAPPING`, `06_PHYSICS_AND_MOVEMENT_ENGINE`, `07_UNREALSCRIPT_VM_AND_BYTECODE`, `08_PARTICLE_AND_EMITTER_SYSTEM`).
- **`l2_original/`** — Material do cliente original: `FORMATOS.md`, `CADEIA_DE_TEXTURAS.md`, `PORTAO0_FERMATA_ONLY.md`, `PROGRESSO.md`, `RED_TEAM.md`, CSVs de cobertura, `agentes/`.
- **`l2packtool-defs/`** — Definições de schema DDF (`armorgrp.ddf`, `etcitemgrp.ddf`, `itemname-e.ddf`, `npcgrp.ddf`, `npcname-e.ddf`, `skillgrp.ddf`, `skillname-e.ddf`) + `nucleo.json`.
- **`elbera-research/`** — Notas de pesquisa sobre o projeto de referência Lineage2Elbera: `ground-truth.md`, `foundation-audit.md`, `dat-format-notes.md`, `geodata-format.md`, `asset-editor-design.md`, `assets-tooling.md`, `character-pipeline.md`, `GUIA-JUGADORES.md` (36 entradas).
- **`elbera_forensics/`** — Forenses do Elbera: `README.md`, `AGENTS.md`, `official_docs/`, `tools/`.
- **`lu4_dump/`** — Dumps de extração do LU4 (UE5): `binary_analysis/`, `blueprints/`, `blueprints_summary/`, `curves/`, `datatables/`, `entities_and_combat/`, `entities_weapons/`, `geodata_collision/` (14 entradas).
- **`lu4_forensics/`** — Síntese forense do LU4: `asset_inventory/`, `behavior/`, `configs/`, `lighting_postprocess/`, `materials/`, `uds/`, `pbr_shader_blueprint.md`, `ue5_shader_reference.md`.
- **`protocol/`** — Notas de protocolo: `server_packets.md`, `client_gap.md`, `client_guesses.md`.
- **`re_reference/`** — `INTERLUDE_PACKET_CATALOG.md`.
- **`weather/`** — Trabalho de clima/shaders: `AUDITORIA_SHADERS_E_MATERIAIS.md`, `KANBAN.md`, `REALITY_PROMPT.md`, `snapshots/`.
- **`refs/`** — Referências externas: `lab-pbr/`, `Sky3D/`.
- **`research/`** — (vazio no momento do levantamento).

---

## 5. Ferramentas e Scripts

### 5.1 `tools/` (24 entradas)
- **`tools/xdat_to_lua`** — CLI Rust (crate Cargo na árvore do workspace, com `src/tests`): converte `assets/silent_dark/interface.json` em scripts `.lua` da Fermata UI (flags: `--all`, `--window <Nome>`).
- **`tools/l2-baker`** — "L2 Baker — Pipeline de Remasterização em Lote e Empacotador de UTX da Unreal Engine 2" (Rust + clap + image): remasterização em lote e reempacotamento de UTX.
- **`tools/frida`** — Kit de instrumentação em runtime para o `L2.exe` original do Interlude C6 (há README): extração de vetores de câmera, detecção de colisão, validação de cliques (`runner.py`, `trace_actor_click.js`, `trace_camera_and_view.js`). Relacionado: `frida_l2_surgical_dump.js` na raiz.
- **`tools/re`** — Auxiliares de engenharia reversa: `disasm_phys_l2.py`, `disasm_phys_l2_direct.py`, `disasm_click_body.py`, `disassemble_critical.py`, `audit_packets_and_xdat.py`, `ExportNetworkImports.java`, `dumps/`.
- **`tools/wine`** — Executa o cliente real sob Wine: `run_interlude_wine.sh`, `configure_l2_ini.py`, `l2_packet_sniffer.py`.
- **`tools/differential`** — Testes diferenciais: `test_cipher_parity.py`, `test_protocol_packets.py`, `test_rotator_math.py`.
- **`tools/verify_all.sh`** — Script de verificação em lote.
- **Toolchain C# de extração CUE4Parse / LU4 (UE5)** — majoritariamente `.csproj` + `Program.cs`: `cue4parse-extractor`, `cue4parse-normal-extractor`, `cue4parse-config-extractor`, `lu4-complete-extractor`, `lu4-engine-dumper`, `lu4-entity-dumper`, `lu4-audio-dumper`, `lu4-audio-and-icons-extractor`, `lu4-behavior-extractor`, `lu4-city-scanner`, `lu4-global-cities-extractor`, `lu4-dungeons-extractor`, `lu4-login-lobby-miner`, `lu4-mesh-dumper`, `lu4-uds-miner`, `lu4-ui-miner`, `lu4-vfx-dumper`, `lighting-forensic`.

### 5.2 `scripts/` (17 entradas)
- **`scripts/elbera-tools/`** — Toolchain derivada do Elbera: `anim/`, `audio/`, `audit/`, `dat/`, `dev/`, `l2lib/`, `maps/`, `mesh/`, `battery.sh`, `build-tools.sh`.
- **Extração do binário/fpak Fermata:** `fermata_full_extractor.py`, `extract_fermata_binary.py`, `extract_fermata_fpak.py`.
- **Descompilação/demangle do Fermata:** `decompile_fermata.py`, `demangle_fermata.py`.
- **Análise:** `build_fermata_inventory.py`, `build_gap_csv.py`, `analyze_fermata_network.py`.
- **Conversão e execução:** `kdl_to_lua.py` (janela KDL → Lua), `parse_upackage.py`, `o3_decrypt_run.py`.
- **Launchers macOS:** `Abrir-Interlude-MeuAmbiente.command`, `Abrir-Interlude-Original.command`.

---

## 6. Pipeline de Renderização (6 passos)
1. Terreno a partir de heightmap
2. Static meshes
3. Pawns / NPCs / skeletal
4. Partículas / magias
5. Fermata UI (Taffy + Glyphon)
6. Textura offscreen → FXAA (`fxaa.wgsl`) → swapchain

## 7. Handshake de Rede C6 / Protocol 746
- **LoginServer `:2106`:** `Init 0x00` (SessionID / módulo RSA / chave Blowfish) → `AuthGameGuard 0x07` → `GGAuth 0x0B` → `RequestAuthLogin 0x00` (RSA-1024) → `LoginOk 0x03` → `RequestServerList 0x05` → `ServerList 0x04` → `RequestServerLogin 0x02` → `PlayOk 0x07`.
- **GameServer `:7777`:** `ProtocolVersion 746` → `KeyPacket 0x00` → `AuthLogin 0x08` → `CharSelectInfo 0x13` → `CharacterSelect 0x0D` → `CharSelected 0x15` → `EnterWorld 0x03` → `UserInfo 0x04`.

## 8. Pipeline de UI
`Interface.xdat` → `tools/xdat_to_lua` → `fermata_ui/*.lua` → `crates/l2-ui` (mlua 5.4) → Taffy flexbox + Glyphon → passe de renderização WGPU.

## 9. Motor de Conhecimento Atual do Projeto
- O projeto já opera um **"Germes Hybrid RAG"** (FTS5 BM25 + vetores) com consultas de símbolos AST e comparação cruzada contra `l2off` (C++) e `brproject-2026` (Java), via `.claude/skills/clientel2-rag/scripts/*.bat`.
- **Sentinel RAG Unificado:** base vetorial cobrindo `Client L2` + `cliente-lu4`, consultável via `.sentinel/sentinel-query.py`:
  - `python3 .sentinel/sentinel-query.py "<busca>"` (pesquisa nos 2 projetos)
  - `--project lu4` (só cliente-lu4) ou `--project l2` (só Client L2).

## 10. Diretrizes de Qualidade e Build
- **Skill obrigatória:** `brproject-rust-lua-ffi` antes de escrever qualquer código Rust/FFI.
- **Gate de qualidade:** `cargo fmt`, `cargo clippy --all-targets -- -D warnings`, `cargo test` — e depois atualizar o RAG.
- **Comandos de build:** `cargo build --release`; `cargo run --bin l2-client --release`; `cargo run --bin l2-studio --release`; `cargo run --bin xdat_to_lua -- assets/silent_dark/interface.json apps/l2-client/fermata_ui`.
- **Pré-requisitos:** Rust 1.85+ (Edition 2024), Vulkan SDK / DX12 / Metal, Python 3.10+.

## 11. Metodologia Mandatória (`docs/GUIA_MESTRE.md`)
- **Papel:** engenheiro-sênior em um cliente Rust do L2 Interlude; nada é implementado sem ser lido, compreendido e testado; **nenhum valor de protocolo, formato de arquivo ou constante de jogo é adivinhado** — tudo deve ser rastreável ao projeto de referência, a uma captura real de pacote ou a documentação oficial de RE. *"Uma lacuna documentada é sempre preferível a uma suposição plausível."*
- **Contexto de dois projetos:** projeto próprio `l2-cliente` (Rust, com Sentinel Turbovec + Watchdog RAG ao vivo) e projeto de referência Lineage2Elbera (clonado em `external/Lineage2Elbera`) — valioso conceitualmente/protocolamente, **não para portar linha a linha** (gateway JS + cliente web three.js, toolchain de assets em Python, servidor Java derivado de aCis).
- **Fatos derivados do Elbera que a diretriz exige dominar:** módulo RSA "embaralhado" da NCSoft (4 passos de desembaralhamento), XOR de login unidirecional, Blowfish little-endian portado do servidor Java, cifra de fluxo XOR (não Blowfish) na perna de jogo, e limitador anti-flood hardcoded (3 conn/s, bans de 300s) que exige um governor de saída de ~400ms.
- **Formatos UE2:** pacotes v117/v123, bit de continuação de FCompactIndex dependente de contexto, bloco de material Lineage escondido no meio do property stream, heightmaps de terreno escondidos em texturas de 16 bits, tabelas `.dat` criptografadas (RSA + protocolos 111–414), decodificadores de pixel DXT1/3/5, RGBA8, RGB8, L8, P8, G16.
- **Padrões metodológicos a adotar:** *sourcing gates*, oráculo de renderização contra o cliente real e testes byte-exatos contra ferramentas nativas de referência.
- **Documentos centrais do Elbera a consultar:** seu `README.md` (arquitetura de duas camadas build plane / play plane), `docs/HANDOFF.md`, `docs/web-port-architecture.md` (marcos M1–M5).
