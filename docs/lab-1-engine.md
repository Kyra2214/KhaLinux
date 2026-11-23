# LAB-1 — Motor de execução do laboratório

**Estado:** parcial, com o núcleo executável integrado.  
**Branch:** `plan/laboratorio-operacional`  
**Implementação:** LAB-1.1 a LAB-1.5 concluídos em código. LAB-1.6 e LAB-1.7 permanecem pendentes.

## Componentes implementados

| Componente | Arquivo | Responsabilidade |
|---|---|---|
| Contratos | `android/runtime/.../operations/LabOperationModels.kt` | Operação, catálogo, ativo, sessão, execução, evento e evidência |
| Persistência | `LabOperationStore.kt` | Sessões, execuções, eventos e referências em arquivos Properties atômicos |
| Evidências | `LabEvidenceStore.kt` | Arquivos stdout/stderr, referência relativa, tamanho e SHA-256 verificado na leitura |
| Motor | `LabOperationEngine.kt` | Fila limitada, concorrência, estados, cancelamento, timeout, eventos e recuperação |
| Service | `KhaLinuxRuntimeService.operationEngine()` | Instância única associada ao runtime atual |
| Terminal | `TerminalViewModel.launchLabCommand()` | CLI `khalab` usando o mesmo motor |
| RED | `RedViewModel` | Catálogo, sessão, execuções e cancelamento operacional |
| BLUE | `BlueViewModel` | Assinatura dos eventos operacionais persistidos e ao vivo |

## Fluxo implementado

1. O catálogo define o executor, alvo, vetor de argumentos, timeout, dependências declaradas e contrato de saída.
2. O motor cria a execução em estado `QUEUED` e a persiste.
3. A fila tem limite configurável, concorrência padrão 2 e rejeita novas tarefas quando cheia.
4. Antes do comando, o shell do RootFS verifica `command -v` para o executável principal.
5. Cada argumento é escapado individualmente; placeholders ocupam um argumento inteiro e os valores são validados pelo tipo declarado.
6. O runtime existente executa o processo no RootFS. O motor coleta stdout, stderr, código de saída e duração.
7. A saída é limitada a 2 MiB por fluxo para conter consumo de memória; truncamento gera evento explícito.
8. stdout/stderr são persistidos como evidências binárias, com SHA-256, tamanho e referência relativa.
9. Eventos de fila, início, evidência, conclusão, falha, cancelamento e interrupção são persistidos e publicados aos listeners.
10. O cancelamento encerra a sessão do runtime; fechar a sessão operacional cancela as execuções associadas.
11. Na recriação do motor, sessões ativas e execuções não terminais são marcadas como interrompidas, sem simular conclusão.

## CLI operacional do terminal

Disponível no campo de comando não interativo do terminal:

- `khalab help`
- `khalab list`
- `khalab sessions`
- `khalab run runtime.kernel-identity`
- `khalab run network.interfaces`
- `khalab run network.routes`
- `khalab run network.resolver-config`

Esses comandos não são scripts fictícios no RootFS: o ViewModel encaminha o comando para o mesmo `LabOperationEngine` usado pelas interfaces RED/BLUE. A saída apresentada vem dos arquivos de evidência persistidos pelo motor.

## Limitações registradas

### LAB-1.6 — sinal de término
O backend atual usa `java.lang.Process.waitFor()`, que entrega o código de saída, mas não expõe de forma separada o número do sinal Unix que encerrou o processo. Não inferir sinal a partir de códigos 128+N, pois um programa também pode retornar esses códigos normalmente. A captura exata exige ampliar o launcher nativo para expor o status bruto de `waitpid`. Até lá, o registro contém exit code, timeout e cancelamento conhecido; sinal fica pendente.

### LAB-1.7 — pré-condições
O motor verifica a disponibilidade do executável principal com `command -v`. Ainda não valida todos os pacotes declarados, dependências transitivas, permissões de API Android ou capacidades de cada adaptador. A presença de um pacote no catálogo APT não é tratada como instalação confirmada.

### Escopo de executores
O motor executa `ROOTFS_ARGV` para alvos `LOCAL_RUNTIME` e `ADB` para alvos `LOCAL_RUNTIME`/ `ANDROID_DEVICE`. O adaptador ADB, o servidor persistente e as operações de inventário estão documentados em [`lab-2-device-connectivity.md`](lab-2-device-connectivity.md). Os tipos TOOLKIT, ANDROID_API, HTTP_SERVICE e PCAP_ANALYZER permanecem representados no contrato e serão conectados nos módulos correspondentes do roadmap.

### Interface
O terminal e os ViewModels RED/BLUE já compartilham o backend. A apresentação final das operações, filtros, evidências e investigação fica no LAB-9. Nenhum layout de demonstração foi usado para declarar uma execução concluída.

## Validação

Nenhum teste, build, compilação ou validação foi executado durante esta implementação. As atividades correspondentes permanecem acumuladas para LAB-10.
