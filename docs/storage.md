# Higiene de armazenamento (8.1)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- `StorageHygiene` (runtime): `report()` separa áreas **preservadas** (RootFS, PRoot, home) de **resíduos** removíveis; `cleanup(installRunning)` remove só resíduos.
- Elegíveis: `.<nome>.staging` (sempre); `.<nome>.download` parcial só após 7 dias (preserva a retomada); `.<nome>.previous` só se o destino existe (se não existe pode ser a única cópia utilizável: restauração é o 8.5); temporários de pacotes (`runtime/packages/tmp`) com mais de 1 dia. Resíduos de pacotes em `rootfs/opt/khalinux` seguem as mesmas regras.
- Nunca toca em RootFS instalado, PRoot instalado, home do usuário, notas e relatórios; nada é feito durante uma instalação; links simbólicos não são seguidos.
- Quando roda: na criação do Service (fila do instalador, serial com a instalação) e sob demanda no botão "Limpar resíduos de instalação" da tela Arquivos, que mostra espaço usado e resíduos removíveis.
- Já existente (não refeito): checagem de espaço antes de baixar (`installRootfsBlocking`/`installProotBlocking`) e remoção do arquivo baixado após a instalação.
- Suposição: 7 dias para `.download` e 1 dia para temporários são valores iniciais, sem medição em aparelho.
- Validação pendente: `StorageHygieneTest` (escrito, não executado).
