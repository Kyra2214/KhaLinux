# Release do APK (9.3)

> **Estado:** a release assinada foi gerada e verificada no CI. A validação funcional final ainda requer teste em aparelho.

## O que existe

- `android/app/build.gradle.kts`: build type `release` com R8/`shrinkResources` (desligável por `-PkhalinuxMinify=false`), `abiFilters = arm64-v8a`, versão por `-PkhalinuxVersionName`/`-PkhalinuxVersionCode` e assinatura **somente por variáveis de ambiente**. Sem elas, `assembleRelease` gera APK **não assinado**.
- `android/app/proguard-rules.pro`: mantém `NativePty` (símbolos JNI) e `commons-compress`; `-dontwarn` das dependências opcionais ausentes; preserva números de linha.
- `.github/workflows/release-apk.yml`: workflow manual (`workflow_dispatch`) que valida entradas, recusa tag existente (`v<versão>`; imutável), exige os secrets, roda a auditoria estática, assina, **verifica com `apksigner`**, gera `KhaLinux-<versão>.apk` e `.apk.sha256`, publica a release com `RELEASE_NOTES.md`.
- `.github/workflows/ci.yml`: executa os testes unitários sem empacotar APK `debug`; após validações passarem em um push para `main`, compila e assina um APK `release` minificado, verifica a assinatura e envia APK + SHA-256 como artefato privado do Actions por 30 dias. O CI não publica APK `debug` nem cria uma release/tag pública; o job release não roda para PRs ou branches de trabalho.

## Chave de assinatura (uma vez, fora do repositório)

```bash
keytool -genkeypair -v -keystore khalinux-release.jks -alias khalinux \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 khalinux-release.jks   # valor do secret KHALINUX_KEYSTORE_BASE64
```

Cadastre em *Settings → Secrets and variables → Actions*: `KHALINUX_KEYSTORE_BASE64`, `KHALINUX_KEYSTORE_PASSWORD`, `KHALINUX_KEY_ALIAS`, `KHALINUX_KEY_PASSWORD`.

- **Guarde o `.jks` e as senhas em local seguro e com cópia.** Perder a chave impede atualizações por cima: o Android recusa APK com assinatura diferente.
- Nunca commite a chave (`.gitignore` e a auditoria bloqueiam `*.jks`, `*.keystore`, `.env*`).

## APK release automático da main (CI)

- Cada push em `main` que passe os testes gera somente o artefato `khalinux-release-main-<run_number>` com APK `release` assinado (`KhaLinux-main-<commit>.apk`) e seu `.sha256`; o CI não monta nem publica APK `debug`.
- O workflow usa a mesma chave de assinatura da distribuição, R8/minificação ativada, `versionName` `1.0.0-main.<run_number>` e `versionCode=8` (reservado para os APKs automáticos da main). O artefato fica disponível em *Actions → KhaLinux CI → Artifacts* por 30 dias.
- A geração automática não cria uma release pública. Para publicar uma nova release/tag, use o workflow manual abaixo e escolha `version_code` maior que 8.

## Gerar uma release

1. Atualize `RELEASE_NOTES.md` e o `CHANGELOG.md`.
2. *Actions → Build and publish signed release APK → Run workflow*, com `version_name` (`1.0.0`) e `version_code` (inteiro maior que o anterior).
3. Baixe o APK e o `.sha256` da release.

## Verificar um APK (usuário)

```bash
sha256sum -c KhaLinux-1.0.0.apk.sha256
apksigner verify --print-certs KhaLinux-1.0.0.apk
```

Compare o resumo do certificado com o publicado nas notas da release.

## Build local para desenvolvimento (fora do CI)

Os comandos abaixo são opcionais para desenvolvimento local. O CI não gera nem envia APK `debug`; os APKs distribuídos pelo pipeline são da variante `release`.

```bash
./gradlew :android:app:assembleDebug                        # APK de depuração
./gradlew :android:app:assembleRelease -PkhalinuxMinify=false  # release sem R8, não assinado
```

## Riscos e pendências

- R8 pode remover/renomear algo usado por reflexão (Room, commons-compress, JNI). As regras são conservadoras, mas **não foram exercitadas**; comparar o comportamento do release com o debug em aparelho.
- `abiFilters = arm64-v8a` só no release; o debug mantém todas as ABIs do módulo nativo.
- Sem `bundleRelease`/Play Store: distribuição é por APK direto.
- Rotação/revogação de chave não prevista nesta versão.
