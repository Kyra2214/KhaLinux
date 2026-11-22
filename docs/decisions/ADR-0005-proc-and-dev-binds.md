# ADR-0005: /proc e /sys não são montados do Android

**Estado:** aceito (3.5). **Contexto:** o plano de correção pedia "avaliar bind de /proc".

**Decisão:** manter a política atual (`AndroidBindMountPolicy`): só `/dev/null, zero, random, urandom, tty, pts` são ligados. `/proc` e `/sys` do Android **não** entram no Debian por padrão.

**Motivos:** expõem processos e informações do aparelho ao convidado; o PRoot já emula os caminhos `/proc/self/*` necessários ao próprio rastreamento; o teste `AndroidBindMountPolicyTest` fixa essa política.

**Consequências:** `ps`, `top`, `free` e `mount` podem mostrar dados incompletos ou falhar. Se a Fase 4/5 exigir essas ferramentas, adotar um bind **opt-in**, somente leitura de fato (o PRoot não implementa `ro`), com análise de exposição e teste dedicado. Nenhum bind novo foi implementado.
