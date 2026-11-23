# Guia do usuário

## Área RED

A aba RED organiza operações práticas em laboratórios autorizados. A Operação 01, **Nunca Conecte à Wi-Fi Pública**, usa dois aparelhos Android, hotspot local controlado pelo sistema e portal de treinamento com dados sintéticos. Leia `docs/red-operation-01.md` antes de iniciar. O portal não coleta credenciais: os campos da demonstração não são enviados, e o servidor registra somente eventos sintéticos. O Android pode impedir a criação do hotspot ou a identificação do endereço local; nesses casos, siga a mensagem apresentada pelo app. Use somente aparelhos próprios ou autorizados.


> **Estado:** descreve o que está implementado em código. Nenhum recurso foi validado em aparelho físico; funcionalidades dependem do resultado do **Diagnóstico**.

**Uso responsável:** use as ferramentas e os laboratórios apenas em sistemas que você possui ou tem autorização explícita para avaliar. Os laboratórios rodam só em `127.0.0.1`.

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
