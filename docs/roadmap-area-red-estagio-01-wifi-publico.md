# RED-01 — Wi-Fi público, portal local e navegação

**Estado:** RED-2 e RED-3 implementados em código; RED-4 é o próximo. Validação final pendente.  
**Branch:** `plan/area-red-wifi-publico`  
**Aparelhos:** dois Android dedicados/controlados pelo instrutor.  
**Servidor:** HTTP local na porta TCP 8080.  
**Validação:** somente após concluir toda a implementação RED, conforme regra do projeto.

---

## 1. Objetivo da operação

Demonstrar, de ponta a ponta, como um hotspot de laboratório e um portal local funcionam, como o segundo aparelho se conecta, como o usuário navega e o que o servidor realmente recebe.

A aula não deve depender de um formulário de login. Deve mostrar também o cenário em que o usuário conecta e navega sem autenticar. O foco inclui links, redirecionamentos e páginas visitadas, pois o risco pode aparecer depois da conexão.

A interface do participante deve parecer um portal Wi-Fi convencional e manter a experiência natural. A identificação de laboratório, os eventos completos e os resultados técnicos ficam no painel do instrutor. Os vídeos mostram o comportamento e os resultados, não código-fonte.

## 2. Dispositivos

### Aparelho A — operador RED
- KhaLinux em modo instrutor.
- Cria o hotspot pelo fluxo suportado pelo Android.
- Inicia o servidor HTTP local na porta 8080.
- Mostra SSID, senha e parâmetros disponibilizados pelo Android.
- Mostra endereço e QR Code para o portal.
- Acompanha conexão, requisições, formulários de teste, links, permissões e erros.
- Encerra hotspot, servidor e sessão.

### Aparelho B — participante
- Android dedicado ao laboratório, sob controle do instrutor.
- Conecta-se à rede pelo seletor Wi-Fi do sistema.
- Abre o portal no navegador.
- Navega, clica nos links e interage com formulários.
- Quando a aula exigir permissões nativas, usa o modo participante Android no próprio aparelho B e autoriza cada solicitação no diálogo real do sistema.

## 3. Topologia e comportamento de rede

1. O operador solicita `LocalOnlyHotspot` ou o fluxo público compatível com a versão Android.
2. O Android decide parâmetros que o aplicativo pode ou não configurar. O KhaLinux mostra somente valores efetivamente fornecidos.
3. O aparelho B conecta-se manualmente.
4. O aparelho A inicia/expõe o servidor local na porta 8080, vinculado à interface de laboratório identificada.
5. O participante abre `http://<IP-local-do-A>:8080`.
6. O QR Code e a digitação manual são alternativas quando o sistema não abre automaticamente o portal.
7. Toda a experiência básica funciona sem internet e sem servidor externo.

Não presumir que um aplicativo comum consiga clonar SSID de estabelecimento, configurar qualquer senha, forçar a abertura do portal ou observar todos os clientes. Se o Android não disponibilizar determinado dado, mostrar “não disponível” em vez de inventá-lo.

## 4. Cenários obrigatórios

### Cenário A — login local
- Portal com identidade fictícia de estabelecimento.
- Página inicial e formulário de acesso local.
- Validação feita exclusivamente pelo servidor do laboratório.
- Campos preenchidos com valores sintéticos de teste.
- O painel RED mostra os valores sintéticos recebidos durante a sessão ativa, o método/rota/status e o resultado da validação.
- Nenhuma conta externa, OAuth ou serviço real.
- Senhas de teste não são persistidas no histórico ou relatório.

### Cenário B — sem login
- Portal de boas-vindas, aceite de termos ou acesso direto.
- O participante consegue navegar sem preencher credenciais.
- Links e páginas locais demonstram que a interação após a conexão também importa.
- O painel mostra a sequência de navegação, os destinos locais, os redirecionamentos e os eventos observados.

### Cenário C — links e redirecionamentos
- Páginas locais interligadas.
- Links com destinos distintos e contexto de origem.
- Registro do clique, rota solicitada, horário, status HTTP e redirecionamento realizado.
- Demonstração de link inesperado e página informativa.
- Não usar domínios externos nem páginas que imitem serviços reais.

