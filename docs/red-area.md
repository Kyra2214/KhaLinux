# Área RED — operações ofensivas do laboratório

**Direção revisada:** 2026-11-23  
**Roadmap operacional:** [roadmap-area-red-completa.md](roadmap-area-red-completa.md)  
**Estado:** o RED Wi-Fi existente é um protótipo de hotspot/portal local e painel. A suíte RED operacional ainda será construída.

## Função

RED é a camada de execução técnica do KhaLinux. Ela usa o Debian ARM64, PRoot, terminal, APT, Cyber Toolkit, motor de operações e adaptadores Android/rede para executar ferramentas e ações reais sobre os ativos cadastrados no laboratório.

Não é uma seção de aulas, questionários ou simulações de resultados. A documentação de apoio fica separada do fluxo de operação.

## Estrutura operacional

- **Ativos:** aparelhos Android, redes, serviços e aplicações identificados pelo inventário.
- **Operações:** procedimentos técnicos com parâmetros, pré-requisitos e ferramentas definidas.
- **Execução:** processos reais no RootFS, APIs Android ou adaptadores de rede.
- **Resultados:** saída original, código de retorno, duração, erros e estado final.
- **Evidências:** logs, relatórios de ferramentas, metadados e arquivos produzidos.
- **Sessões:** agrupam operações e permitem acompanhar, cancelar e repetir execuções.

## Frentes RED

1. **Mobile:** inventário Android, conexão ADB, pacotes, permissões, manifests, componentes, logs e testes dinâmicos.
2. **Network:** interfaces, rotas, DNS, descoberta de hosts, portas, serviços e análise de tráfego.
3. **Wi-Fi:** topologias de laboratório, hotspot, portal, DNS, HTTP/TLS, conexões e captura compatível com o ponto de observação.
4. **Web/AppSec:** reconhecimento, enumeração e testes de aplicações e serviços de laboratório.

## Requisitos de uma operação

- Executar ferramenta, comando ou API real.
- Mostrar parâmetros, alvo, início, estado e término.
- Exibir stdout/stderr e código de saída quando houver processo.
- Preservar evidência de origem e associá-la ao ativo/sessão.
- Permitir cancelar e encerrar processos.
- Publicar eventos reais para a linha do tempo BLUE.
- Não apresentar evento fixo ou resposta pré-fabricada como resultado de execução.

## Estado do código existente

A implementação anterior inclui hotspot Android LocalOnlyHotspot, servidor HTTP local, portal, formulários de demonstração, navegação, linha do tempo e alguns eventos compartilhados com BLUE. Esses componentes podem ser reutilizados como adaptadores e fixtures de rede, mas não equivalem ao laboratório RED completo.

O painel atual não substitui inventário de dispositivos, ADB, descoberta de rede, enumeração de serviços, captura de tráfego, análise de aplicações ou execução de ferramentas. Esses recursos serão implementados conforme as fases LAB-0 a LAB-6.

## Próxima etapa

**LAB-0:** auditar o código atual, mapear capacidades reais do toolkit/runtime e separar o que é infraestrutura reutilizável, conteúdo complementar e protótipo que precisa ser substituído.
