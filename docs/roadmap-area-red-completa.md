# Área RED — Roadmap mestre completo

**Estado deste documento:** roadmap revisado conforme as decisões de produto de 2026-11-23.  
**Branch:** `plan/area-red-wifi-publico`  
**Repositório:** `Kyra2214/KhaLinux`  
**Escopo:** protótipo pessoal de laboratório, com dois aparelhos Android controlados pelo instrutor.  
**Progresso de implementação:** RED-0 a RED-7 concluídos em documentação/código (8 de 14 submódulos, 57%); RED-8 é o próximo. A execução continua na mesma branch, sem ZIP e sem validações antecipadas. Validação permanece pendente e será feita somente ao final.  
**Validação:** reservada para depois de concluir todo o roadmap. Nenhum teste/build será executado durante a implementação.

---

## 0.1. Registro de execução contínua

| Submódulo | Estado | Registro |
|---|---|---|
| RED-0 — escopo e arquitetura | Concluído | contratos, topologia, retenção e limites registrados |
| RED-1 — fundação e catálogo | Concluído em código | modelos de estado/evento, observações HTTP, sequência persistida e catálogo da Operação 01 atualizados |
| RED-2 — hotspot e conexão | Concluído em código | LocalOnlyHotspot, dados expostos pelo Android e atividade do participante inferida por requisição real; Android não expõe lista universal de clientes |
| RED-3 a RED-13 | Pendente | executar em sequência após RED-2 |

**Percentual de implementação do roadmap RED:** 57% (8/14 submódulos).  
**Validação:** não executada por instrução; todos os testes e builds permanecem acumulados para RED-13.  
**Checkpoint ZIP:** desativado por instrução explícita do usuário; trabalhar somente na branch Git indicada.

## 1. Direção do produto

A Área RED será o ambiente de preparação e execução das demonstrações práticas. A Área BLUE será o ambiente de observação defensiva e resposta ao mesmo tipo de cenário. RED e BLUE devem compartilhar conceitos, identificadores de cenário e evidências compatíveis, mas manter roteiros e objetivos didáticos próprios.

O KhaLinux nesta etapa é um protótipo pessoal, não uma plataforma pública de distribuição. O objetivo é demonstrar o comportamento real de uma rede Wi-Fi de laboratório e de um aparelho participante, sem expor código-fonte, scripts internos ou detalhes de implementação nas aulas e vídeos.

**O que será mostrado nas aulas:** criação da rede, conexão do segundo aparelho, portal apresentado, navegação, solicitações legítimas de permissões, eventos e informações efetivamente recebidas pelo servidor local.

**O que fica nos bastidores:** código, scripts, implementação interna e mecanismos de orquestração. A interface do participante deve ser natural e coerente com um portal Wi-Fi comum. A identificação do laboratório, o estado real da sessão e a telemetria completa ficam no painel do instrutor. Não usar avisos gigantes ou elementos artificiais que destruam a experiência; não falsificar diálogos do Android nem afirmar que algo aconteceu quando não aconteceu.

## 2. Objetivos de aprendizagem

Ao concluir as aulas correspondentes, o aluno deverá compreender:

1. Como um hotspot de laboratório é criado e quais parâmetros o Android realmente permite controlar.
2. Como um segundo aparelho encontra e conecta-se à rede.
3. Como funciona um portal local de acesso Wi-Fi, inclusive quando há apenas aceite de termos ou acesso direto, sem login.
4. Como um portal com autenticação local funciona sem autenticar em serviços externos.
5. Como links, redirecionamentos e páginas visitadas podem influenciar o comportamento do usuário.
6. Quais dados uma requisição HTTP local efetivamente entrega ao servidor e quais dados não ficam disponíveis apenas por estar na mesma rede.
7. A diferença entre HTTP e HTTPS, incluindo o que a ausência de TLS permite observar no próprio laboratório.
8. Como funcionam as permissões reais do Android e do navegador, seus limites e o que cada autorização efetivamente permite.
9. Como identificar o que foi solicitado, autorizado, negado, acessado e recebido.
10. Como analisar o mesmo cenário pela perspectiva defensiva da Área BLUE.

