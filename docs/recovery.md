# Recuperação (8.5)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- **Problema:** os instaladores trocam o destino em dois passos (destino → `.previous`, `.staging` → destino). Se o processo morre entre eles, o destino some e a única versão utilizável fica em `.previous`; o app reinstalava do zero e o instalador apagava essa cópia.
- `InstallRecovery.recover` (chamada em `onCreate` do Service, antes de procurar o runtime): restaura `.rootfs-arm64.previous` → `rootfs-arm64` (se tem `bash`) e `.proot.previous` → `proot` (se arquivo ≥ 64 B) **só quando o destino não existe**; nunca sobrescreve; não age durante instalação; downloads parciais ficam para retomada.
- Estado persistido: fases intermediárias em `install-state.properties` (processo morreu no meio) viram `FAILED` com mensagem "Instalação interrompida … será retomada". O arquivo hoje só é gravado (não há leitor na UI): o registro serve a diagnóstico futuro; a retomada acontece porque o app reinstala o que falta, reaproveitando o `.download` parcial.
- Sessões órfãs: já tratadas (`onDestroy` cancela as sessões; `TerminalTabs.clearSessions` desvincula abas quando o Service reconecta) — não alterado.
- A higiene (8.1) só remove `.previous` quando o destino existe, o que é compatível com esta recuperação.
- Validação pendente: `InstallRecoveryTest` (escrito, não executado); simular queda real durante a troca só é possível em aparelho/CI.
