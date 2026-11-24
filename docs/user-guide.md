# Guia do usuário

## Área RED

A aba RED organiza operações práticas em laboratório controlado. A Operação 01 usa dois aparelhos Android: o aparelho A cria o hotspot pelo Android e hospeda um portal HTTP local na porta 8080; o aparelho B conecta-se e navega. Há dois cenários, login local com campos exclusivamente sintéticos e acesso direto sem login. No cenário de login, o formulário envia um POST real ao servidor local, que valida e mostra os campos de teste durante a sessão; esses valores não são gravados no histórico nem no relatório. O aparelho B permanece somente no navegador. A página de permissões apresenta solicitações web separadas, quando a origem e o navegador permitem. Leia `docs/red-operation-01.md` e `docs/red-instructor-guide.md` antes da aula. O Android pode restringir hotspot, endereços e permissões conforme versão/fabricante.


> **Estado:** descreve o que está implementado em código. Nenhum recurso foi validado em aparelho físico; funcionalidades dependem do resultado do **Diagnóstico**.

**Uso responsável:** use as ferramentas e os laboratórios apenas em sistemas que você possui ou tem autorização explícita para avaliar. A Operação RED-01 vincula o portal ao IPv4 da interface do hotspot de laboratório; os demais laboratórios mantêm seu próprio escopo local.

## Terminal

- **Abrir shell** inicia uma sessão Bash no Debian ARM64. Digite no campo "Comando Bash" ou diretamente na sessão.
- **Teclas extras:** `Ctrl` e `Alt` (toque para armar; valem para o próximo caractere) e teclas de navegação/controle (ESC, Tab, ↑ ↓ ← →, Home, End, Ctrl+C, Ctrl+D).
- **Linhas/Colunas:** ajuste `−`/`+` (e `− 8`/`+ 8` nas colunas) para o tamanho do terminal.
- **Tema do terminal:** 5 temas de cores (`docs/customization.md`).
- **Histórico recente:** os últimos comandos; toque para restaurar no campo de entrada.
- **Favoritos:** **★ Favoritar comando** guarda o comando atual; toque no favorito para reutilizá-lo; ✕ remove.
- **Abas:** **+ Aba** (até 4 sessões simultâneas), troque tocando na aba, renomeie e feche com **Fechar aba** (`docs/sessions.md`).
- **Salvar saída em nota:** salva a saída atual em Markdown em `/home/khalinux/reports`.
- A sessão continua com o app em segundo plano por meio de uma notificação persistente; **Encerrar sessões** na notificação as finaliza.

## Scripts

**+ Script** cria um script em `/home/khalinux/scripts` (nome como `ola.sh`). Toque no nome para executar na aba ativa; ✎ edita e ✕ apaga (`docs/scripts.md`).

## Arquivos

**Arquivos** navega somente em `/home/khalinux`: ↑ Subir, + Arquivo, + Pasta, Renomear, editar texto, Apagar (com confirmação). Mostra o espaço usado e **Limpar resíduos de instalação** (`docs/file-manager.md`, `docs/storage.md`). **Gerar relatório de progresso** cria um relatório Markdown em `reports/` (`docs/reports.md`).

## Pacotes

**Pacotes** lista o catálogo com pesquisa e categorias. Instalar/remover/atualizar usa APT no RootFS (versão exata) ou arquivo com SHA-256 verificado, resolve dependências e registra histórico auditável. Cada pacote mostra a validação: "Não verificado em ARM64/PRoot", "Limitações conhecidas" ou "Verificado". Nenhuma compatibilidade é presumida (`docs/package-manager.md`).

## Ferramentas (Cyber Toolkit)

**Ferramentas** agrupa reconhecimento/rede, Web, forense e auditoria. Para cada uma: **Verificar** (executa checagem no aparelho e registra o resultado), **Usar no terminal** e **★ Favoritar**. Ferramentas com alvo externo exigem **Confirmar escopo** (descreva o alvo autorizado; vale 24 h; **Revogar escopo** cancela). Documentação por ferramenta em `docs/toolkit/` (`docs/toolkit.md`).

## Laboratórios

**Laboratórios** traz 12 laboratórios (Linux, rede, Web, forense), 51 exercícios, com trilhas e pré-requisitos:

1. Abra o laboratório e leia o enunciado. Se houver **serviço**, inicie-o num terminal e deixe-o rodando (Ctrl+C encerra).
2. Responda e toque em **Enviar**. As respostas são conferidas por hash; não há gabarito no app.
3. **Dica (−N pts)** custa pontos; erros além de 3 também reduzem a pontuação (piso de 25%).
4. **Reiniciar laboratório** ou **Apagar progresso** zera o estado.

Detalhes em `docs/labs.md` e `docs/labs/<id>.md`.

## Tutor

**Tutor** é um assistente **offline baseado em regras**: não é IA generativa, não usa a internet e só responde o que está documentado. Dentro de um exercício, **Perguntar ao tutor** dá contexto sem entregar a resposta (`docs/tutor.md`).

## Diagnóstico

**Diagnóstico** verifica PRoot, shell, DNS, rede e PTY, e permite confirmar rotação e segundo plano manualmente. **Copiar relatório** gera o texto para reportar problemas (`docs/diagnostics.md`).

## Privacidade e segurança

- Tudo roda localmente; o app só usa a internet para baixar RootFS/PRoot e pacotes, e para o que você executar no terminal.
- Sem contas, sem telemetria, sem credenciais embutidas. `allowBackup=false`.
- Para relatar vulnerabilidades, veja `SECURITY.md`.


## Área BLUE — análise defensiva

A Área BLUE fica no menu **Mais**. Ela apresenta a perspectiva defensiva das sessões RED: requisições observadas, eventos de navegação, permissões solicitadas e resultados. Durante uma sessão RED ativa, alternar para BLUE preserva a execução para permitir observar o mesmo cenário pelos dois lados. Volte ao RED para continuar a operação.

A análise BLUE distingue evidências observadas de recomendações. Não representa captura geral de tráfego nem inspeção de conteúdo HTTPS. Consulte [docs/blue-area.md](blue-area.md).
\nPara o roteiro detalhado da aula RED-01, consulte [docs/red-instructor-guide.md](red-instructor-guide.md).\n