## 3. Topologia do laboratório

### Aparelho A — operador / instrutor RED

- Executa o KhaLinux em modo operador.
- Solicita ao Android a criação do hotspot usando APIs públicas suportadas.
- Mostra SSID, senha, banda, estado e informações que o sistema disponibilizar.
- Hospeda o portal local na porta TCP **8080**.
- Exibe endereço local, QR Code e instrução de conexão.
- Apresenta painel de eventos, requisições e resultados recebidos.
- Controla início, encerramento, limpeza e exportação do relatório.

### Aparelho B — participante / dispositivo de teste

- É um segundo aparelho Android dedicado ao laboratório e sob controle do instrutor.
- Conecta-se manualmente à rede criada pelo aparelho A.
- Abre o portal pelo navegador ou pelo fluxo de abertura assistida suportado pelo Android.
- Navega por páginas, links, formulários e cenários de acesso direto.
- Permite observar permissões e seleções reais no próprio aparelho, sempre por mecanismos legítimos do Android.
- Pode ser inspecionado antes e depois da aula para demonstrar configurações, permissões e efeitos observáveis.

### Rede e servidor

- Comunicação restrita ao laboratório local.
- Portal HTTP em `http://<endereço-local-do-aparelho-A>:8080`.
- Nenhum serviço externo é necessário para a aula básica.
- Não fazer encaminhamento de porta, varredura de terceiros, interceptação de redes alheias ou envio automático para a internet.
- Mostrar claramente o protocolo usado. HTTP na porta 8080 é HTTP sem TLS, não “HTTPS falso”.
- O painel deve distinguir endereço/porta, método, rota, horário, status HTTP, tamanho do corpo e campos de demonstração recebidos.

## 4. Experiências da Operação 01

### 4.1 Dois modos de acesso

**Modo A — portal com autenticação local**
- Página de boas-vindas de aparência natural, com identidade fictícia de estabelecimento.
- Formulário de acesso do próprio laboratório.
- Validação local, sem Google, Facebook, Instagram, OAuth ou qualquer serviço externo.
- Dados de teste claramente definidos para a aula.
- O painel RED mostra os campos sintéticos recebidos na sessão ativa, o resultado da validação e a requisição correspondente.
- Não aceitar nem armazenar credenciais pessoais/reais. Não persistir senhas de teste no histórico permanente ou relatório.

**Modo B — acesso direto, sem login**
- Página inicial simples, aceite de termos ou abertura direta.
- Navegação por páginas e links locais.
- Demonstra que o login pode nem existir e que o risco também pode estar em links, redirecionamentos, páginas visitadas e decisões do usuário.
- Painel mostra quais rotas locais foram abertas e quais eventos foram gerados.

### 4.2 Navegação, links e redirecionamentos

- Criar páginas locais interligadas com links de demonstração.
- Mostrar URL de destino antes/depois do clique no painel do instrutor.
- Registrar sequência de navegação, origem interna, destino local, horário e status.
- Incluir exemplos de link legítimo, link inesperado, redirecionamento e página de aviso.
- Manter todos os destinos no domínio/endereço do laboratório.
- Não clonar serviços reais nem direcionar o participante a páginas externas de coleta.

### 4.3 Formulários e dados recebidos

- Formulários de demonstração com campos sintéticos.
- O envio deve realizar uma requisição HTTP real ao servidor local.
- O operador deve conseguir ver, durante a sessão ativa, os valores de teste que foram enviados e os metadados da requisição.
- A interface deve informar discretamente que se trata do ambiente de treinamento no contexto de preparação da aula; o portal do participante mantém apresentação natural, sem avisos grotescos.
- Bloquear e descartar campos que tentem enviar senhas reais, cookies, tokens, cabeçalhos de autorização ou dados não previstos.
- Não armazenar os valores preenchidos em histórico permanente. O relatório registra que os campos de teste foram enviados, o resultado e os metadados técnicos mínimos, sem reproduzir valores sensíveis.
- O servidor deve mostrar exatamente o que recebeu, não inferir dados que não chegaram.

