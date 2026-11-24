# Matriz de compatibilidade RED/BLUE

**Estado:** matriz documental baseada nas configurações e APIs identificadas no código. Nenhuma combinação de aparelho, fabricante ou navegador foi validada fisicamente nesta etapa.

| Item | Configuração/capacidade atual | Limite/observação |
|---|---|---|
| Android mínimo do app | minSdk 28 | Android 9 ou superior; compatibilidade por fabricante ainda não validada. |
| Android alvo | targetSdk 28 | Decisão arquitetural do runtime PRoot; rever somente após avaliar compatibilidade do runtime. |
| SDK de compilação | compileSdk 35 | Build ainda pendente. |
| Hotspot | LocalOnlyHotspot | API pública; disponibilidade, parâmetros e ciclo de vida dependem do Android/OEM. |
| Hotspot do sistema | Configurações Android abertas pelo operador; descoberta de IPv4 e callback público de rede | Necessário quando upstream será ativado depois sem trocar o Wi-Fi de B; encaminhamento depende da OEM/operadora. |
| Permissão A | ACCESS_FINE_LOCATION | Solicitada no momento de iniciar hotspot; o app não consulta coordenadas. |
| Servidor participante | HTTPS local IPv4 privado, porta 8443 | Certificado X.509 com SAN IP e CA própria; confiança do navegador ainda não validada fisicamente. |
| Navegador B | Navegador Android compatível com HTML/JavaScript/fetch | Nenhuma versão ou navegador foi validado fisicamente. |
| Cookies | Cookie de preferência e POST same-origin | Consentimento por categoria; não concede permissões de recursos. |
| Câmera/microfone/localização web | APIs do navegador em contexto seguro | Exigem HTTPS confiável, permissão do usuário e suporte do navegador; validação física pendente. |
| Preparação TLS | HTTP local, porta 8080 | Página de instruções, download da CA pública e health; não hospeda portal nem endpoints sensíveis. |
| Histórico | SharedPreferences, schema v2 | Migração v1→v2 e retenção 100 sessões/300 eventos aguardam testes. |

## Regras de apresentação

- Não declarar compatibilidade de fabricante/modelo sem evidência física.
- Não inferir que a API de hotspot sempre disponibilizará SSID, senha, interface ou IPv4.
- Não confundir ausência de requisição com ausência de conexão Wi-Fi.
- Não apresentar permissões web como disponíveis quando `window.isSecureContext` for falso.
- A indisponibilidade de câmera/microfone/localização quando a CA não é confiável é uma limitação do navegador, não uma falha que deva ser contornada.
- Upstream validado em A não certifica navegação externa em B.

## Validação pendente

Na etapa final, preencher modelo, versão Android, navegador, resultado, evidência sanitizada e commit de cada combinação realmente testada. Não converter previsões documentais em certificação de compatibilidade.
