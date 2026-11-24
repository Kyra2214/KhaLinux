# RED-13 — Plano de validação física A+B (execução adiada)

**Estado:** matriz corrigida para a topologia atual e pronta para execução somente após RED-12 e a conclusão integral do roadmap. Nenhum teste físico foi executado.

## Topologia obrigatória

- **A:** operador KhaLinux, cria hotspot, hospeda portal, observa e exporta relatório.
- **B:** apenas conecta ao Wi-Fi, abre o portal no navegador, faz login de teste, escolhe cookies e responde às solicitações web legítimas apresentadas pelo navegador. Não instala KhaLinux, não abre ferramentas e não faz configuração adicional.

## Cenário opcional RED-15 — upstream posterior

- Iniciar o hotspot do sistema do Android sem dados móveis/upstream.
- Conectar B e abrir o portal HTTPS.
- Ativar dados móveis/upstream em A sem desligar o hotspot.
- Confirmar que B permanece associado ao mesmo SSID e que o Android disponibiliza navegação externa.
- Confirmar que o A registra somente o upstream validado e não declara navegação em B sem evidência direta.
- Desligar o portal e confirmar que o hotspot do sistema permanece sob controle do Android.

## Matriz física

| ID | Ação | Evidência esperada |
|---|---|---|
| PHY-01 | Iniciar sessão no A | ID, horário e estado inicial registrados. |
| PHY-02 | Solicitar LocalOnlyHotspot | Callback real do Android, SSID/segurança somente se fornecidos. |
| PHY-03 | Identificar interface/IP | Interface e IPv4 observados e associados à sessão. |
| PHY-04 | Iniciar servidor local | Bind no IPv4 específico e porta exibidos no A. |
| PHY-05 | Conectar B ao Wi-Fi | Conexão manual nas Configurações do Android; nenhuma lista de clientes presumida. |
| PHY-06 | Abrir URL/QR no navegador B | Requisição HTTP real registrada no A. |
| PHY-07 | Fazer login de laboratório | POST real com identificador/código sintéticos; nenhum segredo pessoal. |
| PHY-08 | Aceitar cookies | Categorias registradas no A; nenhum pedido de recurso disparado. |
| PHY-09 | Recusar cookies opcionais | Escolha registrada sem categorias opcionais. |
| PHY-10 | Configurar cookies | Estados customizados por categoria registrados. |
| PHY-11 | Abrir Permissões do navegador | Página apresenta ações separadas para câmera, microfone e localização. |
| PHY-12 | Tentar abrir rota sensível no endpoint HTTP/8080 de preparação | Rota não publicada; nenhum formulário, sessão ou prompt de permissão disponível em HTTP. |
| PHY-13 | Instalar CA pública no B após conferir fingerprint e abrir HTTPS/8443 | Navegador aceita a cadeia sem alerta; primeiro handshake TLS é registrado no A. |
| PHY-14 | Negar/cancelar permissão web, quando disponível | Estado negado/cancelado registrado sem declarar concessão. |
| PHY-15 | Conceder e revogar permissão web, quando disponível | Estado real e revogação observados; nenhum payload enviado ao A. |
| PHY-16 | Navegar páginas/links/redirecionamentos locais | Métodos, rotas, status e horários correspondem às requisições reais. |
| PHY-17 | Alternar RED/BLUE | Mesmos IDs e eventos nas duas perspectivas; sem duplicação. |
| PHY-18 | Perda de rede/fechamento do B | Erro/ausência de novas requisições apresentados sem inventar desconexão. |
| PHY-19 | Encerrar sessão no A e revisar histórico | Servidor/hotspot encerrados; relatório sem cookies, token, campos, mídia ou coordenadas. |
| PHY-20 | Repetir ciclo completo | Nova sessão independente, sem token ou estado residual. |

## Limites explícitos

- B não executa app participante nativo, não concede permissões nativas ao KhaLinux e não abre SAF.
- Não há prévia de câmera, gravação de microfone ou leitura de coordenadas para o laboratório. Os recursos web, se habilitados em origem segura, são imediatamente interrompidos/descartados e não são enviados ao A.
- O banner de cookies não concede permissões web ou Android.
- Em HTTP local, indisponibilidade é resultado válido e deve ser registrada como tal.
- Se for necessário HTTPS confiável, domínio/certificado deverá existir e ser aceito pelo navegador; não instalar certificado nem alterar configuração do B silenciosamente.

Para cada execução registrar ID, modelo/versão Android, versão do app, commit, pré-condições, passos, resultado observado e evidência sem dados pessoais. Não publicar IMEI, número de série, MAC completo ou localização.