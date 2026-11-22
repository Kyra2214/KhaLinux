# whois

- **Grupo:** reconhecimento · **Pacote:** `whois` · **Comandos:** `whois`
- **Verificação:** `whois --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Consulta de registro de domínios e alocações de IP em servidores WHOIS públicos.

## Uso autorizado

Não exige confirmação de escopo (diagnóstico/consulta pública), mas só use contra alvos que você possui ou tem autorização para avaliar.

## Limitações sem root

- Servidores WHOIS aplicam limites de consulta; depende de rede.

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install whois` no RootFS. A versão do catálogo foi lida em packages.debian.org (bookworm) e ainda precisa ser conferida no snapshot APT fixado.
