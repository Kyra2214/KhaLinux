# Área RED — Operação 01: Nunca Conecte à Wi-Fi Pública

**Status:** implementação inicial em código concluída; validação final pendente  
**Branch:** `plan/area-red-wifi-publico`  
**Produto:** KhaLinux / futura identidade Brain Lab Security  
**Escopo deste documento:** especificação funcional, critérios e backlog de validação final. O código da Operação 01 foi implementado na branch indicada; nenhum teste/build foi executado durante a implementação.

---

## 1. Conceito da aula

A Área RED apresenta a perspectiva ofensiva de uma simulação controlada: o operador prepara uma rede Wi-Fi de laboratório com aparência de Wi-Fi público, conecta um segundo aparelho participante e executa uma sequência de armadilhas didáticas em um portal local.

O nome da operação é **Nunca Conecte à Wi-Fi Pública**.

A aula RED ensina a preparar e executar o cenário. A aula BLUE, em conteúdo e estágio próprios, ficará responsável por investigação, detecção, mitigação e defesa. Não misturar os roteiros pedagógicos.

## 2. Resultado esperado

Ao concluir o Estágio 1, o aluno deverá conseguir, com dois aparelhos Android sob seu controle ou autorização explícita:

1. Preparar o aparelho operador e iniciar uma rede de laboratório.
2. Conectar o aparelho participante à rede.
3. Abrir o portal cativo/simulador local.
4. Percorrer armadilhas interativas de engenharia social e autenticação fictícia.
5. observar os eventos gerados durante a simulação.
6. Encerrar a operação e consultar o registro local.
7. Seguir um tutorial escrito que descreve a preparação e a execução da aula RED.

O aplicativo precisa ser funcional. Não entregar somente telas ilustrativas, protótipos clicáveis sem comportamento ou documentação conceitual.

## 3. Arquitetura operacional

### Aparelho A — Operador RED

Responsabilidades:

- Abrir a Área RED e selecionar a Operação 01.
- Informar o SSID de laboratório.
- Configurar os parâmetros permitidos pelo Android.
- Orientar/acionar a ativação do hotspot do sistema.
- Iniciar o serviço local do portal.
- Exibir estado da operação, participantes e eventos da simulação.
- Encerrar a operação e consolidar o relatório.

### Aparelho B — Participante

Responsabilidades:

- Localizar e conectar-se à rede de laboratório.
- Acessar o portal local.
- Exibir as páginas de treinamento.
- Interagir somente com dados fictícios e permissões demonstrativas.
- Mostrar aviso persistente de que está em um laboratório autorizado.

### Serviço local

- Hospedar as páginas do portal no aparelho operador.
- Manter o conteúdo e os dados de demonstração no ambiente local.
- Registrar eventos sintéticos da sessão.
- Não depender de um servidor externo para a experiência básica.
- Não encaminhar formulários para serviços de terceiros.

## 4. Limitações Android que devem orientar a implementação

- O Android controla a criação e a configuração do hotspot. O aplicativo deve usar APIs públicas e fluxos do sistema, solicitando a ação do usuário quando o sistema não permitir ativação/configuração silenciosa.
- Não presumir que um aplicativo comum possa definir qualquer senha/SSID de hotspot em todas as versões e fabricantes.
- A abertura automática de portal cativo não é garantida em uma rede sem internet ou em todos os aparelhos. Implementar a melhor integração suportada e manter alternativas claras: notificação/ação para abrir o portal, endereço local e QR Code.
- O portal local deve funcionar na rede de laboratório sem depender de DNS público ou de interceptação de tráfego de terceiros.
- A aplicação deve informar quando o fabricante/versão Android impedir uma etapa e orientar a alternativa suportada, sem alegar que a operação foi iniciada quando não foi.

## 5. Fluxo funcional

