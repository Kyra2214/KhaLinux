# RED-01 — Nunca Conecte à Wi-Fi Pública

## Objetivo

Executar uma simulação controlada de risco em redes Wi-Fi públicas usando dois aparelhos Android próprios ou autorizados.

## Pré-requisitos

- Aparelho A: operador RED.
- Aparelho B: participante do laboratório.
- Wi-Fi e serviço de localização disponíveis quando exigidos pelo Android.
- Participante informado de que está em um exercício.
- Não usar redes de estabelecimentos, credenciais reais ou dispositivos de terceiros.

## Preparação e execução

1. Abra Área RED e selecione **Nunca Conecte à Wi-Fi Pública**.
2. Leia o escopo, objetivos e tutorial.
3. Inicie a sessão.
4. Conceda a permissão de localização solicitada no próprio aparelho operador, se o Android exigir para o hotspot local.
5. Toque em **Iniciar hotspot e portal local**. O app solicita ao Android um hotspot local; SSID e senha são definidos pelo sistema e mostrados quando disponíveis.
6. No aparelho B, abra as configurações Wi-Fi e conecte-se ao hotspot do laboratório usando os dados apresentados.
7. No aparelho B, leia o QR Code ou digite o endereço local mostrado pelo aparelho A.
8. Percorra o formulário demonstrativo usando apenas valores fictícios.
9. O envio da página é um evento fixo de conclusão. Os campos de e-mail e senha não são incluídos na requisição; o servidor não interpreta nem persiste o corpo recebido.
10. No aparelho A, acompanhe eventos e progresso, copie ou exporte o relatório Markdown.
11. Toque em **Parar hotspot e portal** e depois em **Encerrar sessão**.

## O que o portal demonstra

A página está marcada de forma permanente como **SIMULAÇÃO DE LABORATÓRIO**. Ela demonstra como uma tela de acesso a Wi-Fi pode induzir o usuário a preencher um formulário. Não representa Google, Facebook, Instagram ou qualquer outro serviço real.

O navegador envia apenas o evento sintético `demo=completed`. Os valores exibidos nos campos são locais à página e não são enviados ao servidor. O servidor registra a visita e a conclusão, não os valores digitados.

## Limitações do Android

- `LocalOnlyHotspot` é controlado pelo sistema e pode falhar por política do fabricante, permissões, localização desligada ou incompatibilidade.
- O Android não garante abertura automática do portal.
- O endereço local só é mostrado quando a interface Wi-Fi do laboratório pode ser identificada.
- O QR Code e o endereço manual dependem de ambos os aparelhos estarem conectados à mesma rede de laboratório.

## Encerramento

O encerramento fecha o servidor, libera a reserva do hotspot e registra o evento local. Sessões deixadas abertas após fechamento inesperado são marcadas como interrompidas na próxima abertura da Área RED.
