# Arquitetura — Space Clipboard

## 1. Visão geral

O Space Clipboard é um app Android single-process, offline-first. Cada instalação pode:

- atuar como **receptor**, abrindo um servidor HTTP enquanto está em primeiro plano;
- atuar como **remetente**, recebendo texto pelo Sharesheet **ou digitando/colando na tela “Enviar texto”**, e enviando por HTTP na LAN.

Não há backend, nuvem, conta, Firebase, Supabase ou telemetria. O estado persistente fica em Room e DataStore, no próprio aparelho.

## 2. Camadas

```text
UI Compose / ViewModels
        │
        ├── ReceiverController ── ClipboardHttpServer (Ktor CIO)
        │        ├── PairingSession + QR
        │        └── NsdRegistration
        │
        ├── DeviceService / PairingManager ── ClipboardClient (Ktor CIO)
        │        └── DiscoveryController ── NsdDiscovery
        │
        ├── HistoryRepository ── Room
        ├── DeviceRepository ── Room + TokenCipher
        ├── SettingsRepository ── DataStore
        └── ClipboardWriter ── ClipboardManager
```

### Responsabilidades

| Componente | Responsabilidade |
|---|---|
| `MainViewModel` | estado das telas, histórico, dispositivos, configurações, pareamento, envio manual e mensagens |
| `ManualSendScreen` | editor multilinha, contador em bytes, colar do clipboard, seleção de destino e feedback |
| `ShareViewModel` | receber o texto do intent, listar destinos, enviar, parear e mostrar resultado |
| `ReceiverController` | ciclo de vida da sessão receptora, servidor, NSD, QR, sessão de pareamento, recebimento e cópia |
| `ClipboardHttpServer` | API HTTP, autenticação bearer, validação de payload, idempotência, limites |
| `ClipboardClient` | cliente HTTP com timeouts e mapeamento de erros |
| `DiscoveryController` | uma sessão de descoberta NSD por demanda, com timeout e cancelamento |
| `PairingManager` | troca de credenciais, QR/código, gravação do dispositivo pareado |
| `DeviceService` | teste de conexão, envio, redescobrimento de IP, renomear/remover |
| `HistoryRepository` | manter no máximo 20 registros com transação atômica |
| `DeviceRepository` | pareamento, hash de token de entrada, cifra de token de saída |
| `TokenCipher` | AES-GCM com Android Keystore e fallback local |
| `NetworkInfoProvider` | selecionar IPv4 Wi-Fi/Ethernet, observar mudanças |
| `AppContainer` | DI manual e escopo de aplicação |

## 3. Ciclo de vida e sessão receptora

O app **não declara serviços**. O `SpaceClipboardApplication` registra um `DefaultLifecycleObserver` no `ProcessLifecycleOwner`:

```text
ON_START  → ReceiverController.start()
ON_STOP   → ReceiverController.stop()
```

Ao iniciar:

1. lê nome, porta e ID do DataStore;
2. consulta a rede atual e escolhe IPv4 local;
3. abre o servidor Ktor vinculado **somente ao IPv4 da rede local**;
4. gera uma `PairingSession` e o QR Code;
5. registra o serviço NSD `_spaceclipboard._tcp.`;
6. publica o estado `ACTIVE` na UI;
7. carrega o último item do histórico de forma assíncrona.

Ao encerrar:

1. remove o registro NSD e libera o multicast lock;
2. cancela o job de rotação do QR;
3. para o servidor Ktor com timeout;
4. libera a porta;
5. cancela o observer de rede;
6. publica `STOPPED`.

`ProcessLifecycleOwner` foi escolhido em vez de callbacks exclusivos de Activity porque:
- não encerra o servidor em mudança de configuração/rotação;
- encerra de fato quando o processo vai para segundo plano;
- não depende de um único ciclo de tela.

Todas as operações de sessão são serializadas por `Mutex`, evitando corrida entre abrir, fechar, trocar de rede e reconfigurar porta.

### Mudança de rede

`NetworkInfoProvider.observe()` registra:
- callback da rede padrão;
- callback específico de Wi-Fi, necessário para Wi-Fi local sem internet.

Em cada mudança, `ReceiverController` compara o IPv4 e o transporte. Se o IP mudou, o servidor/NSD são reiniciados; se a rede caiu, a sessão é encerrada e a UI mostra “Dispositivo sem rede”. Quando o Wi-Fi volta, a sessão é recriada automaticamente.

## 4. PAREAMENTO

### Sessão temporária

`PairingSession` contém:

- `token`: 16 bytes aleatórios em Base64 URL;
- `code`: 6 dígitos para entrada manual;
- `createdAt`/`expiresAt`: 10 minutos.

O QR Codifica:

```json
{"v":1,"i":"uuid-do-receptor","h":"192.168.0.6","p":8765,"t":"token","e":1794250000000}
```

Compactado em JSON e Base64 URL com prefixo `SCP1:`. O QR não contém credencial permanente.

O código de 6 dígitos tem rate limit de 12 tentativas por minuto por IP. O token de 128 bits não depende da força do código.

### Troca de credenciais

```text
Remetente                                          Receptor
   │  POST /api/pair {pairToken|pairCode, senderId,
   │                  senderName, senderPort,
   │                  senderToken}  ───────────────▶│
   │                                                │ valida sessão + rate limit
   │                                                │ gera credential (32 bytes)
   │                                                │ salva:
   │                                                │  - outbound = senderToken (cifrado)
   │                                                │  - inbound hash = SHA256(credential)
   │                                                │ gira a PairingSession
   │◀──────────── {credential, receiver} ───────────│
   │ salva:
   │  - outbound = credential (cifrado)
   │  - inbound hash = SHA256(senderToken)
   ▼
```

Assim, **cada sentido tem um token próprio**. O receptor autentica o remetente pelo hash do token que emitiu; o remetente autentica o receptor pelo hash do token que emitiu. Remover o dispositivo apaga o hash de entrada e invalida o acesso.

### QR e código

- `GET /api/pair?token=...` ou `?code=...` valida sem consumir, para pré-checagem.
- `POST /api/pair` consome a sessão e emite a credencial.
- Após sucesso, um novo QR/código é gerado.
- O QR é gerado com ZXing no tamanho 512 px e exibido em 170 dp.
- O scanner usa `journeyapps:zxing-android-embedded`, com permissão de câmera pedida só ao tocar em “Ler QR”.

## 5. API HTTP

Servidor: Ktor 3.6.1, engine CIO, bind no IPv4 selecionado, porta 8765 com fallback.

### Endpoints

| Método | Caminho | Auth | Descrição |
|---|---|---|---|
| GET | `/` | não | identificação mínima |
| GET | `/api/status` | não | estado do servidor, deviceId, nome, protocolo, porta, pareamento disponível |
| GET | `/api/pair` | token/código | inspeção da sessão |
| POST | `/api/pair` | token/código | troca de credenciais |
| GET | `/api/auth/check` | Bearer | valida a credencial e devolve o dispositivo |
| POST | `/api/clipboard` | Bearer | recebe texto/URL |
| DELETE | `/api/history/{id}` | Bearer | exclui um item remoto |

### Regras

- Texto: não vazio, sem NUL, no máximo 64 KB UTF-8.
- Corpo: limite de 96 KB (plugin `RequestBodyLimit`).
- UUID: obrigatório e validado.
- Idempotência: `history.id` é chave primária; segundo POST do mesmo UUID devolve `200`/`duplicate=true`.
- `type` declarado deve ser `text` ou `url`; se ausente, é derivado do conteúdo.
- Não há endpoint para ler o histórico de outro dispositivo.
- Respostas de erro: `{ "success": false, "error": { "code": "...", "message": "..." } }`.
- Códigos: 200, 201, 400, 401, 404, 410, 413, 429, 500.
- Bearer token tem pelo menos 20 caracteres; é hasheado com SHA-256 e comparado por busca no banco.

### Timeouts

- Cliente: conexão 4 s, requisição 8 s, socket 8 s.
- Servidor: limite de corpo, engine CIO com timeout de conexão ociosa padrão e encerramento com `grace 250 ms`/`timeout 2,5 s`.
- O cliente não repete envios automaticamente; um reenvio manual usa o mesmo UUID apenas se o app quiser idempotência. O envio real gera UUID novo.

## 6. Histórico e persistência

Room, banco `space_clipboard.db`:

```text
history(id TEXT PK, text, type, senderName, senderId, receivedAt)
paired_devices(id TEXT PK, name, host, port, protocol,
               outboundTokenEncrypted, inboundTokenHash,
               lastSeen, createdAt)
```

- `HistoryDao.insertAndTrim()` executa insert + trim em uma transação.
- O trim apaga tudo que não estiver entre os 20 mais recentes (`ORDER BY receivedAt DESC, id DESC`).
- Inserção concorrente é serializada pelo Room.
- O limite é aplicado também em recebimentos simultâneos.
- DataStore guarda `device_id`, `device_name` e `server_port`.
- `allowBackup=false`.

## 7. Segurança

### Tokens

- 32 bytes aleatórios (`SecureRandom`) em Base64 URL.
- Token de entrada: apenas SHA-256 no banco.
- Token de saída: AES-GCM.
- Prefixos `H1:`/`F1:` registram se a cifra foi feita no Keystore ou no fallback.
- Chave do Keystore: alias `space_clipboard_device_tokens_v1`, AES-256-GCM.
- Fallback: chave AES-256 no `SharedPreferences` privado, usado apenas se a geração no Keystore falhar. O HY300 apresentou falha de Keymaster; sem fallback o pareamento não funciona.