### 4.4 Permissões e recursos do aparelho B

A aula precisa demonstrar permissões reais, não botões cenográficos. Entretanto, uma página HTTP servida por IP local não pode ser tratada como se tivesse automaticamente as mesmas capacidades de um aplicativo Android. Câmera, microfone e geolocalização via navegador dependem de contexto seguro e das políticas da versão do navegador; HTTP em IP de LAN normalmente não satisfaz esse requisito.

Por isso, o roadmap prevê duas trilhas complementares:

1. **Portal web:** pode abrir seletor de arquivo com escolha explícita do participante e registrar o resultado mínimo da seleção; pode explicar as permissões e mostrar as limitações do navegador.
2. **Modo participante Android:** componente/aplicativo de laboratório instalado no aparelho B, ou fluxo nativo equivalente, solicita permissões reais do Android para funções didáticas próprias. O próprio aparelho B mostra o diálogo do sistema e o resultado. Câmera/microfone podem ser demonstrados por prévia local; localização é apresentada localmente no aparelho B. O aparelho A recebe apenas o estado da permissão e o evento da demonstração, salvo se houver uma transferência de amostra não sensível, explicitamente autorizada e necessária à aula.

Regras:
- Nenhuma permissão é concedida por estar conectado ao Wi-Fi ou por preencher um login.
- Nenhuma permissão do Android pode ser solicitada remotamente pelo aparelho A.
- O participante precisa executar/autorizar a função no aparelho B.
- Não fazer captura oculta, gravação, transmissão contínua de câmera/microfone, rastreamento ou leitura de arquivos sem seleção.
- Arquivos são escolhidos pelo seletor do sistema; o conteúdo não é enviado ao aparelho A por padrão.
- O painel registra solicitado/concedido/negado/cancelado e a função realmente executada.

## 5. Painel RED — observabilidade do instrutor

O painel do aparelho A deve exibir em tempo real:

- Estado do hotspot e do servidor.
- SSID e parâmetros disponibilizados pelo Android.
- Estado de conexão do aparelho participante e informações de rede que o sistema realmente expuser.
- Endereço/porta do portal.
- Requisições recebidas: horário, método, rota, status, tamanho, tipo de conteúdo e cabeçalhos permitidos.
- Campos sintéticos recebidos na sessão ativa, com rótulo de dados de demonstração.
- Sequência de cliques e navegação dentro do portal.
- Redirecionamentos locais realizados.
- Tentativas de abrir recursos e resultado real.
- Permissões solicitadas, concedidas, negadas ou canceladas no aparelho B.
- Erros de rede, navegador, Android e servidor.
- Diferença entre “solicitado”, “autorizado”, “executado” e “recebido”.

Não exibir um sucesso genérico quando o Android ou navegador bloquear a ação. Não inventar IP, identidade de dispositivo, permissões ou conteúdo recebido. O painel deve indicar quando uma informação não está disponível ao aplicativo.

## 6. Diretriz visual e material das aulas

- Interface do participante natural, limpa e coerente com portal Wi-Fi comum.
- Sem banners enormes, alertas vermelhos ou rótulos repetidos que destruam o realismo da experiência.
- Identidade do laboratório e autorização ficam na preparação da aula e no painel do instrutor.
- Diálogos de permissão devem ser os diálogos verdadeiros do Android/navegador, sem imitação visual.
- Vídeos mostram o comportamento: criação da rede, conexão, portal, navegação, permissões e o que foi recebido.
- Não mostrar código-fonte, scripts ou detalhes internos nos vídeos.
- A narração diferencia comportamento observado, possibilidade técnica e limitação da plataforma.

