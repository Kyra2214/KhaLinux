# RED-01 — Wi-Fi de laboratório, portal e navegação

**Estado:** implementação em código/documentação parcial. Portal HTTPS local, preparação de CA, login sintético, cookies, página de permissões individuais, telemetria e painel A estão integrados. RED-07 permanece parcial até confiança efetiva do navegador e acabamento; validação/build/testes ficam para a etapa final.  
**Roadmap:** [roadmap-red-blue-laboratorio.md](roadmap-red-blue-laboratorio.md)

## Topologia

- **Aparelho A:** executa KhaLinux, cria a rede, hospeda o portal, controla a sessão e exibe toda a informação RED/BLUE.
- **Aparelho B:** conecta-se ao Wi-Fi e usa somente o navegador. Não instala o KhaLinux, não abre ferramentas e não opera painéis.

## Fluxo principal

1. No A, abrir RED, selecionar a Operação 01 e iniciar uma sessão.
2. Solicitar o hotspot ao Android, iniciar a preparação HTTP da CA em 8080 e o portal HTTPS em 8443.
3. No B, conectar-se ao Wi-Fi, abrir a URL de preparação, baixar a CA pública, conferir o fingerprint com o A e instalar a CA nas configurações do Android; depois abrir o endereço HTTPS/QR.
4. Na própria página do portal, responder ao aviso de cookies e depois fazer login somente com identificador e código de teste do laboratório.
5. Após o login, abrir **Permissões do navegador** e escolher separadamente câmera, microfone ou localização. Cada botão tem finalidade explícita e depende de HTTPS confiável, contexto seguro e suporte do navegador.
8. Navegar pelas páginas locais.
9. No A, acompanhar requisições HTTP, escolha de cookies e eventos realmente recebidos.
10. Encerrar a sessão no A e consultar o relatório.

## Aviso de cookies

O portal oferece três ações:

- **Aceitar todos:** aceita as categorias opcionais apresentadas.
- **Recusar opcionais:** mantém somente a escolha necessária para lembrar o consentimento.
- **Configurar:** permite escolher análise, preferências e personalização separadamente.

O navegador grava cookies próprios de preferência e envia ao servidor uma submissão limitada contendo somente a escolha e os estados das categorias. O servidor valida os campos e registra o resumo no A. Cabeçalhos Cookie e valores dos cookies não são armazenados na linha do tempo nem no relatório.

Aceitar cookies não concede permissões do Android ou do navegador. O aviso de cookies e as permissões de recursos são mecanismos independentes. Os pedidos de câmera, microfone e localização têm ações separadas na página **Permissões do navegador**; não há botão único que aceite tudo.

## Login do laboratório

O formulário usa apenas identificador e código próprios da aula. Não imita provedores, redes sociais, bancos ou serviços reais e não aceita credenciais de contas externas. O corpo do formulário não é persistido no histórico ou no relatório.

## Permissões do navegador

O B permanece exclusivamente no navegador. Câmera, microfone e localização via APIs web exigem contexto seguro, suporte do navegador e ação explícita do participante. O portal participante usa HTTPS local em IP privado. Se a CA não for confiável pelo navegador, a conexão HTTPS não será estabelecida e as APIs sensíveis continuarão indisponíveis.

O projeto não contorna essa política, não oculta a finalidade dos pedidos e não apresenta concessões fictícias. Quando o navegador não aceitar a cadeia TLS ou não considerar a origem segura, o painel registra indisponibilidade e não exibe os prompts. Se uma API não estiver disponível, o A registra a limitação em vez de declarar uma permissão concedida.

## Painel do operador A

O A concentra:

- estado real do hotspot e do servidor;
- interface, endereço e porta quando disponibilizados pelo Android;
- requisições HTTP recebidas, método, rota, status, bytes e cabeçalhos permitidos;
- escolha de cookies e categorias aceitas/recusadas;
- erros, indisponibilidades e linha do tempo;
- relatório local.

Não há captura de tráfego externo, leitura de arquivos, coleta de mídia ou armazenamento de senhas. O servidor observa somente requisições destinadas ao portal local.

## Validação

Nenhum build, compilação ou teste foi executado durante a implementação. As verificações automatizadas, compilação e testes físicos dos dois aparelhos permanecem acumulados para a fase final do roadmap.
