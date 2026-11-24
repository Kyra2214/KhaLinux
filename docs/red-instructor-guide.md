# Guia do instrutor — RED-01 e BLUE

**Público:** instrutor que controla os dois aparelhos Android do laboratório.  
**Vídeos:** demonstrar comportamento e resultados; não exibir código-fonte ou scripts internos.

## Preparação dos aparelhos

### Aparelho A — operador
1. Instale/abra o KhaLinux.
2. Entre em RED e abra a Operação 01.
3. Inicie uma sessão.
4. Escolha o modo do portal: login local ou acesso direto.
5. Inicie o hotspot pelo botão do RED.
6. Aguarde o Android informar o estado da rede e o RED iniciar o servidor HTTP na porta 8080.
7. Compartilhe com o aparelho B o SSID/senha, o endereço do portal e o código temporário da sessão.
8. Mantenha o painel aberto para acompanhar as requisições, eventos e resultados.

### Aparelho B — participante
1. Conecte-se ao hotspot nas configurações Wi-Fi do Android.
2. Abra o endereço local exibido pelo aparelho A ou leia o QR Code.
3. Faça o login usando somente os dados de teste do laboratório.
4. Responda ao aviso de cookies no navegador.
5. Se houver solicitações de permissões web compatíveis, leia a finalidade e responda aos avisos próprios do navegador.
6. Não é necessário instalar ou abrir o KhaLinux no B.


## Cenário A — portal com login local

- No aparelho A, selecione **Login local** antes de iniciar o hotspot.
- No aparelho B, abra o portal e selecione Acessar Wi-Fi.
- Use somente identificador no padrão `aluno-demo` e código no padrão `LAB-2048`.
- O painel A apresenta os campos sintéticos que o servidor recebeu durante a sessão.
- O código de acesso é validado localmente; não existe autenticação em serviço externo.
- Os valores ficam apenas em memória e são removidos ao parar a rede, limpar os dados temporários ou encerrar a sessão.

## Cenário B — acesso direto sem login

- No aparelho A, selecione **Acesso direto**.
- No aparelho B, abra o portal e continue sem preencher formulário.
- Navegue para Conteúdo, Notícias, Ofertas e para o link que realiza um redirecionamento 302 local.
- No painel A, observe as requisições GET, rotas, status e sequência.
- Na Área BLUE, veja as recomendações defensivas correspondentes.

## Cookies e permissões do navegador

- O aviso oferece escolhas separadas para cookies necessários e categorias opcionais.
- “Aceitar todos” registra consentimento para as categorias de cookies descritas no próprio aviso. Não concede câmera, microfone, localização ou permissões Android.
- Solicitações web de câmera, microfone ou localização só podem ocorrer em contexto seguro e após ação explícita. O navegador exibe seus próprios avisos.
- Se o portal HTTP por IP local não for considerado contexto seguro, o recurso aparece como indisponível; não se força nem se falsifica a permissão.
- O aparelho A registra a escolha de cookies e os estados que o portal receber efetivamente. Não recebe mídia, coordenadas, arquivos ou senhas.


## Roteiro de vídeo

1. Mostrar o aparelho A preparando a rede e exibindo os parâmetros que o Android disponibilizou.
2. Mostrar o aparelho B conectando-se manualmente.
3. Mostrar o endereço/QR Code e a abertura do portal.
4. Responder ao aviso de cookies e observar no A a escolha registrada.\n5. Comparar login local com acesso direto sem login.
5. Clicar nos links e observar o redirecionamento.
6. Mostrar no painel A as requisições realmente recebidas.
7. Se disponível em contexto seguro, solicitar uma permissão web por ação explícita e mostrar o aviso real do navegador.
8. Mostrar o evento de estado no painel A.
9. Abrir BLUE e explicar a defesa correspondente.
10. Não exibir código-fonte, scripts ou segredos de implementação.

## Solução de problemas

- **Hotspot não inicia:** conferir Wi-Fi, localização/serviços do sistema e permissões; o fabricante/versão Android pode restringir LocalOnlyHotspot.
- **SSID ou senha não aparecem:** mostrar como indisponível se o Android não os expuser; não inventar valores.
- **Portal não abre automaticamente:** digitar o endereço HTTP ou usar QR Code. A abertura automática não é garantida.
- **Porta 8080 ocupada:** encerrar a sessão/servidor anterior; o app não deve trocar silenciosamente para outra porta.
- **Servidor sem IPv4 de interface AP:** o RED não deve abrir em todas as interfaces como fallback. Rever estado da rede e registrar indisponibilidade.
- **Permissão não aparece:** verificar se já foi concedida, revogada ou bloqueada nas configurações do Android.
- **Evento não chega ao painel:** conferir endereço do aparelho A, porta 8080, código temporário e conexão Wi-Fi do aparelho B.

## Limitações que devem ser explicadas

- O Android controla o hotspot; o app não escolhe arbitrariamente todos os parâmetros.
- O Android não oferece uma lista universal de clientes do LocalOnlyHotspot; o RED confirma atividade do participante quando recebe uma requisição HTTP.
- O portal usa HTTP local sem TLS. Não é HTTPS e não deve ser apresentado como conexão protegida.
- O navegador não ganha acesso a câmera, microfone ou localização por estar conectado, fazer login ou aceitar cookies. APIs sensíveis exigem contexto seguro e uma ação explícita.
- A aula demonstra somente o que o servidor local realmente recebeu.
