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
3. Navegue pelo portal e observe as páginas e os links.
4. Para a trilha de permissões, abra a Área RED e selecione **Modo participante**.
5. Informe o endereço HTTP do aparelho A e o código temporário da sessão.
6. Execute as demonstrações de câmera, microfone, localização ou seleção de arquivo no próprio aparelho B.
7. Quando quiser registrar o resultado, toque em **Registrar resultado no painel do instrutor**. Somente tipo e estado da ação são enviados.

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

## Permissões no aparelho B

- O login do portal e a conexão Wi-Fi não concedem permissões do Android.
- Câmera: o Android solicita CAMERA; a prévia CameraX aparece localmente no B.
- Microfone: o Android solicita RECORD_AUDIO; o app mede nível de áudio e descarta as amostras.
- Localização: o Android solicita localização; a posição aparece somente no B.
- Arquivos: o seletor SAF permite escolha explícita; o app não lê nome/conteúdo.
- O painel A recebe somente tipo e estado do evento, nunca mídia, coordenadas ou conteúdo de arquivos.
- Se o Android negar, cancelar ou não disponibilizar um recurso, o resultado deve permanecer como negado/cancelado/indisponível.

## Roteiro de vídeo

1. Mostrar o aparelho A preparando a rede e exibindo os parâmetros que o Android disponibilizou.
2. Mostrar o aparelho B conectando-se manualmente.
3. Mostrar o endereço/QR Code e a abertura do portal.
4. Comparar login local com acesso direto sem login.
5. Clicar nos links e observar o redirecionamento.
6. Mostrar no painel A as requisições realmente recebidas.
7. Executar uma permissão no B e mostrar o diálogo real do Android e a prévia local.
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
- **Câmera/microfone sem prévia:** conferir permissões, disponibilidade do hardware e uso por outro aplicativo.
- **Localização indisponível:** habilitar serviços de localização; o provedor pode não retornar posição atual.
- **Evento não chega ao painel:** conferir endereço do aparelho A, porta 8080, código temporário e conexão Wi-Fi do aparelho B.

## Limitações que devem ser explicadas

- O Android controla o hotspot; o app não escolhe arbitrariamente todos os parâmetros.
- O Android não oferece uma lista universal de clientes do LocalOnlyHotspot; o RED confirma atividade do participante quando recebe uma requisição HTTP.
- O portal usa HTTP local sem TLS. Não é HTTPS e não deve ser apresentado como conexão protegida.
- O navegador não ganha acesso nativo à câmera, microfone ou localização por estar conectado ou por preencher login.
- A aula demonstra somente o que o servidor local realmente recebeu.
