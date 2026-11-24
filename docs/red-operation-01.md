# RED-01 — Wi-Fi de laboratório, portal e navegação

**Estado:** implementação em andamento nesta branch. O servidor HTTP local, o portal, o formulário de login com dados exclusivos do laboratório e a primeira versão do aviso de cookies estão no código. A escolha de cookies é registrada na linha do tempo do operador. A validação funcional, compilação e testes permanecem reservados à etapa final.  
**Roadmap:** [roadmap-red-blue-laboratorio.md](roadmap-red-blue-laboratorio.md)

## Topologia

- **Aparelho A:** executa KhaLinux, cria a rede, hospeda o portal, controla a sessão e exibe toda a informação RED/BLUE.
- **Aparelho B:** conecta-se ao Wi-Fi e usa somente o navegador. Não instala o KhaLinux, não abre ferramentas e não opera painéis.

## Fluxo principal

1. No A, abrir RED, selecionar a Operação 01 e iniciar uma sessão.
2. Solicitar o hotspot ao Android e iniciar o portal local.
3. No B, conectar-se ao Wi-Fi e abrir o endereço/QR Code exibido pelo A.
4. No navegador, responder ao aviso de cookies.
5. Fazer login somente com identificador e código de teste do laboratório.
6. Navegar pelas páginas locais.
7. No A, acompanhar requisições HTTP, escolha de cookies e eventos realmente recebidos.
8. Encerrar a sessão no A e consultar o relatório.

## Aviso de cookies

O portal oferece três ações:

- **Aceitar todos:** aceita as categorias opcionais apresentadas.
- **Recusar opcionais:** mantém somente a escolha necessária para lembrar o consentimento.
- **Configurar:** permite escolher análise, preferências e personalização separadamente.

O navegador grava cookies próprios de preferência e envia ao servidor uma submissão limitada contendo somente a escolha e os estados das categorias. O servidor valida os campos e registra o resumo no A. Cabeçalhos Cookie e valores dos cookies não são armazenados na linha do tempo nem no relatório.

Aceitar cookies não concede permissões do Android ou do navegador. O aviso de cookies e as permissões de recursos são mecanismos independentes.

## Login do laboratório

O formulário usa apenas identificador e código próprios da aula. Não imita provedores, redes sociais, bancos ou serviços reais e não aceita credenciais de contas externas. O corpo do formulário não é persistido no histórico ou no relatório.

## Permissões do navegador

O B permanece exclusivamente no navegador. Câmera, microfone e localização via APIs web exigem contexto seguro, suporte do navegador e ação explícita do participante. O portal atual usa HTTP em IP local; nessa condição, navegadores modernos normalmente não disponibilizam APIs sensíveis por não ser uma origem segura.

O projeto não contorna essa política, não oculta a finalidade dos pedidos e não apresenta concessões fictícias. Se uma API não estiver disponível, o A registra a limitação em vez de declarar uma permissão concedida.

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