## 7. Integração pedagógica RED ↔ BLUE

RED e BLUE compartilham um identificador de cenário e uma linha do tempo de evidências, mas cada área mantém sua própria interface e roteiro.

**RED apresenta:**
- preparação do hotspot e portal;
- conexão do segundo aparelho;
- execução dos cenários de acesso;
- requisições e eventos recebidos;
- resultados reais das permissões e interações.

**BLUE apresenta o mesmo cenário pela perspectiva defensiva:**
- reconhecer redes e portais suspeitos;
- avaliar se o login é realmente necessário;
- observar URL, protocolo e redirecionamentos;
- entender o significado das permissões solicitadas;
- verificar quais informações foram expostas;
- recusar permissões desnecessárias;
- desconectar-se e esquecer a rede;
- revisar permissões do navegador/aplicativo;
- explicar HTTPS, HTTP e os limites de visibilidade do operador da rede.

O BLUE não deve inventar alertas ou detecções. Cada conclusão deve apontar para um evento observado ou para uma explicação conceitual identificada como tal.

## 8. Arquitetura técnica proposta

- Manter RED como módulo Android independente do runtime PRoot.
- Preservar Kotlin, Jetpack Compose, ViewModel/StateFlow e separação UI/domínio/adaptadores.
- Adaptador de hotspot Android isolado e dependente das APIs públicas.
- Servidor HTTP local com escopo de interface e ciclo de vida da sessão.
- Porta padrão 8080, com tratamento claro de conflito e sem fallback silencioso para exposição em todas as interfaces.
- Portal em assets versionados, com roteador de páginas e formulários de demonstração.
- Parser/validador de catálogo com ids estáveis.
- Modelo de eventos tipados e sequenciados.
- Canal local autenticado entre modo participante Android e painel operador, se necessário, limitado à sessão de laboratório.
- Separar eventos de rede, navegação, formulário, permissão, recurso e erro.
- Telemetria temporária em memória para os valores sintéticos; persistência apenas de metadados mínimos.
- Relatório Markdown/JSON sem valores de campos sensíveis.
- Configuração explícita de limites de tamanho, rotas permitidas, tempo de sessão e retenção.
- Nenhuma dependência de servidor remoto para a operação básica.

## 9. Roadmap de implementação completo

### RED-0 — Revisão de escopo e arquitetura <sub>CONCLUÍDO</sub>
- Documentação da primeira versão simplificada atualizada.
- Arquivos RED existentes e pontos de integração mapeados.
- Contratos iniciais e topologia de dois aparelhos definidos.
- Modelo de eventos, observabilidade e política de retenção registrados.
- Limitações reais de Android, navegador e HTTP local documentadas.

### RED-1 — Fundação RED e catálogo <sub>CONCLUÍDO EM CÓDIGO</sub>
- Navegação e catálogo existentes preservados.
- Metadados e roteiro da Operação 01 atualizados para os dois cenários.
- Estados de rede e portal modelados separadamente.
- Eventos tipados ampliados para hotspot, portal, HTTP, navegação, formulários e permissões.
- Observação HTTP modelada sem conteúdo de formulário persistente.
- Linha do tempo ganhou sequência e metadados de rota/status/bytes; persistência retrocompatível com sessões anteriores.
- Histórico e progresso continuam isolados.

### RED-2 — Hotspot e conexão de dois aparelhos <sub>CONCLUÍDO EM CÓDIGO</sub>
- Integrado o fluxo público `LocalOnlyHotspot` para iniciar/parar o hotspot.
- Exibição do SSID e senha somente quando fornecidos pelo Android.
- Identificação indicativa de interface Wi-Fi/AP e IPv4 quando expostos pelo sistema.
- Estado de hotspot solicitado, ativo, encerrado ou falho modelado e exibido.
- A atividade do aparelho participante é confirmada por requisição HTTP real ao portal, sem varredura de dispositivos.
- Limitação registrada: Android não oferece uma API universal para listar clientes conectados ao `LocalOnlyHotspot`; desconexão silenciosa do participante não pode ser afirmada sem evento observável.

