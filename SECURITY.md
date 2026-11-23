# Segurança

## Escopo

Esta política cobre o código, scripts, manifestos, artefatos e documentação do KhaLinux. O projeto é destinado a aprendizado, diagnóstico e auditoria autorizada.

## Regras de segurança

- Downloads devem usar HTTPS e origens verificáveis.
- Arquivos distribuídos devem possuir SHA-256 validado antes do uso.
- Manifestos são versionados e devem declarar arquitetura, versão, origem, tamanho, dependências e limitações.
- Extrações devem impedir path traversal e symlink abuse.
- Entradas do usuário devem ser validadas; comandos não podem ser montados por concatenação insegura.
- Nenhum token, senha, chave privada ou credencial deve ser embutido no APK ou commitado.
- Operações relevantes devem ser registradas sem expor dados sensíveis.
- PRoot não é isolamento forte; não tratar o ambiente como equivalente a VM.
- Laboratórios vulneráveis devem operar em localhost por padrão e somente com escopo autorizado.

## Como reportar uma vulnerabilidade

Não abra uma issue pública para uma vulnerabilidade não corrigida. Envie um relatório privado pelos recursos de segurança do GitHub deste repositório, incluindo:

1. descrição e impacto;
2. versão, commit ou componente afetado;
3. passos mínimos para reprodução;
4. evidência técnica sem dados pessoais;
5. sugestão de mitigação, se disponível.

Responderemos assim que possível, confirmaremos a triagem e coordenaremos correção e divulgação responsável. Não inclua credenciais reais, dados de terceiros ou alvos sem autorização.

## Ferramentas ofensivas

Integrações de pentest somente serão aceitas após validação de compatibilidade, licenciamento, risco e uso em laboratórios controlados. A presença futura de uma ferramenta não autoriza testes contra sistemas de terceiros.

## Bind mounts Android

O runtime expõe somente nós específicos de `/dev` necessários ao shell: `null`, `zero`, `random`, `urandom`, `tty` e `pts`, quando disponíveis no host. Monta também o `/proc` do Android (ADR-0005, revisada: permite `ps`, `top`, `ss` e auditorias; expõe ao Debian a lista de processos visíveis ao app, que no Android moderno se limita aos do próprio app). Não monta `/dev` inteiro nem `/sys`.

O PRoot usa bind mounts de caminho, não uma fronteira de segurança. A opção `-b` não oferece modo read-only; o KhaLinux não acrescenta sufixos não suportados como `:ro`. Os caminhos expostos continuam sujeitos às permissões do UID do aplicativo e não devem ser apresentados como sandbox.

## Auditoria estática

`distribution/scripts/audit-security.py` verifica manifesto, segredos, APIs proibidas, downloads (HTTPS + SHA-256), escopo loopback dos laboratórios e workflows, e roda no CI. Regras, revisão manual e riscos aceitos em [`docs/security-audit.md`](docs/security-audit.md).
