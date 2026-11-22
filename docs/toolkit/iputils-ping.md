# ping

- **Grupo:** rede · **Pacote:** `iputils-ping` · **Comandos:** `ping`
- **Verificação:** `ping -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Teste de alcance e latência com ICMP echo.

## Uso autorizado

Não exige confirmação de escopo (uso passivo/local), mas só use contra alvos que você possui ou tem autorização para avaliar.

## Limitações sem root

- ICMP pode ser bloqueado ou exigir privilégios que o Android/PRoot sem root não fornece; resultado ainda não verificado em aparelho.

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install iputils-ping` no RootFS.
