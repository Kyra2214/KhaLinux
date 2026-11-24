# Regras de desenvolvimento do KhaLinux — laboratório RED/BLUE

## Topologia fixa

- O laboratório usa exclusivamente dois aparelhos Android pertencentes ao usuário e dedicados aos testes.
- **Aparelho A:** operador e central do laboratório. Executa o KhaLinux, cria a rede, hospeda o portal, controla a sessão e exibe todo o painel RED/BLUE, eventos, permissões observáveis e relatórios.
- **Aparelho B:** participante passivo. Apenas conecta-se ao Wi-Fi do laboratório, abre o endereço no navegador, preenche o formulário de teste e responde às interfaces e solicitações legítimas apresentadas pelo navegador. Não instala nem executa o KhaLinux, não abre ferramentas e não opera painéis.
- Não transferir para B funções de operador, configuração, monitoramento ou auditoria.

## Fidelidade técnica

- Implementar comportamento real do Android, navegador e rede. Não inventar clientes, conexões, permissões, resultados ou eventos.
- Distinguir dados observados, escolhas do participante, configurações do operador e informações indisponíveis.
- O painel A exibe somente fatos efetivamente recebidos pelo servidor ou APIs locais disponíveis.
- Não armazenar senhas, tokens, cookies, cabeçalhos de autenticação, conteúdo de arquivos, áudio, vídeo ou coordenadas.
- Formulários de laboratório usam dados sintéticos. O formulário Home Security pode validar e-mail e senha somente como demonstração local; deve descartar a senha, exibir apenas e-mail mascarado no painel e nunca autenticar contas reais. Nunca solicitar credenciais de serviços externos. Facebook e Instagram, neste escopo, são somente exemplos visuais.
- O aviso de cookies registra escolhas reais por categoria. “Aceitar todos” vale para as categorias explicitamente descritas; não concede permissões do Android ou do navegador.
- Solicitações de câmera, microfone e localização do navegador devem ser visíveis, separadas e iniciadas por ação claramente identificada. Não mascarar a finalidade nem simular concessão.
- APIs de permissões do navegador dependem de contexto seguro e política do navegador. Em HTTP por IP local, registrar indisponibilidade quando a API não puder ser usada; não contornar essa restrição.

## Recursos Android e navegador

- Usar APIs públicas e os controles normais do sistema.
- Não atribuir ao portal web capacidade de conceder permissões nativas de outros aplicativos.
- Não contornar avisos, políticas de origem segura, controles do usuário ou proteções do Android.
- Registrar solicitado, concedido, negado, cancelado, bloqueado e indisponível como estados distintos.

## Implementação e validação

- Preservar código existente e integrar mudanças incrementalmente.
- Não declarar funcionalidade concluída sem implementação e documentação correspondentes.
- Durante a implementação, não executar testes, builds, compilação, auditorias de validação ou testes físicos. Acumular essas atividades para a fase final do roadmap.
- Testes físicos nos dois aparelhos ficam reservados à etapa final.
- Trabalhar somente na branch de recuperação definida pelo usuário. Não alterar `main`, não fazer force push e não fazer merge sem instrução explícita.
