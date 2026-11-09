# Relatório de testes — Space Clipboard

Data da execução: **09/11/2026**.

## 1. Ambiente real

| Dispositivo | Modelo | Android | API | Hardware | Tela |
|---|---|---|---|---|---|
| Projetor | HY300 (`rockchip`) | 11.0 | 28 | RK3326, ARMv7, ~997 MB RAM | 1024 × 600, 160 dpi |
| Celular | Samsung SM-J410G | 8.1.0 | 27 | ARMv7, ~879 MB RAM | 720 × 1480, 320 dpi |
| Host | Windows 11 | — | — | JDK 17, SDK 37 | — |

HY320, Android TV/Google TV de outra marca e emulador não estavam disponíveis para esta rodada.

## 2. Resultado resumido

| Suíte | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | **33 testes, 0 falhas** |
| Instrumentados (`connectedDebugAndroidTest`, SM-J410G) | **6 testes, 4 executados + 2 ignorados, 0 falhas** |
| Instrumentado de clipboard no HY300 | **passou** |
| Ponta a ponta real celular → HY300 | **passou** |
| Servidor em segundo plano | **passou** |
| Wi-Fi desligado/reconectado | **passou** |
| D-PAD no HY300 | **passou no fluxo principal (keyevents)** |

## 3. Testes JVM (33)

### Servidor HTTP — 11 testes

Arquivo: `app/src/test/java/com/spaceclipboard/app/network/server/ClipboardHttpServerTest.kt`

- inicialização em porta efêmera;
- `GET /api/status` público com protocolo e porta;
- pareamento por código, retorno de credencial;
- autenticação ausente → 401;
- recebimento de URL → 201;
- recebimento de texto → 201;
- UUID duplicado → 200 com `duplicate=true`;
- texto vazio → 400;
- UUID inválido → 400;
- conteúdo > 64 KB → 400;
- corpo > limite do servidor → 413;
- JSON malformado → 400;
- auth check com Bearer válido;
- delete autenticado e 404 no segundo delete;
- encerramento libera a porta;
- porta configurada ocupada → fallback para outra porta.

### Room/DAO — 4 testes

Arquivo: `HistoryDaoTest.kt` (Robolectric + Room em memória).

- 25 inserções mantêm somente 20, ordem correta;
- 40 inserções concorrentes mantêm limite de 20;
- UUID duplicado é ignorado;
- exclusão individual;
- limpeza total.

### Repositório de dispositivos — 3 testes

- pareamento grava token de saída cifrado e hash de entrada;
- autenticação por hash correto/incorreto;
- remoção revoga as duas credenciais;
- re-pareamento substitui endpoint e credenciais.

### Validação de conteúdo — 8 testes

- texto preservado exatamente;
- URLs HTTP/HTTPS;
- URL inválida cai como texto;
- vazio/nulo;
- NUL;
- 64 KB exatos vs. 64 KB + 1;
- UTF-8 medido em bytes;
- UUID e sanitização de nome.

### Pareamento/QR — 7 testes

- round-trip do payload;
- expiração;
- QR estranho/corrompido;
- sessão: token ou código, expiração;
- rate limit por IP;
- validação de endereço local;
- QR real gerado com ZXing e decodificado de volta ao payload original.

**Total: 11 + 4 + 3 + 8 + 7 = 33.**

## 4. Testes instrumentados

### SM-J410G — 6 testes

| Teste | Resultado | O que valida |
|---|---|---|
| `ClipboardInstrumentedTest.receivingContentCopiesToClipboardAndPersistsHistory` | passou | recebe, grava no Room, copia e lê o clipboard real (inclui `readText`, usado pelo botão Colar) |
| `ClipboardInstrumentedTest.manifestDeclaresNoBackgroundServices` | passou | nenhum `<service>` no APK final |
| `ClipboardInstrumentedTest.sharesheetTextIntentResolvesToShareActivityAndKeepsText` | passou | `ACTION_SEND` + `text/plain` resolve para `ShareActivity` |
| `DpadNavigationTest.dpadFocusesAControl` | passou | evento D-PAD real via UiAutomator cria foco |
| `EndToEndSenderTest` | ignorado sem argumentos | integração real opcional |
| `ClipboardCurrentProbeTest` | ignorado sem argumentos | leitura do clipboard atual, opcional |

