# Scripts (7.4)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- Armazenamento no host: `filesDir/home/scripts/*.sh`, que o bind do home persistente expõe em `/home/khalinux/scripts`; os scripts também podem ser editados de dentro do Linux.
- Nome: começa com letra/número, `[A-Za-z0-9._-]`, até 64 caracteres, sem `..`; sem extensão ganha `.sh`. Como só nomes validados chegam ao comando, não há injeção no `bash <caminho>`.
- Execução: comando não interativo na aba ativa (respeita um comando por aba). Scripts rodam com as mesmas limitações do PRoot e **sem** o gate de escopo do toolkit: o usuário é responsável por só atacar alvos autorizados.
- Suposição: sem permissão de execução (`chmod`) — sempre `bash script`. Sem importar/exportar arquivos (7.5).
