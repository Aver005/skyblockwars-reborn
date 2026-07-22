#!/usr/bin/env bash
# ============================================================================
#  Переименование шаблона MCMGP под новый проект.
#  Меняет: Java-пакет, имя плагина, команду, права, главный класс, артефакт.
#
#  Использование (из корня репозитория):
#     ./rename.sh <new.base.package> <PluginName> <command>
#  Пример:
#     ./rename.sh com.acme.spleef Spleef spleef
#
#  После запуска: проверьте git diff, затем ./gradlew build.
#  Скрипт правит файлы НА МЕСТЕ — сделайте коммит/бэкап до запуска.
# ============================================================================
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: ./rename.sh <new.base.package> <PluginName> <command>"
  echo "Example: ./rename.sh com.acme.spleef Spleef spleef"
  exit 1
fi

NEW_PKG="$1"          # com.acme.spleef
NEW_NAME="$2"         # Spleef
NEW_CMD="$3"          # spleef
MAIN_CLASS="${NEW_NAME}Plugin"

OLD_PKG="ru.kiviuly.mcmgp"
OLD_PKG_PATH="ru/kiviuly/mcmgp"
NEW_PKG_PATH="${NEW_PKG//.//}"

if [ ! -f "build.gradle.kts" ] || [ ! -d "src/main/java/${OLD_PKG_PATH}" ]; then
  echo "ERROR: run from the template repo root (build.gradle.kts + src/main/java/${OLD_PKG_PATH} expected)."
  exit 1
fi

echo "Renaming: ${OLD_PKG} -> ${NEW_PKG} | MCMGP -> ${NEW_NAME} | /mg -> /${NEW_CMD}"

# Список правимых текстовых файлов (без build/, .git/, бинарного wrapper).
mapfile -t FILES < <(find . -type f \
  \( -name '*.java' -o -name '*.kts' -o -name '*.yml' -o -name '*.md' \) \
  -not -path './build/*' -not -path './.git/*' -not -path './gradle/*')

repl() {
  local from="$1" to="$2"
  for f in "${FILES[@]}"; do
    sed -i "s|${from}|${to}|g" "$f"
  done
}

# Порядок важен: сначала специфичные шаблоны команды, потом общие идентификаторы.
repl 'getCommand("mg")'        "getCommand(\"${NEW_CMD}\")"
repl 'performCommand("mg '     "performCommand(\"${NEW_CMD} "
repl '/mg'                     "/${NEW_CMD}"
repl 'ru\.kiviuly\.mcmgp'      "${NEW_PKG}"
repl 'McmgpPlugin'             "${MAIN_CLASS}"
repl 'mcmgp\.admin'            "${NEW_CMD}.admin"
repl 'MCMGP'                   "${NEW_NAME}"

# plugin.yml: ключ команды и rootProject.name — точечно.
sed -i "s|^  mg:|  ${NEW_CMD}:|" src/main/resources/plugin.yml
sed -i "s|group = \"ru.kiviuly\"|group = \"${NEW_PKG}\"|" build.gradle.kts
sed -i "s|rootProject.name = \"mcmgp-template\"|rootProject.name = \"${NEW_CMD}\"|" settings.gradle.kts

# Перенос каталога пакета.
NEW_PARENT="src/main/java/$(dirname "${NEW_PKG_PATH}")"
mkdir -p "${NEW_PARENT}"
if [ "${NEW_PKG_PATH}" != "${OLD_PKG_PATH}" ]; then
  git mv "src/main/java/${OLD_PKG_PATH}" "src/main/java/${NEW_PKG_PATH}" 2>/dev/null \
    || mv "src/main/java/${OLD_PKG_PATH}" "src/main/java/${NEW_PKG_PATH}"
  # подчистить осиротевшие пустые каталоги старого пакета
  find src/main/java/ru -type d -empty -delete 2>/dev/null || true
fi

# Переименование файла главного класса.
NEW_DIR="src/main/java/${NEW_PKG_PATH}"
if [ -f "${NEW_DIR}/McmgpPlugin.java" ]; then
  git mv "${NEW_DIR}/McmgpPlugin.java" "${NEW_DIR}/${MAIN_CLASS}.java" 2>/dev/null \
    || mv "${NEW_DIR}/McmgpPlugin.java" "${NEW_DIR}/${MAIN_CLASS}.java"
fi

echo "Done. Review the diff, then: ./gradlew build"
