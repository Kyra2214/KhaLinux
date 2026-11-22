# Laboratórios (Fase 6)

Camada de aprendizado prático sobre o Cyber Toolkit. Dados em `distribution/labs/labs.json` (schema 1); código em `android/runtime/.../labs/`.

## Modelo (6.1)

- **Laboratório** (`LabDescriptor`): id, trilha (`linux`, `rede`, `web`, `forense`), título, resumo, dificuldade (`iniciante` 10 pts, `intermediario` 20, `avancado` 40 — pontuação aplicada no motor), `required_tools` (devem existir no `tools.json`), `prerequisites` (outros laboratórios; sem ciclos), `services`, `exercises` e `doc` (`docs/labs/<id>.md`).
- **Exercício** (`LabExercise`): enunciado, dificuldade, até 5 dicas com custo (0–100), resposta guardada só como `answer_sha256` da resposta normalizada (`AnswerHasher`: trim, espaços colapsados, minúsculas se `ignore_case`). O hash não impede descobrir a resposta por tentativa; apenas não a entrega junto com o enunciado.
- **Serviço** (`LabService`): `bind` só `127.0.0.1`, `::1` ou `localhost`; porta 1024–65535; comando sem metacaracteres de shell. Não existe campo de alvo externo no modelo.
- **Catálogo** (`LabCatalog`/`LabCatalogParser`): ids únicos, pré-requisitos existentes e sem ciclos, ferramentas exigidas presentes no toolkit, campos desconhecidos rejeitados; `available(completed)` lista o que já pode ser feito.

## Motor de exercícios (6.2)

- `LabEngine.open(labId, completed)` valida pré-requisitos (`LabLockedException` se faltar algum) e devolve uma `LabSession`.
- `LabSession`: `submit(exercicio, resposta)` compara o SHA-256 da resposta normalizada com `answer_sha256` (tempo constante) e retorna `Correct`/`Incorrect`/`AlreadySolved`/`EmptyAnswer`; `revealNextHint` revela dicas só em ordem e não depois de resolvido; `state`, `snapshot`, `isCompleted`, `totalPoints`.
- Pontuação (`LabScoring`): pontos base da dificuldade do exercício − custo das dicas reveladas − 1 ponto por tentativa errada além de 3; nunca abaixo de 25% do base.
- Estado em memória na sessão; `restored` no `open` e `snapshot()` alimentam a persistência de 6.3. Sem limite de tentativas (o objetivo é didático).

## Progresso persistido (6.3)

- `LabProgressStore(file)`: linhas `E<TAB>lab<TAB>exercicio<TAB>erradas<TAB>dicas<TAB>resolvido<TAB>pontos` e `C<TAB>lab`; gravação em arquivo temporário + renomeação. Linhas inválidas são ignoradas; valores negativos viram 0; pontos só contam se `resolvido`.
- `save(session)` grava o snapshot e marca o laboratório como concluído quando todos os exercícios foram resolvidos. `resetLab` apaga estado e conclusão de um laboratório; `resetAll` apaga tudo.
- `LabProgressService`: `open` restaura o estado via `LabEngine.open(..., restored)`; `submit` e `revealNextHint` gravam após cada ação; `progress().totalPoints` dá o total.
- Ids de laboratório e exercício não podem conter TAB/quebra de linha (garantido por `require` na gravação).
- Reiniciar um laboratório não revoga a conclusão de dependentes já registrados (suposição).

## Fixtures dos serviços (6.6)

Serviços vulneráveis são scripts Python (stdlib) em `distribution/labs/fixtures/<trilha>/`, previstos para `/opt/khalinux-labs/<trilha>/` no RootFS; o `command` do serviço não pode ter aspas nem metacaracteres, então o código mora em arquivo. Cada script fixa `127.0.0.1` no código e valida a porta (1024–65535). A cópia para o RootFS será feita pelo app em 6.8 (suposição).

## Escopo e segurança

Vulnerabilidades só em loopback e sempre dentro do RootFS (PRoot não é isolamento forte). Nenhum laboratório aponta para sistemas de terceiros.

## Estado

`labs.json` tem 3 laboratórios Linux (6.4) 3 de rede (6.5), 3 Web (6.6) e 3 de forense (6.7), em `docs/labs/`. A UI (6.8) lista trilhas e bloqueia laboratórios até concluir os pré-requisitos; a validação da fase está pendente (`docs/phase6-validation.md`). O serviço de rede serve o diretório atual do shell (sem fixtures empacotadas). UI/trilhas (6.8) ainda não existem. Nenhum teste foi executado ou criado para 6.1 (política por fase); o código foi apenas compilado no host.

## Suposições

- Respostas por hash SHA-256 (sem sal) bastam para esta fase; o objetivo é didático, não proteger segredos.
- Pontuação por dificuldade fixa (10/20/40); custo de dica e tentativas extras descontados, com piso de 25%.
