# KhaLinux RED/BLUE — notas de versão preliminares

**Estado:** documentação de release preparada para a branch de trabalho; não representa release validada, APK publicado ou compatibilidade certificada.

## Escopo incluído na branch

- Laboratório RED com dois aparelhos: A opera o KhaLinux e centraliza observabilidade; B participa exclusivamente pelo navegador.
- Hotspot LocalOnlyHotspot solicitado ao Android; portal HTTPS/8443 vinculado ao IPv4 privado e endpoint HTTP/8080 restrito à preparação da CA pública.
- Portal local com login de demonstração, navegação, redirecionamentos e aviso de cookies com aceitar todos, recusar opcionais e configuração por categoria.
- Escolhas de cookies registradas por categoria; o aceite de cookies não concede permissões Android ou web.
- Página de permissões web com ações separadas para câmera, microfone e localização, condicionadas a contexto seguro e suporte do navegador.
- Eventos do portal HTTPS, download da CA e handshake TLS, além dos estados de permissões, integrados à linha do tempo RED e ao painel defensivo BLUE.
- Sessões com token efêmero, cookie HttpOnly/SameSite, controle de origem, expiração e limitação de tentativas.
- Histórico com schema versionado, retenção limitada, relatório Markdown e exclusão individual.
- BLUE separa fatos observados de interpretação defensiva e não inventa alertas.

## Limitações conhecidas

- O portal participante usa HTTPS local com CA própria; a confiança precisa ser configurada manualmente no B e confirmada por handshake. A aceitação da CA varia por navegador/versão Android.
- O token de sessão usa cookie Secure/HttpOnly/SameSite e origem HTTPS; HTTP/8080 não recebe credenciais nem eventos sensíveis.
- O comportamento do LocalOnlyHotspot varia por versão Android e fabricante.
- A lista universal de clientes do hotspot não é disponibilizada a apps comuns; o painel confirma atividade somente quando recebe requisições HTTP.
- RED-07 está implementado em código; a validação física da confiança TLS e dos prompts web reais ainda não foi executada.
- RED-15 adiciona modo alternativo de hotspot do sistema: ativação manual nas Configurações, descoberta de interface, portal HTTPS na mesma rede e observação de upstream validado em A. A navegação externa em B continua dependente do Android/operadora e de validação física.
- A matriz de compatibilidade é documental; não há combinação de aparelho/fabricante/navegador declarada como validada.

## Estado técnico e de validação

- Branch: `recovery/red-portal-integration`.
- Snapshot de referência da estabilização: `8b32737c8400a5d4076f57f9d5f6132c12fceae9`.
- Build/compilação: pendentes.
- Testes unitários e integração host/Android: pendentes.
- Auditoria final de segurança/privacidade: pendente.
- Testes físicos A+B: pendentes.
- APK, assinatura e SHA-256: não gerados.
- PR: não aberto.

Estas notas são preliminares e devem ser atualizadas com resultados reais, commit final e hash do pacote somente depois da aprovação de todos os portões de validação.