### Cenário D — permissões e recursos do aparelho
A página HTTP local não recebe poderes nativos do Android. Em particular, câmera, microfone e geolocalização pelo navegador dependem de contexto seguro; HTTP em IP de LAN normalmente não é contexto seguro. Login ou conexão Wi-Fi não concede permissões.

Implementar uma trilha web e uma trilha nativa:
- Portal web: seletor de arquivos do navegador com escolha explícita, sem envio do conteúdo por padrão.
- Modo participante Android no aparelho B: solicitações reais de câmera, microfone e localização, acionadas no próprio aparelho e apenas para funções demonstrativas.
- Câmera/microfone: prévia local com controles de início/parada; sem transmissão contínua ao aparelho A.
- Localização: apresentada no aparelho B; o aparelho A recebe apenas o estado da permissão e o evento de conclusão, não coordenadas precisas.
- Arquivos: seleção pelo seletor do Android; não enumerar diretórios nem ler arquivos sem escolha. O conteúdo não é enviado ao A por padrão.
- Painel registra solicitado, concedido, negado, cancelado, revogado e executado, distinguindo cada estado.

## 5. O que o painel RED precisa mostrar

- Hotspot: iniciando, ativo, indisponível, encerrando.
- SSID, senha e banda quando o Android fornecer.
- Estado de conexão do participante e dados de rede realmente disponíveis.
- Endereço/porta do portal e estado do servidor.
- Requisições: timestamp, método, rota, status, tipo e tamanho.
- Cabeçalhos de baixo risco permitidos por allowlist.
- Campos sintéticos recebidos em memória durante a sessão ativa.
- Resultado da validação local.
- Sequência de páginas, cliques e redirecionamentos.
- Estado das permissões e função executada no aparelho B.
- Erros e limitações observados.
- Separação visual entre solicitado, autorizado, executado e recebido.

O painel não pode alegar que recebeu senha, cookie, token, arquivo, localização ou conteúdo de câmera se isso não foi efetivamente transmitido e autorizado. Não coletar credenciais reais, tokens, cookies, conteúdo privado de outros aplicativos ou tráfego de terceiros. Campos não previstos devem ser rejeitados e descartados.

## 6. Diretriz de apresentação

- Portal do participante natural, sem banners grotescos ou alertas artificiais repetidos.
- Identificação do laboratório na preparação da aula e no painel do instrutor.
- Diálogos de permissão devem ser os verdadeiros diálogos do Android/navegador.
- Nenhum diálogo de sistema falso ou imitado.
- Vídeos mostram criação da rede, conexão, portal, navegação, permissões e o que foi recebido.
- Código-fonte e scripts internos não fazem parte da apresentação.

## 7. Estado de implementação em 2026-11-23\n\n- RED-1: contratos de estado/eventos e catálogo revisados.\n- RED-2: hotspot Android integrado; SSID/senha e dados de interface exibidos quando disponíveis; participante passa a ser considerado ativo após requisição real. A API Android não fornece lista universal de clientes.\n- RED-3: servidor HTTP na porta 8080, bind por IPv4 de interface Wi-Fi/AP, limites de requisição, timeout e telemetria HTTP conectados ao painel.\n- RED-4: portal natural, autenticação local e acesso direto sem login ainda pendentes.\n- Nenhum teste/build executado; validação reservada à fase final.\n\n## 8. Roadmap por fases

### 01.1 — Revisar modelos e estados
- Atualizar modelos de cenário, dispositivo, requisição e evento.
- Estados separados para hotspot, servidor, participante e sessão.
- Identificador de cenário compartilhável com BLUE.
- Campos desconhecidos e indisponíveis representados corretamente.

### 01.2 — Hotspot e dados de rede
- Iniciar/parar hotspot pelo Android.
- Mostrar SSID/senha/banda fornecidos pelo sistema.
- Mostrar instruções para conexão do segundo aparelho.
- Tratar perda de rede, cancelamento e restrições do fabricante.
- Não executar varredura de dispositivos.

### 01.3 — Servidor HTTP 8080
- Porta padrão 8080.
- Bind apenas na interface de laboratório identificada.
- Rotas allowlist, limites de corpo, timeout e limite de conexões.
- Encerrar o socket junto com a sessão.
- Mostrar endereço e QR Code.
- Registrar status e metadados das requisições.