### Pareamento

- token temporário de 128 bits;
- código de 6 dígitos + rate limit;
- QR de uso único;
- expiração de 10 minutos;
- nenhuma credencial permanente no QR;
- nenhum token em log.

### Transporte

HTTP na LAN, sem TLS nesta versão. A decisão prioriza compatibilidade com Android 8.1 e projetores de baixo custo. O servidor não aceita destinos fora da rede local e não faz conexões para a internet. Em redes não confiáveis, o tráfego pode ser observado; a recomendação é usar uma rede privada.

## 8. Descoberta NSD/mDNS

- Tipo: `_spaceclipboard._tcp.`
- Registro apenas enquanto o servidor está ativo.
- TXT: `id`, `name`, `v`.
- Descoberta iniciada por demanda, com timeout de 12 s.
- Um `NsdManager.ResolveListener` por vez, com fila.
- Multicast lock liberado ao parar.
- Sem varredura de faixas de IP e sem polling.
- Se NSD falhar, a UI oferece QR/manual e mostra explicação sobre AP Isolation.

## 9. UI e design system

### Princípios

- Tema escuro azul-marinho/grafite, azul e ciano.
- Tipografia Space Grotesk embarcada (OFL) com fallback de sistema.
- Contraste alto, corpo 17 sp, títulos 20–32 sp.
- Espaçamento em múltiplos de 4/8 dp, painéis com cantos 16–22 dp.
- Foco visível com borda ciano de 2 dp e fundo elevado; nenhuma animação contínua.
- Alvos mínimos de 48 dp.
- Layout adaptativo:
  - rail lateral em largura ≥ 840 dp;
  - barra inferior em telas compactas;
  - home em duas colunas quando há espaço;
  - lista e cards empilhados no celular.

### Componentes

`SpacePanel`, `SpaceFocusable`, `SpaceButton`, `StatusPill`, `TypeBadge`, `CodeDisplay`, `PairingDialog`, `ConfirmDialog`, `TextPromptDialog`, `EmptyState`, `FeedbackBanner`.

### D-PAD

- `FocusRequester` nos focos iniciais;
- `focusGroup` em grupos de ações;
- `onFocusChanged` no histórico para restaurar foco por ID;
- `focusProperties` implícito pela ordem espacial do Compose;
- diálogos com foco inicial seguro.

## 10. Fluxos de envio

```text
ACTION_SEND (text/plain)
   ↓
ShareActivity → ShareViewModel
   ↓
lista de dispositivos pareados
   ├── 0 → tela de pareamento
   ├── 1 → tenta envio direto
   └── N → seleção
   ↓
DeviceService.send()
   ├── valida texto
   ├── decrypt outbound token
   ├── POST /api/clipboard
   ├── em falha de conexão: NSD one-shot para achar novo IP e retry
   └── sucesso/erro
```

O app não monitora o clipboard do remetente e não exige que o Space Clipboard esteja aberto no celular antes do compartilhamento.

### Envio manual

A tela **Enviar texto** é uma rota interna (`send`) acessível pelo botão **Enviar** da Home e, em telas largas/Android TV, por um item próprio da rail lateral. Ela usa o mesmo `DeviceService.send()` do Sharesheet:

```text
MainActivity → rota send → ManualSendScreen
  texto digitado/colado
  dispositivo pareado selecionado
  ClipboardValidator (64 KB)
  DeviceService.send()
  FeedbackBanner de sucesso/erro
```

O botão **Colar** lê o clipboard apenas quando o usuário toca nele, via `ClipboardWriter.readText()`, e nunca em segundo plano. A tela não depende do intent externo e funciona com o app aberto normalmente.

## 11. Decisões de performance

- Release com R8 e `shrinkResources`.
- Sem Hilt, sem WebView, sem ícones extended.
- Fonte local, sem download em runtime.
- Sem animações contínuas.
- Ktor client/`DeviceService`/`TokenCipher` criados sob demanda.
- Servidor só em primeiro plano.
- Histórico limitado a 20 e consultas indexadas.
- Descoberta com timeout e cancelamento.

Resultado no HY300 release: ~2,7 s até o primeiro frame, ~42 MB PSS e 4,2 MB de APK.

## 12. Limitações arquiteturais

- IPv4 apenas.
- HTTP sem TLS.
- Sem recebimento em segundo plano por requisito.
- Sem sincronização entre vários receptores.
- AP Isolation não pode ser detectado com 100% de certeza; é informado como orientação.
- Android Keystore pode falhar em projetores; fallback local documentado.
