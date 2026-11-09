# Segurança — Space Clipboard

## 1. Modelo de ameaça considerado

O Space Clipboard assume que todos os dispositivos estão na mesma LAN. Os riscos
tratados são:

1. outro dispositivo da LAN tentando enviar conteúdo sem pareamento;
2. adivinhação do código de 6 dígitos;
3. reutilização de QR Code vazado;
4. leitura de credenciais armazenadas no aparelho;
5. servidor exposto em interfaces além do Wi-Fi local;
6. payloads gigantes, vazios, malformados ou duplicados;
7. observação passiva do tráfego HTTP na LAN;
8. QR Code malicioso apontando para fora da rede local.

## 2. Mitigações implementadas

| Risco | Mitigação |
|---|---|
| Envio não autorizado | Bearer token de 32 bytes por dispositivo; hash SHA-256 no receptor |
| Força bruta do código | 6 dígitos + limite de 12 tentativas/minuto por IP + expiração de 10 min |
| QR vazado | token de 128 bits, uso único, curto prazo; QR gira após pareamento |
| Credenciais em repouso | AES-GCM; Keystore quando funcional; fallback local em Keymaster quebrado |
| Exposição de rede | bind somente no IPv4 local; não em 0.0.0.0; sem encaminhamento externo |
| Payloads abusivos | limite de 64 KB de texto, 96 KB de corpo, UUID, validação de tipo |
| Duplicação | UUID como chave primária; segundo envio retorna duplicate |
| QR malicioso | host precisa ser privado/loopback/link-local; sem URLs, esquemas ou portas inválidas |
| Logs | nenhum conteúdo clipboard é logado; apenas erros técnicos |
| Backup | `android:allowBackup="false"` |
| Serviço persistente | proibido por design; servidor só em primeiro plano |

## 3. Tokens

- `Tokens.newToken(32)` usa `SecureRandom`.
- O token de entrada é salvo apenas como `SHA-256` em Base64 URL.
- A consulta de autenticação busca o hash; não há comparação de token em texto no banco.
- O token de saída é cifrado com AES-256-GCM.
- Prefixos `H1:` e `F1:` identificam a origem da chave e permitem decifrar corretamente após reinício.
- Tokens não aparecem em logs, QR permanente ou histórico.

## 4. Keystore e o fallback do HY300

O HY300 testado apresentou falha no Keymaster ao gerar chaves AES:

```text
android.security.KeyStoreException: Unknown error
Failed to generate key -> falling back to software keymaster
```

Sem fallback, o pareamento falhava com `pairing_store_failed`. O app tenta o
Android Keystore e, se a geração falhar, usa uma chave AES-256 aleatória no
sandbox privado do aplicativo. Isso mantém o pareamento funcional nesses
projetores, mas a proteção é menor do que a de um Keystore saudável: um agente
com acesso root ao aparelho poderia ler a chave e o banco. Em aparelhos com
Keystore funcional, a chave não sai do Keystore.

O fallback é transparente e registrado no log técnico. O README e a tela de
Ajustes informam a diferença.

## 5. Transporte

Não há TLS nesta versão. HTTP em texto claro na LAN permite que alguém com
capacidade de sniffing observe o conteúdo e os tokens. A decisão foi tomada
para:

- manter compatibilidade com Android 8.1 e projetores de baixo custo;
- evitar a complexidade de confiar em certificado autoassinado no cliente;
- não depender de internet, CA pública ou conta.

Recomendações:

- usar uma rede Wi-Fi privada e confiável;
- não usar em Wi-Fi público;
- desabilitar AP Isolation apenas se a rede for controlada;
- em cenário de alto risco, usar uma VPN local ou uma rede separada.

TLS local com certificado autoassinado e pinning pelo QR é uma evolução possível,
mas não foi incluído para não comprometer a simplicidade e a compatibilidade.

## 6. Servidor

- Escuta somente no IPv4 Wi-Fi/Ethernet selecionado.
- Porta padrão 8765, com fallback.
- `GET /api/status` e pareamento são públicos por necessidade de descoberta.
- Qualquer operação sobre clipboard exige Bearer.
- O corpo é limitado antes da desserialização.
- Erros internos não devolvem stack trace ao cliente.
- Servidor é encerrado no `ON_STOP`; porta liberada.
- NSD/multicast lock são liberados no encerramento.
- Sem CORS, sem cookies, sem WebView.

## 7. Histórico e privacidade

- Somente local, em Room.
- Limitado a 20 itens.
- Sem analytics, crashes remotos ou telemetria.
- Sem internet: o app não faz requisições externas.
- O conteúdo permanece legível no banco local (não é cifrado em repouso); o
  sandbox do Android é a proteção principal. Isso é adequado ao objetivo, mas
  deve ser considerado em aparelhos compartilhados.
- Limpar o histórico remove os registros imediatamente.

## 8. Limitações residuais

- HTTP sem TLS.
- Sem atestação de dispositivo.
- O primeiro pareamento depende de um canal físico (QR/código).
- AP Isolation pode impedir a comunicação; não é contornado.
- Se o aparelho estiver comprometido/root, o sandbox e o fallback local podem ser lidos.
- Não há proteção contra um atacante que já controla o dispositivo pareado.

## 9. Boas práticas de distribuição

- Gerar uma chave de release própria; a chave de teste incluída em `dist/` não
  deve ser usada para publicação.
- Guardar `keystore.properties` fora do versionamento.
- Revisar as permissões ao publicar.
- Publicar preferencialmente como APK assinado com sua chave e, se usar loja,
  seguir as políticas de privacidade.
