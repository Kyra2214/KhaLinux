# Área BLUE — análise defensiva do laboratório RED

**Estado:** primeira integração implementada em código; validação final pendente.  
**Branch:** plan/area-red-wifi-publico  
**Cenário compartilhado:** red-wifi-public-01

## Objetivo

A Área BLUE apresenta a perspectiva defensiva do mesmo laboratório executado pelo RED. Ela não repete a execução ofensiva: lê as sessões, requisições e eventos registrados pelo RED e explica o que um participante deve observar e como responder.

## Evidências compartilhadas

- Sessões e eventos persistidos são lidos de RedSessionStore.
- Durante uma sessão ativa, RedLiveEvidenceStore compartilha temporariamente requisições, campos sintéticos aceitos e eventos atuais.
- Os valores do formulário são sintéticos e existem somente em memória; ao encerrar/parar a sessão, são removidos.
- BLUE não recebe áudio, vídeo, coordenadas nem conteúdo de arquivos.
- Eventos não observados não são apresentados como detecções.

## Perspectiva defensiva

- Hotspot: confirmar a rede com o estabelecimento e desconfiar de nomes semelhantes.
- Portal: a presença de uma página de login não prova legitimidade.
- Login local: não reutilizar senhas pessoais em portais Wi-Fi.
- Acesso direto: ausência de login não significa ausência de risco.
- Links: verificar URL, domínio e destino após redirecionamentos.
- HTTP: conteúdo não é protegido por TLS; serviços reais devem usar HTTPS válido.
- Permissões: avaliar necessidade e contexto de cada pedido; recusar quando não fizer sentido.
- Câmera/microfone: observar indicadores do sistema e encerrar uso após a demonstração.
- Localização: conceder somente quando necessária; revisar permissões posteriormente.
- Arquivos: usar seletor do sistema e conferir o arquivo/destino antes de compartilhar.
- Resposta: desconectar, esquecer rede suspeita, revisar permissões e conferir URLs.

## Navegação e ciclo de sessão

- BLUE está disponível no menu Mais.
- Ao alternar RED → BLUE, a sessão RED ativa permanece em execução para permitir observação paralela.
- Ao retornar BLUE → RED, a sessão é reaberta sem ser marcada como interrompida.
- Sair do laboratório para outra seção encerra a sessão RED segundo o fluxo atual.

## Limitações

- A linha do tempo reflete eventos do laboratório, não uma captura geral de tráfego.
- O painel não inspeciona conexões externas ou conteúdo HTTPS.
- Dados efêmeros de formulários não permanecem disponíveis após limpeza ou encerramento.
- A compatibilidade e a experiência em aparelho real serão verificadas somente na validação final.
