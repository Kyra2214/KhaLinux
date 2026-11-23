# Validação final

A validação de infraestrutura e a conclusão do laboratório operacional são marcos distintos. O CI nº 284 executado no commit `49648c9a7bed05fddce2053aae28ebab0aa9902c`, posteriormente integrado à `main`, passou em todos os jobs.

## CI nº 284 — 2026-11-23

| Verificação | Resultado |
|---|---|
| RootFS/configuração e sintaxe dos scripts | OK |
| Auditoria estática de segurança | OK |
| JNI/PTy no host | OK |
| Smoke test ARM64 RootFS com PRoot | OK |
| Testes unitários runtime e Android | OK |
| Compilação `:android:app:assembleDebug` | OK |
| Artefato `khalinux-debug-apk` | Gerado e publicado no artifact do workflow |
| APK Release com R8 e assinatura | Não executado |
| Testes físicos em Android | Pendentes |

Workflow: [CI nº 284](https://github.com/Kyra2214/KhaLinux/actions/runs/45138571275).  
Merge que integrou o commit validado: `b14812e934d10fa81af890be9aea461badc4ce69`.

## O que ainda falta na validação da infraestrutura

- Build `assembleRelease` com R8 e assinatura.
- Verificação da assinatura com `apksigner verify`.
- Instalação limpa, atualização e recuperação em aparelho Android ARM64.
- Verificação do PRoot, RootFS, terminal, PTY e ferramentas no dispositivo.
- Medições de memória, inicialização e bateria.
- Testes físicos com dois aparelhos para as operações do laboratório, somente após a implementação operacional.

## Estado do laboratório operacional

O CI nº 284 valida a compilação Debug e os testes automatizados existentes. Ele **não valida o laboratório RED/BLUE/PURPLE operacional**, nem substitui os testes de integração com os dois aparelhos.

A direção do produto foi corrigida em 2026-11-23. O roadmap operacional está em [roadmap-area-red-completa.md](roadmap-area-red-completa.md), com a sequência LAB-0 a LAB-10. O protótipo atual de hotspot/portal e as telas RED/BLUE serão auditados e evoluídos para operações reais.

**Conclusão:** a integração CI da infraestrutura passou; o APK Release ainda não foi produzido; a validação física e a implementação do laboratório operacional continuam pendentes.