### 01.4 — Portal e experiência do participante
- Layout natural de portal Wi-Fi fictício.
- Páginas de boas-vindas, termos, login local e conclusão.
- Modo com autenticação local.
- Modo sem login e acesso direto.
- Navegação local e páginas de demonstração.
- Não imitar marca/serviço real.

### 01.5 — Recepção de formulário sintético
- Campos de teste claramente definidos no material do instrutor.
- Requisição HTTP real para o servidor local.
- Validação local e resposta real.
- Mostrar campos sintéticos recebidos no painel durante a sessão.
- Descartar campos desconhecidos e dados sensíveis.
- Não persistir valores de senha.

### 01.6 — Links, navegação e redirecionamento
- Registrar sequência de navegação.
- Mostrar origem, destino local, método, horário e status.
- Demonstrar acesso direto sem login.
- Demonstrar links inesperados e redirecionamentos internos.
- Exibir ao participante a URL real do destino no navegador.

### 01.7 — Modo participante Android
- Definir componente Android instalável/ativável no aparelho B.
- Solicitar permissões no aparelho B, não remotamente pelo A.
- Implementar demonstrações de câmera, microfone e localização com prévia/resultado local.
- Implementar seletor Android de arquivos.
- Transmitir ao A somente estados/eventos mínimos da demonstração.
- Cobrir concessão, recusa, cancelamento e revogação.
- Explicar limitações de contexto seguro do navegador.

### 01.8 — Painel de eventos e evidências
- Linha do tempo em tempo real.
- Detalhes da requisição recebida.
- Valores sintéticos temporários.
- Estado de conexão e permissões.
- Erros, bloqueios e ações não disponíveis.
- Nunca converter falha em sucesso.

### 01.9 — Histórico e relatório
- Relatório local sem segredos ou dados pessoais desnecessários.
- Exportação manual.
- Limpeza de dados temporários.
- Histórico limitado e exclusão manual.
- Sessão interrompida não reabre hotspot/servidor automaticamente.

### 01.10 — Integração BLUE
- Link para aula BLUE correspondente.
- Mesma identificação de cenário e sequência de evidências.
- BLUE explica login local, acesso direto, links, HTTP/HTTPS, permissões e resposta defensiva.
- BLUE não executa a operação RED nem altera o dispositivo participante.

### 01.11 — Tutorial e documentação
- Tutorial dentro do app para o instrutor.
- Roteiro do aparelho B.
- Requisitos, limitações e solução de problemas.
- Conteúdo de aula sem código-fonte.
- Atualizar arquitetura, guia, changelog, roadmap e retomada.

### 01.12 — Fechamento da implementação
- Revisar integração e consistência.
- Consolidar backlog de validação.
- Gerar checkpoint completo.
- Não executar testes/builds.
- Não fazer merge para main sem autorização.

### 01.13 — Validação final
Somente após todas as fases anteriores:
- Testes unitários/integrados.
- Build Android.
- Testes do servidor e limites.
- Testes de hotspot e ciclo de vida.
- Testes de formulário, navegação e eventos.
- Testes de permissões reais no aparelho B.
- Teste físico com os dois aparelhos.
- Regressão nas demais áreas.

## 8. Critérios de conclusão

- Dois aparelhos conectam e participam do cenário.
- Portal local acessível na porta 8080.
- Cenário com login local e cenário sem login funcionam.
- Requisições reais chegam ao servidor e aparecem no painel.
- Valores sintéticos aparecem na sessão ativa, sem persistência de segredos.
- Links e redirecionamentos locais geram eventos.
- Permissões reais são solicitadas no aparelho B e o resultado é registrado corretamente.
- Interface do participante é natural.
- Relatório e histórico respeitam minimização de dados.
- Tutorial permite repetir a aula.
- Limitações técnicas estão documentadas.
- Validação final é concluída separadamente.

## 9. Estado atual conhecido

A primeira versão de código possui hotspot Android, servidor HTTP local, QR Code, sessões e painel inicial. O formulário atual envia apenas um evento fixo e não transmite campos; permissões são apenas demonstrativas. Portanto, o fluxo completo especificado acima ainda não está implementado nem validado.

## 10. Processo de desenvolvimento

- Implementar em sequência, sem pedir autorização entre subfases.
- Não executar testes, builds, verificações ou testes físicos durante a implementação.
- Anotar tudo para validação final.
- Não fazer merge em `main` sem autorização explícita.
