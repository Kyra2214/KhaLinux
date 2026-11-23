# RED-01 — Wi-Fi público, portal local e navegação

**Estado:** RED-2 a RED-7 implementados em código: hotspot, servidor 8080, portal natural, login local fictício, acesso direto, formulário sintético, navegação local e modo participante Android. RED-9 em diante pendentes; validação final não executada.  
**Roadmap:** `docs/roadmap-area-red-estagio-01-wifi-publico.md`

## Objetivo

Demonstrar com dois aparelhos Android controlados pelo instrutor como uma rede Wi-Fi de laboratório é criada, como o segundo aparelho se conecta, como um portal local funciona e o que o servidor realmente recebe. A aula também demonstra que pode não haver login: links e navegação após a conexão são parte importante do cenário.

## Aparelhos

- **Aparelho A:** operador RED, hotspot Android, servidor HTTP local na porta 8080, QR Code, painel e relatório.
- **Aparelho B:** aparelho participante dedicado, conectado manualmente à rede, usado para navegar e executar demonstrações autorizadas de permissões.

## Fluxo principal

1. Abrir RED e selecionar a Operação 01.
2. Iniciar sessão e preparar o aparelho A.
3. Solicitar ao Android o hotspot usando API pública suportada.
4. Exibir SSID, senha e parâmetros efetivamente fornecidos pelo sistema.
5. Iniciar servidor HTTP local na porta 8080, vinculado à interface do laboratório.
6. Exibir endereço e QR Code.
7. Conectar o aparelho B pelo seletor Wi-Fi do Android.
8. Abrir `http://<IP-local-do-A>:8080` no navegador.
9. Executar os cenários de portal com login local e de acesso direto sem login.
10. Navegar por links e páginas locais.
11. Observar no painel as requisições, os eventos e os dados sintéticos efetivamente recebidos.
12. Executar no aparelho B, quando previsto, o modo participante Android para demonstrações reais de permissões.
13. Encerrar sessão, hotspot e servidor; gerar relatório local.

## Cenário A — autenticação local

Portal fictício com aparência natural de acesso Wi-Fi. O formulário usa somente valores sintéticos definidos para a aula. A autenticação é validada localmente, sem conexão com Google, Facebook, Instagram, OAuth ou serviços externos.

O servidor recebe uma requisição HTTP real. O painel RED mostra durante a sessão ativa os campos sintéticos enviados, o método, a rota, o status e o resultado da validação. Senhas e valores sensíveis não são mantidos no histórico permanente nem no relatório.

## Cenário B — acesso direto

Portal de boas-vindas, aceite de termos ou acesso imediato. Não há formulário de login obrigatório. O participante navega por páginas e links locais. O painel mostra a sequência de cliques, rotas, redirecionamentos e respostas.

## Links e navegação

Páginas locais demonstram destinos diferentes, links inesperados e redirecionamentos internos. O painel registra origem, destino local, horário, método e status. Nenhuma página imita serviço real e nenhum link direciona para uma coleta externa.

## Permissões reais

O login e a conexão Wi-Fi não concedem permissões Android. Uma página HTTP em IP local também não tem automaticamente acesso a câmera, microfone ou geolocalização; APIs do navegador dependem de contexto seguro e políticas do navegador.

A demonstração real será feita por um modo participante Android no aparelho B:
- câmera e microfone com prévia local e controles de início/parada;
- localização exibida localmente no aparelho B;
- arquivos escolhidos pelo seletor do sistema;
- painel A recebe somente o estado da permissão e eventos mínimos da demonstração;
- não há captura oculta, transmissão contínua ou leitura indiscriminada de arquivos.

## O que o painel RED apresenta

- estado do hotspot e do servidor;
- SSID e parâmetros fornecidos pelo Android;
- estado e dados de conexão realmente disponíveis;
- endereço e porta do portal;
- requisições recebidas com horário, método, rota, status, tipo e tamanho;
- campos sintéticos recebidos durante a sessão ativa;
- sequência de navegação e redirecionamentos;
- permissões solicitadas/concedidas/negadas/canceladas;
- funções efetivamente executadas;
- erros e limitações.

O painel não inventa informações. Deve distinguir solicitado, autorizado, executado e recebido.

## Diretriz visual e vídeo

O portal do participante deve ser natural, sem banners ou alertas grotescos que destruam o realismo. A identificação do laboratório e a observabilidade ficam no painel do instrutor. Os vídeos mostram criação da rede, conexão, portal, navegação, permissões e resultados, sem exibir código-fonte.

## Painel de observabilidade\n\nO painel mostra estado do hotspot e servidor, interface e porta, atividade HTTP do participante, requisições com método/rota/status/tamanho/tipo/endereço remoto e cabeçalhos de baixo risco permitidos. Valores sintéticos são exibidos apenas em memória. Eventos de permissões, prévias e seleções são apresentados na linha do tempo. Cabeçalhos Cookie e Authorization não são armazenados.\n\n## Modo participante no aparelho B\n\nO segundo aparelho pode entrar no modo participante do KhaLinux. O Android solicita as permissões reais. A câmera é exibida em prévia local; o microfone alimenta um medidor de nível que descarta as amostras; a localização aparece somente no aparelho B; arquivos são escolhidos pelo seletor SAF sem leitura de nome/conteúdo. Para registrar o resultado no painel A, o participante envia somente o estado da ação por uma rota local com token temporário da sessão.\n\n## Limitações técnicas

- Hotspot é gerenciado pelo Android e varia por fabricante/versão.
- A abertura automática do portal não é garantida; QR Code e endereço manual são alternativas.
- HTTP na porta 8080 é HTTP sem TLS, não HTTPS falso.
- O portal não recebe automaticamente permissões nativas.
- O segundo aparelho precisa autorizar cada função no próprio Android.
- Informações não expostas pelo sistema devem ser indicadas como indisponíveis.

## Estado atual

A implementação atual tem hotspot Android, servidor HTTP local na porta 8080, QR Code, limites de requisição, timeout, painel de requisições, portal natural fictício, modos login local e acesso direto, páginas/links internos, redirect 302 e formulário POST com allowlist de valores sintéticos. O operador vê os valores permitidos apenas em memória durante a sessão; histórico e relatório não os persistem. O modo participante Android e permissões reais ainda precisam ser implementados. Nenhum teste/build foi executado; validação permanece para a fase final.
