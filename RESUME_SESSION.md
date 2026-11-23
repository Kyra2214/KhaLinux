# RESUME_SESSION — KhaLinux / Área RED

- Repositório: `Kyra2214/KhaLinux`.
- Branch de trabalho: `plan/area-red-wifi-publico` (não alterar `main`; não gerar ZIP/checkpoints).
- Última alteração confirmada nesta retomada: `49e0ed646e95c3ea49c02f8cc5e138826d10fb7f` — RED-3: porta do servidor HTTP configurável, padrão 8080.
- Estado do roadmap RED: RED-0 a RED-10 implementados em código/documentação (11/14 = 79%); RED-11 é o próximo submódulo. RED-13 é exclusivamente a validação final.
- RED-3: `RedPortalServer` aceita `configuredPort`, valida 1024..65535 e mantém bind apenas no IPv4 da interface Wi-Fi/AP identificada.
- RED-4: servidor contém portal local com modo de login de laboratório e acesso direto, páginas locais e redirecionamento 302.
- RED-5 confirmado por inspeção: POST local, validação estrita de `demo_id`/`demo_code`, visualização efêmera no painel e ausência dos valores no histórico/relatório. RED-6 confirmado por inspeção: páginas locais, modo sem login, links internos, redirect 302 e linha do tempo de requisições. RED-7 confirmado e complementado: permissões Android reais, prévia local de câmera, medidor local de microfone, localização local, seletor SAF e reporte de estado; revogação agora é atualizada ao retomar o app. RED-8: painel exibe requisições, metadados, eventos e resumo por recurso do último estado de câmera/microfone/localização/arquivos. RED-9 confirmado: histórico com limites, recuperação de sessão, relatório Markdown sem valores sensíveis e exportação/exclusão. RED-10: BLUE lê a mesma sessão e evidências RED; agora exibe metadados HTTP permitidos e estado mais recente dos recursos com leitura defensiva. Próximo: RED-11 documentação e experiência de aula, RED-7 permissões reais no aparelho participante, RED-8 observabilidade, RED-9 relatórios/retensão, RED-10 integração BLUE, RED-11 aulas/documentação e RED-12 fechamento.
- Regras ativas: não executar testes, builds, compilação, auditorias, simulações ou testes físicos até RED-0..RED-12 estarem integralmente implementados. Não remover os requisitos de validação; acumular em RED-13.
- Nenhum teste/build foi executado nesta retomada. Não declarar funcionalidades validadas. Não fazer merge sem autorização.
- Próxima ação: iniciar RED-5, inspecionando `RedLabInputValidator`, `RedPortalServer`, `RedViewModel`, `RedScreen` e os modelos de submissão antes de alterar código.
