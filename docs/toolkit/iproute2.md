# iproute2 (ip, ss)

- **Grupo:** rede · **Pacote:** `iproute2` · **Comandos:** `ip`, `ss`
- **Verificação:** `ip -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Inspeção de interfaces, endereços, rotas e sockets.

## Uso autorizado

Não exige confirmação de escopo (uso passivo/local), mas só use contra alvos que você possui ou tem autorização para avaliar.

## Limitações sem root

- Alterar interfaces/rotas exige privilégios indisponíveis sem root.
- /proc e /sys não são montados no RootFS (ADR-0005); ss e partes de ip podem falhar.

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install iproute2` no RootFS.
