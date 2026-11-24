# Matriz de compatibilidade RED/BLUE

**Estado:** matriz documental baseada nas configurações e APIs usadas no código; nenhuma combinação foi validada fisicamente nesta execução.

| Item | Configuração/capacidade atual | Limite/observação |
|---|---|---|
| Android mínimo do app | minSdk 28 | Android 9 ou superior; ainda depende de validação por fabricante. |
| Android alvo | targetSdk 28 | Decisão arquitetural do runtime PRoot; rever somente após avaliar compatibilidade do runtime. |
| SDK de compilação | compileSdk 35 | Build ainda pendente nesta execução. |
| Hotspot | LocalOnlyHotspot | API pública; disponibilidade, parâmetros e ciclo de vida dependem do Android/OEM. |
| Permissão A | ACCESS_FINE_LOCATION | Solicitada no momento de iniciar hotspot; o app não consulta coordenadas. |
| Servidor | HTTP local IPv4 privado, porta padrão 8080 | Sem TLS; bind somente na interface/IP identificados. |
| Navegador B | Navegador Android compatível com HTML/JavaScript/fetch | Nenhuma lista de navegadores foi validada fisicamente. |
| Cookies | Cookie de preferência e POST same-origin | Consentimento por categoria; não concede permissões de recursos. |
| Câmera/microfone/localização web | APIs do navegador em contexto seguro | HTTP por IP privado normalmente não é contexto seguro; estado esperado pode ser UNAVAILABLE. |
| HTTPS | Não configurado | Exige certificado confiável e nome/IP compatível; domínio/certificado ainda não fornecido. |
| Histórico | SharedPreferences, schema v2 | Migração v1→v2 e retenção 100 sessões/300 eventos aguardam testes. |

## Regras de apresentação

- Não declarar compatibilidade de fabricante/modelo sem evidência física.
- Não inferir que a API de hotspot sempre disponibilizará SSID, senha, interface ou IPv4.
- Não confundir ausência de requisição com ausência de conexão Wi-Fi.
- Não apresentar permissões web como disponíveis quando window.isSecureContext for falso.
- A indisponibilidade de câmera/microfone/localização em HTTP local é uma limitação do navegador, não uma falha que deva ser contornada.

## Validação pendente

Preencher modelo, versão Android, navegador, resultado, evidência sanitizada e commit durante RED-12/RED-13 na fase final.