### HY300 — clipboard

O teste `ClipboardInstrumentedTest` foi executado no HY300 e passou, usando uma Activity de teste mínima e `Instrumentation.startActivitySync` (evitando o problema do ROM customizado ao encerrar Activities do AndroidX Test).

### HY300 — limitação de instrumentação

Ao usar `ActivityScenario`/Compose Test Rule para encerrar `MainActivity` no ROM do HY300, o sistema mata o processo de instrumentação com “user request after error”. O app em uso normal não apresentou esse problema; os testes de clipboard e as verificações reais de servidor/clipboard passaram. O teste de D-PAD foi executado por keyevents e o teste de foco por UiAutomator foi executado no SM-J410G.

## 5. Teste ponta a ponta real

Cenário executado:

1. HY300 com Space Clipboard aberto, servidor ativo na porta 8765, QR Code e código manual exibidos.
2. Celular SM-J410G pareado com o HY300 por IP + código temporário.
3. `ShareActivity` iniciada com `Intent.ACTION_SEND` e `text/plain`.
4. Envio HTTP real pela rede local.
5. HY300 exibiu “Novo conteúdo recebido e copiado!”.
6. Histórico do HY300 registrou o texto, tipo LINK e remetente `SM-J410G`.
7. O clipboard do HY300 foi lido separadamente e continha exatamente `https://example.com/space-clipboard-e2e`.

O teste automatizado `EndToEndSenderTest` executa os passos 2–4 e verifica a confirmação “Enviado para ...” na tela de envio. Ele foi executado com sucesso no SM-J410G apontando para o HY300.

### Envio manual real

Também foi executado o fluxo sem Sharesheet:

1. celular já pareado com o HY300;
2. app do celular → **Enviar** → editor de texto;
3. texto `https://example.com/manual-send` digitado;
4. destino HY300 selecionado;
5. **Enviar agora**;
6. celular exibiu “Enviado para HY300.”;
7. HY300 exibiu “Novo conteúdo recebido e copiado!” e registrou o link no histórico.

Esse teste valida o botão **Enviar** da Home, a rota interna, o editor, a seleção de destino, o feedback de sucesso e a comunicação real. O botão **Colar** usa `ClipboardWriter.readText()` e essa leitura foi coberta pelo teste instrumentado.

## 6. Ciclo de vida e rede

### Servidor em primeiro plano

```text
GET /api/status  → online
KEYCODE_HOME
GET /api/status  → falha de conexão (exit 7)
reabrir o app
GET /api/status  → online novamente
```

O tempo de espera após HOME foi de 4 s; a porta não estava mais acessível.

### Wi-Fi desligado/religado no celular

```text
Wi-Fi ligado   → servidor online em 192.168.0.9:8765
svc wifi disable
Wi-Fi desligado → conexão falhou
svc wifi enable
Wi-Fi ligado   → servidor online novamente
```

### Porta

- O teste JVM ocupa a porta configurada e confirma que o servidor escolhe outra.
- O teste de encerramento confirma que a porta é liberada.

## 7. D-PAD

Evidências:

- No HY300, `KEYCODE_DPAD_CENTER` sobre o botão “Histórico” abriu a tela de histórico; `KEYCODE_BACK` retornou.
- A rail lateral recebeu foco e foi percorrida com eventos D-PAD; Ajustes foi aberto e o retorno funcionou.
- No SM-J410G, o teste com UiAutomator injeta `pressDPadDown()` e verifica um nó focado.
- Anéis de foco ciano são visíveis em botões, cards e rail.
- Diálogos usam foco inicial no botão seguro (Cancelar em ações destrutivas; campo/botão principal em formulários).

Não foi usado um controle remoto físico; os eventos foram injetados pelo Android (`adb shell input keyevent` e UiAutomator), que é o mesmo caminho de entrada do D-PAD.

## 8. Desempenho medido

No HY300, APK release final:

