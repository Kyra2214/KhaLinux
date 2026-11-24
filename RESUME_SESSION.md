# RESUME_SESSION — KhaLinux RED/BLUE

- Repositório: `Kyra2214/KhaLinux`.
- Branch de trabalho: `recovery/red-portal-integration`.
- Não alterar `main`, não fazer merge e não fazer force push.
- Roadmap ativo: `docs/roadmap-red-blue-laboratorio.md`.
- Topologia definida: **A** é a central que executa KhaLinux, cria a rede, hospeda o portal e exibe toda a observabilidade; **B** apenas conecta ao Wi-Fi, abre o portal no navegador, faz login de teste e responde ao aviso de cookies.
- RED-00: concluído como inventário documental e inspeção do código RED. Ver `docs/red-baseline-inventory.md`.
- RED-05: parcial. Banner de cookies, escolhas por categoria e endpoint `POST /consent` adicionados; falta completar integração/experiência e documentação final do submódulo.
- RED-07: escopo corrigido para navegador-only. Modo participante nativo, endpoint de token, permissões CAMERA/RECORD_AUDIO e dependências CameraX removidos. Permissões web reais ainda pendentes e dependem de contexto seguro.
- RED-01: próximo submódulo a implementar, conforme ordem do roadmap.
- Progresso provisório: 1 de 16 submódulos RED/BLUE concluído (6,25%). Itens parciais não contam como concluídos.
- Último commit conhecido antes desta atualização de retomada: `a62bbc431cd7342f928f7af327ab0effc2480a86`; conferir o HEAD remoto ao retomar.
- Nenhum teste, build, compilação, lint ou validação foi executado nesta execução. Todos permanecem acumulados para a etapa final.
- Testes físicos nos dois aparelhos permanecem na fase final.
- Não gerar ZIP/checkpoints; o usuário determinou execução diretamente no repositório.
- Pendências técnicas já identificadas: descoberta/correlação confiável da interface AP; limitações de contexto seguro para permissões web; revisão dos rótulos legados no painel; integração final do relatório/BLUE com o evento de consentimento.
