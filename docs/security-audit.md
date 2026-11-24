# Auditoria de segurança estática (9.1)

**Estado:** implementada em código e por revisão manual. A execução do script `audit-security.py` pertence à fase final de validação, após 100% da implementação do roadmap.

## O que foi entregue

- `distribution/scripts/audit-security.py`: auditoria estática, só biblioteca padrão, sem rede, sem escrita. Saída em texto ou `--json`; código de saída 1 se houver achado `ERRO`. Passa a rodar no CI (job `host-validation`).
- Endurecimento do manifesto: `android:usesCleartextTraffic="false"` explícito (com `targetSdk 35` já era o padrão; agora é auditável).
- Esta revisão manual do código de superfície crítica.

## Regras do script

| Grupo | Regra | Nível |
|---|---|---|
| Manifesto | `allowBackup=false`; sem `debuggable`; `usesCleartextTraffic=false`; permissões só da lista aprovada; só `.MainActivity` exportada; `exported` explícito em todo componente | ERRO |
| Segredos | sem `.jks/.keystore/.p12/.pem/.env/local.properties/keystore.properties`; padrões de chave privada, tokens GitHub/AWS/Google/Slack e atribuições `senha=`/`password=`/`storePassword=` com literal | ERRO |
| Kotlin/Java (`src/main`) | sem `addJavascriptInterface`, `setJavaScriptEnabled`, `MODE_WORLD_*`, `X509TrustManager`/`HostnameVerifier`, `printStackTrace`, `Runtime.exec`, `Log.*`; `SSLContext.getInstance` é permitido somente em `RedLocalTlsManager.kt` para contexto TLS de servidor com `X509ExtendedKeyManager`, trust managers padrão e sem `HostnameVerifier`; `ProcessBuilder` só em `ProotLauncher.kt` e `RuntimeDiagnostics.kt` | ERRO |
| Kotlin/Java | literal `http://` | AVISO |
| Downloads | manifestos de release com `*_url` HTTPS e `*sha256` de 64 hex; fonte por URL no catálogo exige HTTPS e SHA-256 | ERRO |
| Laboratórios | fixtures sem `0.0.0.0`/`INADDR_ANY`; serviços do `labs.json` só em loopback | ERRO |
| Shell/CI | `pipefail` (AVISO); `curl \| sh`; `pull_request_target`; workflow sem `permissions`; ação com `@main/@master/@latest` | ERRO |

Permissões aprovadas: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC` (sessão interativa e instalação), `POST_NOTIFICATIONS` (notificação do foreground service), `INTERNET` (download de RootFS/PRoot e rede dentro do convidado), `ACCESS_WIFI_STATE`/`CHANGE_WIFI_STATE` (LocalOnlyHotspot e leitura do estado Wi-Fi), `ACCESS_NETWORK_STATE`/`CHANGE_NETWORK_STATE` (estado e ciclo de rede do laboratório), `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` (requisitos de versões Android e demonstração de localização solicitada no aparelho participante), `CAMERA` (prévia local CameraX) e `RECORD_AUDIO` (medidor local de nível de áudio, sem gravação). As permissões de câmera, microfone e localização são solicitadas no próprio aparelho participante, no momento da ação; conteúdo de mídia e coordenadas não são transmitidos ao operador.

## Revisão manual (leitura de código, sem execução)

| Área | Arquivo | Conclusão |
|---|---|---|
| Download | `GitHubHttpsConnection` | HTTPS obrigatório, redirecionamento manual (máx. 5), **lista de hosts verificada a cada salto** (`github.com`, `release-assets.githubusercontent.com`, `objects.githubusercontent.com`), redirecionamento para não HTTPS recusado. |
| Integridade | `RootfsInstaller`, `ProotInstaller` | SHA-256 de 64 hex validado antes do uso; hashes pinados no app. |
| Extração TAR | `RootfsInstaller.resolveEntry/resolveLink` | Recusa caminho absoluto, `..`, link simbólico que escapa da raiz e pai simbólico. |
| PRoot | `ProotInstaller` | Aceita um único ELF `proot` regular, sem link; cabeçalhos ELF validados. |
| Binds | `ProotBindMountPreparer`, `AndroidBindMountPolicy` | Alvos canônicos dentro do RootFS, sem links simbólicos; nós de `/dev` necessários e `/proc` (ADR-0005 revisada); `/sys` não montado. |
| Processos | `ProotLauncher` | `ProcessBuilder` com lista de argumentos (sem shell do usuário); ambiente limpo e explícito; script do wrapper de grupo de processos é constante, com argumentos posicionais. |
| Comandos apt | `PackageInstaller`, `PackageRemover` | Nome (`[a-z0-9][a-z0-9+.-]+`) e versão (`[0-9][A-Za-z0-9.+~:_-]*`) validados por regex completa no manifesto; sem aspas, `;`, `$` ou espaço possíveis, logo o `'…'` do comando não é escapável. |
| Arquivos | `HomeFileManager` | Caminho canônico confinado à raiz; recusa `.`, `..`, vazio e NUL. |
| Scripts | `ScriptStore` | Nomes `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`. |
| Toolkit | `ToolkitGuard`, `AuthorizedUse` | Ferramentas com alvo externo exigem confirmação de escopo registrada; escopo sanitizado e truncado. |
| Laboratórios | fixtures e `labs.json` | Serviços só em `127.0.0.1`; vulnerabilidades propositais, marcadas. Respostas só como SHA-256. |
| Segredos | todo o projeto | Nenhuma credencial encontrada na leitura; `.gitignore` bloqueia `*.keystore`, `*.jks` e `.env*`. |

## Riscos aceitos e limitações (não são defeitos corrigíveis aqui)

1. **PRoot não é sandbox.** Um programa no convidado roda com o UID do app e acessa os dados privados do app. Documentado em `SECURITY.md` e ADR-0002.
2. **APT usa `http://deb.debian.org`** dentro do convidado (padrão do Debian; a integridade vem das assinaturas do `Release`). A diagnóstica faz `curl` para essa URL dentro do convidado; não é tráfego do app (por isso o AVISO do script, não ERRO).
3. **`binwalk`** está no catálogo com limitação registrada (extração automática de arquivos não confiáveis).
4. **Ferramentas ofensivas** só abaixo de confirmação de escopo; não há bloqueio técnico de rede dentro do PRoot (o convidado tem a rede do app). A proteção é de processo (gate) e de documentação.
5. **Sem pinning de certificado**; confia-se na PKI do sistema e nos hashes pinados.
6. **Sem verificação de assinatura do APK em tempo de execução**; a integridade do app é a da instalação Android.

## Pendências transferidas para a validação final

- Executar `python3 distribution/scripts/audit-security.py` e corrigir ERROs/AVISOS (o script nunca foi executado; pode haver falso positivo ou erro de sintaxe).
- Revisão manual não cobriu `NativePty`/`native_pty.c` linha a linha para erros de memória; fazer revisão específica e, no CI, compilar com `-fsanitize` se possível.
- Verificar em aparelho o que `Log.*`/notificação expõem (nenhum dado sensível previsto).
