# ============================================================================
#  Переименование шаблона MCMGP под новый проект (PowerShell).
#  Меняет: Java-пакет, имя плагина, команду, права, главный класс, артефакт.
#
#  Использование (из корня репозитория):
#     ./rename.ps1 <new.base.package> <PluginName> <command>
#     или через bat:  rename.bat <new.base.package> <PluginName> <command>
#  Пример:
#     ./rename.ps1 com.acme.spleef Spleef spleef
#
#  После запуска: проверьте git diff, затем ./gradlew build.
#  Скрипт правит файлы НА МЕСТЕ — сделайте коммит/бэкап до запуска.
# ============================================================================
param(
    [Parameter(Mandatory = $true)][string]$NewPkg,
    [Parameter(Mandatory = $true)][string]$NewName,
    [Parameter(Mandatory = $true)][string]$NewCmd
)
$ErrorActionPreference = 'Stop'

$MainClass  = "${NewName}Plugin"
$OldPkgPath = 'ru/kiviuly/mcmgp'
$NewPkgPath = $NewPkg -replace '\.', '/'

if (-not (Test-Path 'build.gradle.kts') -or -not (Test-Path "src/main/java/$OldPkgPath")) {
    Write-Error "Run from the template repo root (build.gradle.kts + src/main/java/$OldPkgPath expected)."
}

Write-Host "Renaming: ru.kiviuly.mcmgp -> $NewPkg | MCMGP -> $NewName | /mg -> /$NewCmd"

$files = Get-ChildItem -Recurse -File -Include *.java, *.kts, *.yml, *.md |
    Where-Object { $_.FullName -notmatch '\\(build|\.git|gradle)\\' }

$utf8 = New-Object System.Text.UTF8Encoding($false)  # без BOM
foreach ($f in $files) {
    $c = [System.IO.File]::ReadAllText($f.FullName)
    # порядок важен: специфичные шаблоны команды до общих идентификаторов
    $c = $c.Replace('getCommand("mg")',   "getCommand(`"$NewCmd`")")
    $c = $c.Replace('performCommand("mg ', "performCommand(`"$NewCmd ")
    $c = $c.Replace('/mg',                "/$NewCmd")
    $c = $c.Replace('ru.kiviuly.mcmgp',   $NewPkg)
    $c = $c.Replace('McmgpPlugin',        $MainClass)
    $c = $c.Replace('mcmgp.admin',        "$NewCmd.admin")
    $c = $c.Replace('MCMGP',              $NewName)   # регистрозависимо: lowercase mcmgp не тронут
    [System.IO.File]::WriteAllText($f.FullName, $c, $utf8)
}

# точечные правки
(Get-Content 'src/main/resources/plugin.yml' -Raw).Replace('  mg:', "  ${NewCmd}:") |
    Set-Content 'src/main/resources/plugin.yml' -NoNewline -Encoding utf8
(Get-Content 'build.gradle.kts' -Raw).Replace('group = "ru.kiviuly"', "group = `"$NewPkg`"") |
    Set-Content 'build.gradle.kts' -NoNewline -Encoding utf8
(Get-Content 'settings.gradle.kts' -Raw).Replace('rootProject.name = "mcmgp-template"', "rootProject.name = `"$NewCmd`"") |
    Set-Content 'settings.gradle.kts' -NoNewline -Encoding utf8

# перенос каталога пакета
if ($NewPkgPath -ne $OldPkgPath) {
    $newParent = Split-Path "src/main/java/$NewPkgPath" -Parent
    New-Item -ItemType Directory -Force -Path $newParent | Out-Null
    Move-Item "src/main/java/$OldPkgPath" "src/main/java/$NewPkgPath"
    # Подчистить осиротевшие пустые каталоги старого пакета. ВАЖНО: удаляем ТОЛЬКО
    # каталоги без файлов — иначе новый пакет под тем же префиксом (напр.
    # ru.kiviuly.skyblockwars остаётся под src/main/java/ru) был бы снесён.
    Get-ChildItem 'src/main/java/ru' -Recurse -Directory -ErrorAction SilentlyContinue |
        Where-Object { @(Get-ChildItem $_.FullName -Recurse -File).Count -eq 0 } |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    if ((Test-Path 'src/main/java/ru') -and
        (@(Get-ChildItem 'src/main/java/ru' -Recurse -File).Count -eq 0)) {
        try { Remove-Item 'src/main/java/ru' -Recurse -Force -ErrorAction Stop } catch {}
    }
}

# переименование файла главного класса
$newDir = "src/main/java/$NewPkgPath"
if (Test-Path "$newDir/McmgpPlugin.java") {
    Move-Item "$newDir/McmgpPlugin.java" "$newDir/$MainClass.java"
}

Write-Host "Done. Review the diff, then: ./gradlew build"
