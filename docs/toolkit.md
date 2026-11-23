# Cyber Toolkit (Fase 5)

Camada sobre o gerenciador de pacotes: cada ferramenta é um `ToolDescriptor` (`distribution/toolkit/tools.json`) que referencia um pacote do catálogo e registra comandos, comando de verificação, limitações sem root, documentação (`docs/toolkit/<id>.md`) e se exige confirmação de escopo.

- Código: `android/runtime/.../toolkit/` (`ToolDescriptor`, `ToolkitRegistry`, `ToolkitRegistryParser`, `AuthorizedUse`).
- Regras validadas na construção: ids/comandos únicos, `checkCommand` começa por um comando da ferramenta e não contém metacaracteres de shell, documentação em `docs/toolkit/`, pacote existente no catálogo.
- `verified` só é verdadeiro com `verification_evidence`; nenhuma ferramenta tem evidência ainda.

## Uso autorizado

`ToolkitGuard` bloqueia ferramentas com `requires_authorization` até o usuário descrever o escopo (alvo que possui ou tem permissão explícita). A confirmação fica registrada (`AuthorizationStore`) e expira em 24 h (padrão). O guarda não executa comandos: a camada que lança ferramentas deve consultá-lo (integração prevista nos próximos submódulos). Nunca avaliar terceiros sem autorização.

## Ferramentas atuais (5.1)

curl, dnsutils, iputils-ping, iproute2, openssh-client — todas já presentes no catálogo/RootFS base. Novas ferramentas entram nos submódulos 5.2–5.5 com pacote no catálogo (versão do repositório APT fixado), descritor e documentação.

## Ferramentas adicionadas em 5.2

nmap (exige confirmação de escopo), whois, traceroute. Versões do catálogo lidas em packages.debian.org (bookworm) e ainda não conferidas no snapshot APT fixado.

## Ferramentas adicionadas em 5.3

whatweb, sqlmap e wfuzz (grupo web; todas exigem confirmação de escopo). `nikto` ficou de fora: está em `non-free` no bookworm e o RootFS não habilita esse componente. Wordlists não são distribuídas com o wfuzz. Versões lidas em páginas de pacotes do Debian; conferir no snapshot APT fixado em 5.6.

## Ferramentas adicionadas em 5.4

sleuthkit, binwalk, hashdeep e exiftool (grupo forense; análise local, sem confirmação de escopo). `foremost` não foi incluído por falta de confirmação. Versões lidas em páginas do Debian; conferir no snapshot APT fixado em 5.6.

## Ferramentas adicionadas em 5.5

lynis, chkrootkit e rkhunter (grupo auditoria; `known-limited` por falta de acesso ao kernel; auditam apenas o RootFS). Versões lidas em páginas do Debian; conferir no snapshot APT fixado em 5.6.

## Integração no app (5.6)

- Asset `tools.json` (Gradle `assets.srcDir("../../distribution/toolkit")`) decodificado por `ToolkitJson` (org.json) e validado contra o catálogo.
- `ToolkitService` (runtime): `status()`, `verify(id)` (executa `check_command` no RootFS, exige pacote instalado, grava `verifications.tsv`), `run(id, comando)` (guarda de escopo, comando precisa ser da ferramenta, sem metacaracteres de shell), `acknowledge`/`revoke`.
- App: `KhaLinuxRuntimeService.toolkit()`, `TerminalRuntimeGateway.toolkit()`, `ToolkitViewModel`, `ToolkitScreen` (botão "Ferramentas"). O terminal interativo continua livre: o guarda vale para o lançamento pelo toolkit, não bloqueia o shell.
- Validação da fase: ver `docs/phase5-validation.md`.

## Suposições

- O componente `non-free` não está habilitado no RootFS (suposição; reconferir em 5.6).
- Nenhum comando foi executado em aparelho; limitações são as esperadas pelo ambiente PRoot sem root, não confirmações.
- Decodificação de `tools.json` (org.json) e embarque como asset ficam para 5.6.
- Nenhum teste executado ou criado nesta etapa (política por fase); nada compilado.
