# RESUME_SESSION — KhaLinux / Área RED

- Repositório: `Kyra2214/KhaLinux`.
- Branch de trabalho: `plan/area-red-wifi-publico` (não alterar `main`; não gerar ZIP/checkpoints).
- Última alteração confirmada nesta retomada: `49e0ed646e95c3ea49c02f8cc5e138826d10fb7f` — RED-3: porta do servidor HTTP configurável, padrão 8080.
- Estado do roadmap RED: RED-0 a RED-5 implementados em código/documentação (6/14 = 43%); RED-6 é o próximo submódulo. RED-13 é exclusivamente a validação final.
- RED-3: `RedPortalServer` aceita `configuredPort`, valida 1024..65535 e mantém bind apenas no IPv4 da interface Wi-Fi/AP identificada.
- RED-4: servidor contém portal local com modo de login de laboratório e acesso direto, páginas locais e redirecionamento 302.
- RED-5 confirmado por inspeção: POST local, validação estrita de `demo_id`/`demo_code`, visualização efêmera no painel e ausência dos valores no histórico/relatório. Próximo: RED-6 navegação, RED-7 permissões reais no aparelho participante, RED-8 observabilidade, RED-9 relatórios/retensão, RED-10 integração BLUE, RED-11 aulas/documentação e RED-12 fechamento.
- Regras ativas: não executar testes, builds, compilação, auditorias, simulações ou testes físicos até RED-0..RED-12 estarem integralmente implementados. Não remover os requisitos de validação; acumular em RED-13.
- Nenhum teste/build foi executado nesta retomada. Não declarar funcionalidades validadas. Não fazer merge sem autorização.
- Próxima ação: iniciar RED-5, inspecionando `RedLabInputValidator`, `RedPortalServer`, `RedViewModel`, `RedScreen` e os modelos de submissão antes de alterar código.
