# RESUME_SESSION — KhaLinux / Laboratório operacional

- Repositório: `Kyra2214/KhaLinux`.
- Base integrada: `main`, merge do PR #18 em 2026-11-23, commit `b14812e934d10fa81af890be9aea461badc4ce69`.
- Branch atual de planejamento: `plan/laboratorio-operacional`.
- Direção corrigida: o laboratório operacional RED/BLUE/PURPLE é o produto principal. Debian ARM64, PRoot, runtime Android, terminal, APT, toolkit e integração Android são a infraestrutura para executá-lo.
- Roadmap mestre: `docs/roadmap-area-red-completa.md`.
- Roadmap principal: `ROADMAP.md`.
- Operação Wi-Fi: `docs/roadmap-area-red-estagio-01-wifi-publico.md`.
- O RED Wi-Fi existente é protótipo de hotspot/portal local e painel; não representa uma suíte operacional de avaliação de dispositivos e redes.
- O BLUE atual é primeira integração de evidências; detecção, investigação e resposta operacionais ainda precisam ser construídas.
- O conteúdo de exercícios da antiga Fase 6 é complementar e não conta como laboratório operacional.
- CI nº 284 passou no commit anterior ao merge, incluindo testes unitários Android e build APK Debug. Isso não comprova APK Release nem validação física.
- Testes físicos com dois aparelhos continuam reservados à etapa final.
- Não gerar ZIP/checkpoints. Não fazer merge para `main` sem autorização explícita.
- Não executar testes/builds intermediários durante a implementação; acumular validação para LAB-10.
- Próxima ação obrigatória: LAB-0 — auditar o estado atual do repositório, mapear o código reutilizável e classificar infraestrutura, conteúdo didático, protótipos e lacunas operacionais.