1. **Tela inicial:** Área RED → Operação 01.
2. **Preparação:** nome da rede, instruções de hotspot e confirmação de ambiente autorizado.
3. **Ativação:** usuário ativa o hotspot pelo fluxo permitido pelo Android.
4. **Aguardando participante:** operador vê instruções e estado da rede.
5. **Conexão:** participante conecta-se pelo seletor Wi-Fi do Android.
6. **Portal:** participante abre o portal local, preferencialmente por detecção/abertura assistida; QR Code e endereço local são alternativas.
7. **Aula prática:** participante percorre as armadilhas fictícias.
8. **Painel RED:** operador acompanha eventos didáticos sem visualizar dados pessoais reais.
9. **Encerramento:** sessão é finalizada, dados temporários são limpos conforme política e relatório local é disponibilizado.

## 6. Armadilhas didáticas do portal

Todas as páginas devem ser claramente identificadas como **SIMULAÇÃO DE LABORATÓRIO**. A interface pode ensinar a reconhecer padrões de portais públicos e tentativas de engenharia social, mas não deve se passar por um serviço real nem coletar credenciais utilizáveis.

### 6.1 Portal de Wi-Fi público simulado

- Página inicial com nome e identidade visual fictícios do estabelecimento/laboratório.
- Aviso de simulação visível.
- Botões e etapas interativas.
- Registro de navegação e escolhas fictícias.

### 6.2 Formulário de credenciais fictícias

- Campo de e-mail e senha para exercício.
- Credenciais de demonstração fornecidas pelo próprio laboratório, por exemplo `xxx@gmail.com` e `123`.
- Os valores são dados de teste, não contas reais; não validar contra Gmail, Facebook, Instagram ou qualquer serviço externo.
- Armazenar apenas o resultado sintético do exercício (por exemplo: formulário preenchido, etapa concluída), evitando persistir a senha digitada.
- Exibir ao final o que o participante acabou de fazer e por que o cenário é uma armadilha.

### 6.3 Páginas temáticas de redes sociais

- Páginas fictícias para demonstrar padrões visuais de autenticação e indução.
- Não usar integração de login real, OAuth real ou endpoints de terceiros.
- Não copiar de modo enganoso páginas oficiais, não coletar senhas reais e não transmitir credenciais.
- Usar nomes/identidades genéricos ou telas explicitamente marcadas como “simulação de rede social”.

### 6.4 Permissões do Android

- Demonstração educativa de solicitações de localização, câmera e arquivos.
- Cada permissão real só poderá ser solicitada pelo aplicativo que a utiliza, com explicação e consentimento explícito no próprio aparelho.
- A conexão Wi-Fi não concede essas permissões ao aparelho operador.
- Não solicitar permissões sem funcionalidade legítima e necessária.
- Se a permissão não for necessária para a aula, usar uma tela demonstrativa em vez de pedir acesso real.

### 6.5 Eventos registrados

Registrar apenas dados necessários ao exercício:

- início e fim da sessão;
- identificador aleatório da sessão;
- etapa aberta/concluída;
- escolhas e botões acionados;
- formulário de demonstração concluído, sem persistir a senha;
- permissões demonstradas e resultado autorizado pelo próprio Android;
- erros técnicos e encerramento.

Não coletar conteúdo de outros aplicativos, mensagens, cookies, tokens, arquivos privados, credenciais reais ou tráfego de terceiros.

## 7. Tutorial escrito da aula RED

Entregar um tutorial dentro do aplicativo e em documentação do projeto, com:

1. objetivo e pré-requisitos;
2. preparação dos dois aparelhos;
3. configuração do nome da rede;
4. ativação do hotspot pelos controles permitidos do Android;
5. conexão do aparelho participante;
6. abertura do portal local;
7. execução passo a passo de cada armadilha simulada;
8. uso das credenciais fictícias de laboratório;
9. observação dos eventos no painel RED;
10. encerramento e geração do relatório;
11. solução de problemas comuns de conexão e compatibilidade.

O tutorial é exclusivamente o roteiro de execução da simulação RED. O conteúdo de defesa, investigação e correção pertence à futura aula BLUE.

## 8. Roadmap de implementação

