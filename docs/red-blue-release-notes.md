# KhaLinux RED/BLUE — notas de versão em preparação

**Estado:** notas preparadas para revisão; não é uma release validada. Nenhum APK foi compilado ou publicado nesta execução.

## Implementação incluída

- Laboratório RED com dois aparelhos: A opera o KhaLinux e centraliza observabilidade; B participa exclusivamente pelo navegador.
- Hotspot LocalOnlyHotspot solicitado ao Android e servidor HTTP vinculado ao IPv4 privado identificado.
- Portal local com login de teste, navegação, redirecionamentos e aviso de cookies com aceitar todos, recusar opcionais e configuração por categoria.
- Escolhas de cookies registradas por categoria; o aceite de cookies não concede permissões Android ou web.
- Página de permissões web com ações separadas para câmera, microfone e localização, condicionadas a contexto seguro e suporte do navegador.
- Eventos HTTP e estados de permissões integrados à linha do tempo RED e ao painel defensivo BLUE.
- Sessões com token efêmero, cookie HttpOnly/SameSite, controle de origem, expiração e limitação de tentativas.
- Histórico com schema versionado, retenção limitada, relatório Markdown e exclusão individual.
- BLUE separa fatos observados de interpretação defensiva e não inventa alertas.

## Limitações conhecidas

- O portal atual usa HTTP por IPv4 privado, sem TLS. O navegador normalmente não habilita APIs sensíveis nesse contexto; permissões web podem aparecer como indisponíveis.
- O token de sessão é protegido contra submissões cross-origin, mas HTTP não fornece confidencialidade contra observação do tráfego local.
- O comportamento do LocalOnlyHotspot varia por versão Android e fabricante.
- A lista universal de clientes do hotspot não é disponibilizada a apps comuns; o painel confirma atividade somente quando recebe requisições HTTP.
- RED-07 depende de origem HTTPS confiável para prompts nativos reais; domínio/certificado ainda não foi fornecido.

## Estado de validação

- Compilação/build: pendente.
- Testes unitários e integração host/Android: pendentes.
- Auditoria de segurança/privacidade: pendente.
- Testes físicos A+B: pendentes.
- APK, assinatura e SHA-256: não gerados.

Estas notas devem ser atualizadas após a validação final e vinculadas ao commit e ao hash do pacote efetivamente produzido.