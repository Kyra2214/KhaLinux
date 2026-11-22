# DNS Utils (dig, nslookup, host)

- **Grupo:** reconhecimento · **Pacote:** `dnsutils` · **Comandos:** `dig`, `nslookup`, `host`
- **Verificação:** `dig -v` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Consultas DNS: registros A/AAAA/MX/TXT/NS, resolução reversa e diagnóstico de resolvedores.

## Uso autorizado

Não exige confirmação de escopo (uso passivo/local), mas só use contra alvos que você possui ou tem autorização para avaliar.

## Limitações sem root

- Depende do resolvedor definido em /etc/resolv.conf (padrão 1.1.1.1/8.8.8.8, suposição do projeto).

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install dnsutils` no RootFS.
