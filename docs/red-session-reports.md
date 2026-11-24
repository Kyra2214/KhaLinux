# RED-09 — Histórico, relatório e exportação

**Estado:** implementação em código/documentação concluída; não validada. Testes de migração, serialização, sanitização, exportação e exclusão ficam para a etapa final.

## Schema e migração

- Cada registro de sessão contém `schemaVersion`; a versão atual é 2.
- Registros legados sem versão são interpretados como schema 1.
- Migração 1 → 2 preserva os campos existentes e normaliza `completedSteps` e `events` ausentes para coleções vazias.
- Registros migrados são regravados no formato atual quando lidos.
- Versões futuras desconhecidas não são interpretadas como schema atual.

## Persistência e retenção

- Persistência local em SharedPreferences privadas do aplicativo.
- Limite de 100 sessões e 300 eventos por sessão.
- Campos persistidos: identificador/horários/estado/modo/etapas e metadados tipados dos eventos.
- Requisições guardam método, rota, status, bytes, horário, endereço remoto e somente cabeçalhos permitidos.
- Consentimento guarda a escolha e os estados por categoria; permissões web guardam recurso e estado.
- Não são persistidos corpos HTTP, campos de formulário, códigos de acesso, cookies, tokens de sessão, mídia, coordenadas ou conteúdo de arquivos.
- Valores sintéticos de formulário permanecem somente em memória durante a sessão ativa e são removidos ao encerrar/limpar o painel.

## Relatório

O relatório Markdown inclui:
- versão do KhaLinux e versão/API do Android;
- ID da operação/sessão, início/fim, estado e modo;
- contagens derivadas de eventos, escolhas de cookies e estados de permissões;
- linha do tempo com metadados e cabeçalhos permitidos;
- limitações do HTTP sem TLS, contexto seguro para permissões web e validação ainda pendente.

O relatório é gerado sob demanda para copiar ou exportar pelo seletor de documentos do Android. A exclusão da sessão remove o registro e a fonte local do relatório; arquivos já exportados permanecem no destino escolhido pelo usuário.

## Exclusão

- A lista de histórico permite excluir uma sessão individual com confirmação.
- A exclusão de histórico completo permanece disponível.
- Uma sessão ainda ativa não pode ser excluída individualmente; deve ser encerrada primeiro.
- A retenção automática remove as sessões mais antigas acima do limite de 100.

## Validação acumulada

- Migração schema 1 → 2 e leitura de sessões antigas.
- Serialização/leitura dos campos opcionais e eventos tipados.
- Sanitização: ausência de corpo, formulário, cookie, token, mídia, coordenadas e arquivo.
- Exportação Markdown e exclusão individual/global.
- Sessões concluídas, interrompidas e relatórios parciais.
- Confirmar limites de 100 sessões/300 eventos.

Nenhum teste, build ou compilação foi executado durante a implementação.