### Fase 1 — Estrutura da Operação 01
- Criar entrada da Operação 01 na Área RED.
- Criar estados de operação: não iniciada, preparando, aguardando conexão, ativa, encerrando e concluída.
- Criar modelo local de sessão e eventos.
- Criar navegação entre configuração, painel e tutorial.

### Fase 2 — Rede de laboratório
- Implementar integração com os fluxos e APIs públicas do Android.
- Configurar SSID conforme capacidades disponíveis.
- Orientar ativação do hotspot pelo sistema quando necessário.
- Detectar/acompanhar conectividade local sem varrer redes alheias.
- Apresentar limitações por versão/fabricante e alternativa manual.

### Fase 3 — Portal local
- Implementar servidor HTTP local restrito à interface de laboratório.
- Disponibilizar endereço local, QR Code e ação de abrir portal.
- Implementar abertura assistida quando possível.
- Garantir que o portal seja acessível apenas durante a sessão e pela rede de laboratório.

### Fase 4 — Armadilhas simuladas
- Criar portal público fictício.
- Criar formulário de credenciais sintéticas.
- Criar telas temáticas genéricas de autenticação.
- Criar demonstração consentida de permissões.
- Implementar feedback pedagógico após cada interação.
- Garantir que nenhuma credencial seja enviada a terceiros ou persistida como senha.

### Fase 5 — Painel e evidências RED
- Mostrar estado da sessão.
- Exibir eventos em ordem cronológica.
- Mostrar progresso das etapas.
- Gerar relatório local com dados sintéticos e técnicos mínimos.
- Permitir encerrar e limpar a sessão.

### Fase 6 — Tutorial e integração
- Incorporar tutorial completo na aplicação.
- Adicionar solução de problemas.
- Integrar entrada da Operação 01 à navegação da Área RED.
- Atualizar documentação do projeto, changelog e roadmap principal sem sobrescrever conteúdo existente.

## 9. Critérios de conclusão do Estágio 1

**Situação atual:** o código dos fluxos descritos foi adicionado. Os critérios abaixo ainda precisam ser confirmados na validação final, especialmente hotspot real, conectividade entre dois aparelhos, QR Code, encerramento e compatibilidade por fabricante.

O estágio será considerado implementado quando:

- O operador consegue iniciar e encerrar a operação usando fluxos Android suportados.
- O segundo aparelho consegue conectar-se à rede de laboratório.
- O portal local abre pelo fluxo automático suportado ou por uma alternativa assistida funcional.
- Todas as armadilhas são interativas e usam exclusivamente dados fictícios.
- O painel mostra eventos reais da simulação, não dados inventados.
- O tutorial permite repetir a aula do início ao fim.
- A sessão pode ser encerrada e os dados temporários tratados corretamente.
- As limitações de compatibilidade estão documentadas.

## 10. Fora do escopo deste estágio

- Ataques contra redes públicas ou dispositivos de terceiros.
- Clonagem de redes de estabelecimentos reais para enganar pessoas.
- Captura, armazenamento ou retransmissão de senhas reais.
- Login real com Google, Facebook ou Instagram.
- Interceptação de tráfego de terceiros, quebra de criptografia ou desvio de proteções.
- Acesso remoto a câmera, localização, arquivos ou outros recursos do aparelho participante.
- Aula BLUE de defesa, detecção e mitigação; será planejada separadamente.

## 11. Regras de execução do desenvolvimento

- Este documento define planejamento; não autoriza iniciar implementação nesta tarefa.
- Ler o estado atual do repositório antes de qualquer implementação.
- Seguir o roadmap fase por fase, sem criar funcionalidades fora do escopo.
- Não executar testes ou validações durante a implementação do roadmap. Requisitos de teste devem ser anotados para a fase final de validação.
- Testes físicos em aparelhos Android ficam exclusivamente para a etapa final, após a implementação completa e as validações finais previstas.
- Não fazer merge desta branch para `main` sem solicitação explícita.
- Registrar decisões técnicas, limitações e progresso no documento de retomada do projeto quando a implementação for autorizada.
