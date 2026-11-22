# traceroute

- **Grupo:** rede · **Pacote:** `traceroute` · **Comandos:** `traceroute`
- **Verificação:** `traceroute --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Caminho de pacotes IP até um host, para diagnóstico de conectividade.

## Uso autorizado

Não exige confirmação de escopo (diagnóstico/consulta pública), mas só use contra alvos que você possui ou tem autorização para avaliar.

## Limitações sem root

- Modos ICMP (-I) e TCP (-T) podem exigir privilégios indisponíveis sem root; modo padrão não verificado.

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install traceroute` no RootFS. A versão do catálogo foi lida em packages.debian.org (bookworm) e ainda precisa ser conferida no snapshot APT fixado.
