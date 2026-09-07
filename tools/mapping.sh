#!/usr/bin/env bash
# Печатает сигнатуры класса Minecraft в маппингах Yarn для 1.20.1.
#
# Зачем: имена методов в Yarn часто не те, что подсказывает память или опыт
# других версий. Проверка занимает секунды и уже несколько раз спасала от
# правок вслепую (createError против throwGameTestException, setDimensions,
# setPositionTarget). Правило простое: сначала посмотреть, потом писать код.
#
#   tools/mapping.sh net.minecraft.test.TestContext
#   tools/mapping.sh net.minecraft.entity.Entity remove
#   tools/mapping.sh 'net.minecraft.entity.EntityType$Builder'
#
# Второй аргумент — фильтр без учёта регистра по строке сигнатуры.
set -uo pipefail

CLASS="${1:-}"
FILTER="${2:-}"

if [ -z "$CLASS" ]; then
    echo "нужно имя класса, например: tools/mapping.sh net.minecraft.util.math.BlockPos" >&2
    exit 2
fi

VER="1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/fabric-loom/minecraftMaven/net/minecraft"
COMMON="$CACHE/minecraft-common/$VER/minecraft-common-$VER.jar"
CLIENT="$CACHE/minecraft-clientonly/$VER/minecraft-clientonly-$VER.jar"

# javap здесь -- windows-программа, а пути POSIX-овые. Оболочка MSYS пытается
# помочь и переписывает аргументы, похожие на пути, но на строке из двух путей
# через точку с запятой ломает их молча: javap получает мусор и отвечает
# "class not found" на любой класс. Отсюда два лекарства сразу -- отключить
# преобразование и отдать пути в windows-виде самому.
towin() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        printf '%s' "$1"
    fi
}

if [ ! -f "$COMMON" ]; then
    echo "не найден jar с маппингами: $COMMON" >&2
    echo "сначала выполни ./gradlew build — Loom положит его в кэш" >&2
    exit 1
fi

JAVAP="${JAVAP:-/c/Program Files/Java/jdk-17/bin/javap.exe}"
if [ ! -x "$JAVAP" ]; then
    JAVAP="javap"
fi

# На Windows разделитель classpath — точка с запятой, а не двоеточие.
CP="$(towin "$COMMON");$(towin "$CLIENT")"

OUT=$(MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'       "$JAVAP" -classpath "$CP" "$CLASS" 2>&1       | grep -vE "^Compiled from|Picked up _JAVA_OPTIONS")

# Опечатка в имени класса и сломанный classpath дают одну и ту же строку,
# поэтому различаем их здесь, а не гадаем потом.
if printf '%s' "$OUT" | grep -q "class not found"; then
    echo "$OUT" >&2
    if command -v cygpath >/dev/null 2>&1; then
        echo "проверь имя класса: в Yarn 1.20.1 это, например, net.minecraft.structure.StructureTemplate" >&2
    else
        # Без cygpath пути уходят в javap в POSIX-виде, и он не находит вообще
        # ничего. Иначе это сообщение врало бы про опечатку в имени класса.
        echo "cygpath не найден, поэтому classpath ушёл в javap в POSIX-виде и не открылся." >&2
        echo "Запусти скрипт из Git Bash или задай JAVAP и пути вручную." >&2
    fi
    exit 1
fi

if [ -n "$FILTER" ]; then
    echo "$OUT" | grep -i -- "$FILTER"
else
    echo "$OUT"
fi