### RED-3 — Servidor HTTP local na porta 8080
- Evoluir servidor atual para porta 8080 configurável com padrão fixo.
- Vincular somente à interface do laboratório identificada.
- Implementar rotas permitidas, limites de requisição, timeout e encerramento.
- Mostrar URL completa e QR Code.
- Exibir método, rota, status, tipo e tamanho de cada requisição.
- Não implementar interceptação, proxy transparente ou captura de tráfego de terceiros.

### RED-4 — Portal natural e dois cenários <sub>CONCLUÍDO EM CÓDIGO</sub>
- Identidade fictícia de portal Wi-Fi, com apresentação natural e sem imitar marcas reais.
- Seletor de modo no operador: login local ou acesso direto.
- Páginas de boas-vindas, termos, formulário local, conteúdo, destinos e aviso.
- Links locais e redirecionamento HTTP 302 implementados.
- Linha do tempo registra método, rota e status.

### RED-5 — Formulário local e visualização do que foi recebido
- Implementar formulário de dados exclusivamente sintéticos.
- Enviar uma requisição real ao servidor local.
- Validar campos e tipos no servidor.
- Mostrar no painel os valores sintéticos recebidos na sessão ativa.
- Mostrar a requisição e resposta correspondentes.
- Descartar campos não previstos e qualquer dado sensível.
- Não persistir valores de senha ou dados pessoais em histórico/relatório.
- Diferenciar validação local de autenticação externa.

### RED-6 — Navegação, links e comportamento após conexão
- Criar páginas locais com links e destinos distintos.
- Demonstrar que uma sessão pode não ter login.
- Registrar sequência de cliques e páginas abertas.
- Demonstrar redirecionamentos locais e páginas inesperadas.
- Explicar no conteúdo o que HTTP permite observar e o que HTTPS protege.
- Não redirecionar para domínios externos nem realizar coleta fora do laboratório.

### RED-7 — Modo participante e permissões reais
- Definir a arquitetura do modo participante Android para o aparelho B.
- Solicitar permissões no próprio aparelho B, no momento da função.
- Demonstrar câmera/microfone por prévia local e com controles visíveis.
- Demonstrar localização no próprio aparelho B, sem rastreamento contínuo.
- Demonstrar seleção de arquivo pelo seletor Android.
- Enviar ao aparelho A somente eventos de estado necessários à aula.
- Explicar que o portal HTTP em IP de LAN não ganha permissões nativas e que o login não concede permissões.
- Tratar permissão negada, revogada, cancelada ou indisponível.

### RED-8 — Painel de observabilidade
- Criar linha do tempo unificada.
- Exibir requisições e dados sintéticos recebidos.
- Exibir estado de conexão e recursos.
- Exibir permissões solicitadas e resultado.
- Separar fatos observados, inferências e explicações.
- Permitir limpar dados temporários e encerrar sessão.
- Garantir que falhas sejam visíveis e não convertidas em sucesso.

### RED-9 — Relatórios e histórico
- Relatório por sessão com cenário, dispositivos identificados apenas por rótulos locais, horários, eventos, requisições e resultados.
- Não incluir valores de senha, cookies, tokens, arquivos ou localização precisa.
- Exportação manual pelo usuário.
- Retenção limitada e exclusão completa do histórico da operação.
- Recuperação de sessão interrompida sem reabrir hotspot/servidor automaticamente.