| Métrica | Valor |
|---|---|
| Primeiro frame (`Displayed`) | 2 s 669 ms |
| Servidor ativo | ~2,1 s após início do processo |
| PSS em repouso | ~42 MB |
| APK release | 4,2 MB |
| APK debug | 19,5 MB |
| Debug no HY300 | ~17 s até o primeiro frame |

O debug é muito mais lento por não ter R8/otimização de recursos. Para uso real, instalar o release.

## 9. Matriz dos 20 testes obrigatórios do enunciado

| # | Requisito | Status | Evidência |
|---|---|---|---|
| 1 | Inicialização do servidor | passou | JVM + HY300 |
| 2 | Encerramento do servidor | passou | JVM + HOME |
| 3 | Liberação da porta | passou | teste JVM |
| 4 | Recebimento de texto | passou | JVM + instrumentado |
| 5 | Recebimento de URL | passou | JVM + E2E |
| 6 | Autenticação válida | passou | JVM + E2E |
| 7 | Autenticação inválida | passou | JVM |
| 8 | Conteúdo vazio | passou | JVM |
| 9 | Conteúdo > 64 KB | passou | JVM |
| 10 | Persistência no Room | passou | Robolectric + instrumentado |
| 11 | Limite de 20 | passou | concorrente e sequencial |
| 12 | Exclusão | passou | JVM + DAO |
| 13 | Cópia para clipboard | passou | SM-J410G e HY300 |
| 14 | Mudança de endereço IP | parcial | redesenho/NSD implementados; perda e retorno de Wi-Fi testados; troca de sub-rede não testada |
| 15 | Perda de conexão Wi-Fi | passou | celular |
| 16 | Receptor indisponível | parcial | UI e timeouts implementados; não foi automatizado com o receptor fechado |
| 17 | Pareamento por QR Code | parcial | QR gerado/decodificado em teste; pareamento real por código; câmera física não testada |
| 18 | Compartilhamento pelo Sharesheet | parcial | intent resolvido e E2E via `ACTION_SEND`; seleção manual no Chrome não automatizada |
| 18b | Envio manual no app | passou | tela Enviar texto, digitação, destino e E2E manual |
| 19 | Navegação por D-PAD | parcial | foco e fluxo principal no HY300; não exaustivo em todas as telas |
| 20 | Ausência de serviços em segundo plano | passou | manifest + varredura + HOME |

“Parcial” significa que a funcionalidade existe e há testes da lógica, mas a condição física específica não pôde ser reproduzida integralmente no ambiente.

## 10. Limitações e riscos conhecidos

1. HTTP sem TLS; tráfego observável na LAN.
2. Servidor IPv4 apenas.
3. HY320 não testado.
4. QR por câmera não foi apontado para uma tela física.
5. Chrome não foi operado manualmente; usei o mesmo intent do Sharesheet.
6. HY300 tem Keymaster quebrado; fallback local de chave foi necessário.
7. Teste exaustivo de D-PAD em todas as telas não foi automatizado no HY300.
8. Não há teste de troca de sub-rede nem de AP Isolation real.
9. A interface está em português.
10. Sem recebimento em segundo plano, por requisito.

## 11. Comandos

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Integração real opcional:

```bash
adb shell am instrument -w \
  -e class com.spaceclipboard.app.EndToEndSenderTest \
  -e receiverHost 192.168.0.6 \
  -e pairCode 123456 \
  -e expectedText https://example.com/teste \
  com.spaceclipboard.app.test/androidx.test.runner.AndroidJUnitRunner
```

## 12. Evidências visuais

- `docs/screenshots/hy300-release.png`
- `docs/screenshots/hy300-history.png`
- `docs/screenshots/phone-home-final.png`
- `docs/screenshots/phone-share-final.png`
- `docs/screenshots/phone-devices-final.png`
- `docs/screenshots/phone-settings-final.png`
- `docs/screenshots/phone-no-wifi.png`
- `docs/screenshots/manual-send.png`
- `docs/screenshots/manual-success.png`

Os screenshots do HY300 foram extraídos de gravação de tela porque o display do projetor marca a captura direta como segura; a interface é a mesma.