### RED-10 — Integração BLUE
- Definir referência compartilhada entre a operação RED e aula BLUE.
- Reutilizar a linha do tempo como evidência, sem compartilhar dados desnecessários.
- Criar roteiro BLUE para o cenário com login local.
- Criar roteiro BLUE para o cenário sem login.
- Criar roteiro BLUE sobre links, redirecionamentos e navegação.
- Criar roteiro BLUE sobre permissões Android.
- Criar roteiro BLUE sobre HTTP/HTTPS e visibilidade de rede.
- Incluir ações de resposta: recusar, desconectar, esquecer rede, revisar permissões e verificar URLs.

### RED-11 — Documentação e experiência de aula
- Tutorial completo no app.
- Preparação dos dois aparelhos.
- Roteiro do instrutor e roteiro do participante.
- Passo a passo de cada cenário.
- Explicação dos resultados esperados sem prometer comportamentos dependentes do fabricante.
- Guia de gravação de vídeo focado na experiência e nos resultados, sem exibir código.
- Solução de problemas de hotspot, endereço, porta 8080, navegador e permissões.

### RED-12 — Fechamento da implementação e congelamento
- Revisar integração e consistência documental.
- Atualizar changelog, arquitetura, guia e retomada.
- Marcar cada fase como implementada somente após alteração de código/documentação correspondente.
- Consolidar lista única de validação final.
- Gerar checkpoint completo.
- Não fazer merge em `main` sem autorização explícita.

### RED-13 — Validação final (somente após RED-0 a RED-12)
- Testes unitários e de integração.
- Build Android e inspeção de artefatos.
- Testes de ciclo de vida do hotspot/servidor.
- Testes de requisições, formulários sintéticos e limites.
- Testes do modo participante e permissões reais.
- Teste físico com os dois aparelhos Android.
- Testes de compatibilidade por versões/fabricantes disponíveis.
- Regressão nas áreas existentes.
- Corrigir achados e repetir validação.
- Teste físico ocorre apenas nesta fase final.

## 10. Critérios globais de conclusão

A Área RED não será considerada completa até que:

- O aparelho A crie e encerre a rede pelo fluxo Android suportado.
- O aparelho B conecte-se e alcance o portal local.
- O servidor opere na porta 8080 e seja acessível somente no escopo do laboratório.
- Os dois cenários (login local e acesso direto) funcionem.
- Links e navegação gerem eventos observáveis.
- O painel mostre requisições e valores sintéticos efetivamente recebidos.
- Permissões sejam solicitadas no aparelho correto e seus resultados reais sejam registrados.
- A interface do participante seja natural, sem alertas artificiais excessivos.
- RED e BLUE estejam ligados ao mesmo cenário e apresentem perspectivas complementares.
- Relatórios e histórico não retenham segredos nem dados pessoais desnecessários.
- Limitações de Android e navegador estejam documentadas.
- Validação final e testes físicos tenham sido concluídos e registrados separadamente.

## 11. Regras de execução

- Usuário e ChatGPT definem escopo e arquitetura; implementação segue as decisões registradas.
- Ler o repositório antes de alterar arquivos.
- Executar fases em sequência, sem parar para pedir aprovação repetida entre subfases.
- Não executar testes, builds, validações, simulações automatizadas ou testes físicos durante a implementação.
- Registrar pendências de validação, sem declarar aprovação antecipada.
- Não apagar ou sobrescrever trabalho existente fora do escopo.
- Não fazer merge para `main` sem autorização explícita.
- O protótipo permanece local e controlado; não adicionar envio automático de dados a serviços externos.

## 12. Estado honesto na revisão de 2026-11-23

A fundação RED já criada oferece navegação, catálogo, sessões, hotspot Android, servidor HTTP local, QR Code e painel inicial. A primeira implementação do portal é simplificada: envia apenas um evento fixo, não mostra os campos recebidos e usa botões de permissões simuladas. Portanto, ela **não satisfaz ainda** o novo escopo de laboratório completo.

O trabalho seguinte deve corrigir essa diferença e implementar RED-2 a RED-12. Nenhuma dessas capacidades adicionais deve ser marcada como concluída antes de existir no código e, posteriormente, ser validada na fase final.
