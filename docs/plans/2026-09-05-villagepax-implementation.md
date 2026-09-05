# Village Pax — план реализации, фазы 0 и 1

**Цель:** довести мод от пустой папки до играбельного вертикального среза 0.1 — игрок находит норманнскую деревню, выполняет квесты, получает чертёж ратуши, основывает колонию, нанимает жителей и смотрит, как билдер строит здания блок за блоком.

**Архитектура:** вся симуляция на сервере в `SettlementManager extends PersistentState`, привязанном к `ServerWorld`. Житель существует как данные всегда, как энтити — только при загруженном чанке. Народы, здания, профессии и квесты загружаются из датапака через `Codec`. Подробности — в [дизайн-документе](2026-09-02-villagepax-design.md).

**Стек:** Minecraft 1.20.1, Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1, Yarn 1.20.1+build.10, Fabric Loom 1.17.20, Java 17 (скачивается Gradle автоматически), JUnit 5, Fabric GameTest.

**modid:** `villagepax` · **корневой пакет:** `com.villagepax`

---

## О детализации этого плана

Фаза 0 расписана до последней команды — она выполняется прямо сейчас и от неё зависит всё остальное.

Задачи фазы 1 заданы **контрактом и приёмочным тестом**, а не готовым кодом. Писать литеральный код для пятнадцати ещё не существующих подсистем — гарантия того, что к пятой задаче он весь устареет. Каждая задача фазы 1 получает свой детальный под-план в момент, когда за неё берёмся, и тогда код в нём будет опираться на уже существующие сигнатуры, а не на предположения о них.

Отдельная оговорка про маппинги: точные сигнатуры Yarn для 1.20.1 подтверждаются первой компиляцией. Там, где ниже приведён код, он может потребовать правки имён методов — это нормальная часть шага «скомпилировать».

---

# ФАЗА 0 — Инфраструктура

**Статус: выполнена 2026-09-05**, коммит `e9c3c6a`. Проверено: `./gradlew build` собирает jar и проходит `IdsTest`; `./gradlew runGametest` поднимает сервер Minecraft 1.20.1, мод инициализируется (`Village Pax: инициализация` в логе, `villagepax 0.1.0-SNAPSHOT` среди 47 загруженных модов), игровой тест проходит.

**Что разошлось с предположениями плана:**

| Предполагалось | Оказалось |
|---|---|
| JDK 17 надо доставить | Уже стоит: `C:\Program Files\Java\jdk-17`. Раньше я видел только JDK 21, потому что смотрел `java -version` — то, что на PATH. Foojay-резолвер не нужен, вместо него `org.gradle.java.installations.paths` |
| Wrapper взять из шаблона Fabric на GitHub | `codeload.github.com` из песочницы недоступен. Wrapper сгенерирован из дистрибутива Gradle, скачанного с `services.gradle.org` напрямую. Остальные нужные хосты — `maven.fabricmc.net`, `repo1.maven.org` — доступны, сборка тянет зависимости нормально |
| Gradle 8.x | Gradle 9.7.1, текущий. Loom 1.17.20 с ним работает на Minecraft 1.20.1 — проверено |
| Игровые тесты в отдельном source set | Пока лежат в `src/main/java/com/villagepax/gametest`. Отдельный source set добавляет возню с конфигурацией Loom без выигрыша на этом этапе; вынесем, когда тестов станет много |
| `runClient` как проверка фазы | Не понадобился: `runGametest` поднимает настоящий сервер и грузит мод, что доказывает больше. `runClient` доступен, но открывает окно игры |

Оставшийся шум в сборке: Gradle ругается на найденный в реестре битый `jre-1.8` (безвредно) и предупреждает о deprecated-возможностях, несовместимых с Gradle 10 — они изнутри Loom, не из наших скриптов.

---

### Задача 0.1 — Репозиторий и структура каталогов

**Файлы:**
- Создать: `.gitignore`
- Создать: структуру каталогов исходников

**Шаг 1. Инициализировать репозиторий**

```bash
git init
```

Ожидается: `Initialized empty Git repository`.

**Шаг 2. Создать `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
out/
classes/

# IDEA
.idea/
*.iml
*.ipr
*.iws

# Eclipse / VSCode
.classpath
.project
.settings/
.vscode/

# Fabric Loom
run/
remappedSrc/

# OS
.DS_Store
Thumbs.db
```

**Шаг 3. Создать дерево каталогов**

```bash
mkdir -p src/main/java/com/villagepax/{core,sim,entity,net,block,item,datagen}
mkdir -p src/main/resources/{assets/villagepax,data/villagepax}
mkdir -p src/client/java/com/villagepax/client
mkdir -p src/test/java/com/villagepax
mkdir -p src/gametest/java/com/villagepax/gametest
```

**Шаг 4. Проверить**

```bash
find src -type d | sort
```

Ожидается: список из 12+ каталогов, без ошибок.

---

### Задача 0.2 — Сборка Gradle

**Файлы:**
- Создать: `gradle.properties`, `settings.gradle`, `build.gradle`
- Получить: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`

**Шаг 1. Получить Gradle wrapper**

Глобального Gradle в системе нет, поэтому wrapper берём из официального шаблона Fabric (он под CC0, использовать как основу можно):

```bash
curl -L -o /tmp/fem.zip https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/1.20.1.zip
```

Если `curl` в песочнице недоступен — запасной путь: скачать дистрибутив Gradle и один раз выполнить `gradle wrapper`. Проверяется на месте.

Ожидается: файл скачан, `unzip -l` показывает `gradle/wrapper/gradle-wrapper.jar`.

**Шаг 2. Перенести только wrapper, остальное не трогать**

Из архива берём `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. Файлы сборки пишем свои — шаблонные содержат чужой modid и лишнее.

**Шаг 3. Создать `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.19.5

mod_version=0.1.0-SNAPSHOT
maven_group=com.villagepax
archives_base_name=villagepax

fabric_version=0.92.12+1.20.1
```

Заметь: `org.gradle.jvmargs` здесь задан явно, потому что в системе стоит глобальная переменная `_JAVA_OPTIONS=-Xmx16G`, и без этого демон Gradle резервирует 16 ГБ.

**Шаг 4. Создать `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Позволяет Gradle самому скачать JDK 17 — вручную ничего ставить не нужно
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'villagepax'
```

**Шаг 5. Создать `build.gradle`**

```groovy
plugins {
    id 'fabric-loom' version '1.17.20'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base { archivesName = project.archives_base_name }

loom {
    splitEnvironmentSourceSets()

    mods {
        "villagepax" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }

    runs {
        gametest {
            server()
            name = "Game Test"
            vmArg "-Dfabric-api.gametest"
            vmArg "-Dfabric-api.gametest.report-file=${project.buildDir}/junit.xml"
            runDir "build/gametest"
        }
    }
}

sourceSets {
    gametest {
        compileClasspath += sourceSets.main.compileClasspath + sourceSets.main.output
        runtimeClasspath += sourceSets.main.runtimeClasspath + sourceSets.main.output
    }
}

loom.mods."villagepax".sourceSet sourceSets.gametest

repositories {
    maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 17
    it.options.encoding = "UTF-8"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
}

test {
    useJUnitPlatform()
    testLogging { events "passed", "skipped", "failed" }
}
```

**Шаг 6. Проверить, что Gradle стартует и сам добывает JDK 17**

```bash
./gradlew --version
```

Ожидается: версия Gradle 8.x, без ошибок. Первый запуск долгий — скачивается Gradle и JDK 17.

**Шаг 7. Коммит**

```bash
git add .gitignore gradle.properties settings.gradle build.gradle gradlew gradlew.bat gradle/
git commit -m "build: настроить проект Fabric для Minecraft 1.20.1"
```

---

### Задача 0.3 — Точка входа мода и первый запуск игры

**Файлы:**
- Создать: `src/main/resources/fabric.mod.json`
- Создать: `src/main/java/com/villagepax/VillagePax.java`
- Создать: `src/client/java/com/villagepax/client/VillagePaxClient.java`
- Создать: `src/main/resources/assets/villagepax/icon.png` (заглушка 128×128)

**Шаг 1. Написать `fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "villagepax",
  "version": "${version}",
  "name": "Village Pax",
  "description": "Колонии, народы, дипломатия и вера в одном движке поселений.",
  "authors": ["TryMe"],
  "license": "ARR",
  "icon": "assets/villagepax/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.villagepax.VillagePax"],
    "client": ["com.villagepax.client.VillagePaxClient"],
    "fabric-gametest": ["com.villagepax.gametest.VillagePaxGameTests"]
  },
  "depends": {
    "fabricloader": ">=0.19.0",
    "minecraft": "~1.20.1",
    "java": ">=17",
    "fabric-api": "*"
  }
}
```

**Шаг 2. Написать главный класс**

```java
package com.villagepax;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagePax implements ModInitializer {
    public static final String MOD_ID = "villagepax";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Village Pax: инициализация");
    }
}
```

**Шаг 3. Написать клиентскую точку входа**

```java
package com.villagepax.client;

import net.fabricmc.api.ClientModInitializer;

public class VillagePaxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
    }
}
```

**Шаг 4. Собрать**

```bash
./gradlew build
```

Ожидается: `BUILD SUCCESSFUL`, в `build/libs/` появился `villagepax-0.1.0-SNAPSHOT.jar`.

**Шаг 5. Запустить игру**

```bash
./gradlew runClient
```

Ожидается: открывается Minecraft 1.20.1, в логе есть строка `Village Pax: инициализация`. Это первая настоящая проверка, что вся связка работает.

**Шаг 6. Коммит**

```bash
git add src/ && git commit -m "feat: точка входа мода и первый запуск"
```

---

### Задача 0.4 — Харнесс модульных тестов

**Файлы:**
- Создать: `src/main/java/com/villagepax/core/Ids.java`
- Создать: `src/test/java/com/villagepax/core/IdsTest.java`

Первый тест намеренно тривиален: он проверяет не логику, а то, что тестовый контур вообще работает. Заводить его надо до того, как появится что-то сложное.

**Шаг 1. Написать падающий тест**

```java
package com.villagepax.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdsTest {
    @Test
    void buildsNamespacedPath() {
        assertEquals("villagepax:norman/lumberjack", Ids.path("norman/lumberjack"));
    }
}
```

**Шаг 2. Убедиться, что тест падает**

```bash
./gradlew test
```

Ожидается: FAIL — класс `Ids` не найден.

**Шаг 3. Минимальная реализация**

```java
package com.villagepax.core;

public final class Ids {
    private Ids() {}

    public static String path(String value) {
        return "villagepax:" + value;
    }
}
```

Класс не трогает классы Minecraft намеренно — так тесты остаются быстрыми и не требуют запуска игры. Всё, что зависит от Minecraft, проверяется в GameTest.

**Шаг 4. Убедиться, что тест проходит**

```bash
./gradlew test
```

Ожидается: PASS.

**Шаг 5. Коммит**

```bash
git add src/main/java/com/villagepax/core/Ids.java src/test/ && git commit -m "test: харнесс модульных тестов"
```

---

### Задача 0.5 — Харнесс игровых тестов

**Файлы:**
- Создать: `src/gametest/java/com/villagepax/gametest/VillagePaxGameTests.java`

**Шаг 1. Написать простейший игровой тест**

```java
package com.villagepax.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class VillagePaxGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modIsLoaded(TestContext context) {
        context.complete();
    }
}
```

**Шаг 2. Запустить**

```bash
./gradlew runGametest
```

Ожидается: `BUILD SUCCESSFUL`, в отчёте один пройденный тест.

**Шаг 3. Коммит**

```bash
git add src/gametest/ && git commit -m "test: харнесс игровых тестов"
```

---

### Задача 0.6 — Непрерывная сборка

**Файлы:**
- Создать: `.github/workflows/build.yml`

Файл кладём сразу, даже пока нет удалённого репозитория — когда он появится, сборка заработает без доработок.

```yaml
name: build

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - run: chmod +x gradlew
      - run: ./gradlew build test
```

Коммит: `ci: сборка и тесты на GitHub Actions`.

---

# ФАЗА 1 — Вертикальный срез 0.1

Каждая задача ниже задана целью, контрактом и приёмочным тестом. Детальный под-план с кодом пишется в момент, когда за задачу берёмся.

Порядок не произвольный: он выстроен так, чтобы **каждая задача заканчивалась чем-то, что видно в игре**, а не «ещё одним слоем абстракции». Это защита от того, чтобы полгода писать движок и ни разу не поиграть.

---

### Задача 1.1 — Реестры и загрузка датапаков

**Статус: выполнена 2026-09-05.**

Сделано: запись `Culture` с кодеком и вложенными `CultureKind`, `SpawnSettings`, `NamePools`; загрузчик `CultureManager` поверх ванильного `JsonDataLoader`; блоки (ратуша и пять маркеров схемы), предметы, творческая вкладка, чертёж ратуши с рецептом-подстраховкой; девять нарисованных текстур 16×16, модели, блокстейты, лут-таблицы, локализация RU/EN; датапак норманнов.

**Приёмка пройдена:** в логе запущенного сервера `Загружено культур: 1 (villagepax:norman)`. Четыре модульных теста на кодек — разбор полного файла, значения по умолчанию, отказ на неизвестном роде культуры, отказ на пропущенном обязательном поле. Два игровых теста — культура доступна из мира, блоки зарегистрированы под своими идентификаторами и ставятся.

**Уроки, стоящие записи:**

- Файлы культур лежат в `data/<пространство имён>/villagepax/cultures/`, а не в `data/<ns>/cultures/`. Вложенная папка мода нужна, чтобы `JsonDataLoader` не подхватывал файлы других модов и не пытался разобрать их нашим кодеком. Путь в дизайн-документе исправлен.
- `StringIdentifiable` содержит вложенный тип с именем `Codec`, и внутри перечисления, реализующего этот интерфейс, короткое имя `Codec` разрешается в него, а не в мозанговский. Тип пишется полностью.
- Кодек перечисления собран вручную, а не через `StringIdentifiable.createCodec`: тот deprecated и при опечатке в датапаке даёт невнятную ошибку. Свой вариант печатает и введённое значение, и список допустимых.
- Метод отказа игрового теста в 1.20.1 — `TestContext.throwGameTestException(String)`, не `createError`. Имена маппингов проверяются `javap` по jar в кэше Loom, а не по памяти.
- Текстуры рисуются генератором из явных пиксельных карт (`scratchpad/make-textures.ps1`) — своё, без чужих лицензий. Хеш-таблицы PowerShell регистронезависимы, поэтому палитра собрана на `Dictionary` с `StringComparer.Ordinal`, иначе `S` и `s` схлопываются.
- Heredoc в этой оболочке ломается на нечётном числе одинарных кавычек в теле — для такого содержимого нужен файловый инструмент.

---

### Задача 1.2 — Модель поселения и сохранение

**Статус: выполнена 2026-09-05.**

Сделано: `Settlement` с зданиями, жителями, уровнем, показателями и границами; `Building` с якорем, поворотом и состоянием стройки; `Citizen` со всем, что должно пережить выгрузку чанка; `Owner`, `SettlementLevel`, `BuildProgress`, `Gender`, `SettlementStats`; `SettlementManager extends PersistentState` на `ServerWorld`.

**Приёмка пройдена:** семь модульных тестов на круговой прогон через NBT — поселение, здания с поворотом схемы и назначенными работниками, жители с профессией и жильём, автономная деревня, границы по уровню, обнаружение пересечения границ, потолок населения. Игровой тест `settlementSurvivesWorldReload` гоняет поселение через настоящий `PersistentStateManager` запущенного мира и читает обратно. Всего 12 модульных и 4 игровых теста.

**Решения, принятые по ходу:**

- **Уровень поселения задаёт границы и потолок населения**: хутор 6 жителей и радиус 2 чанка, деревня 14 и 3, город 28 и 4, столица 64 и 6. Границы — радиус от ратуши, а не набор чанков: для среза 0.1 этого достаточно, а хранить и синхронизировать нечего.
- **Поселения кодируются по одному, а не списком целиком.** Список был бы короче кодом, но одна повреждённая запись уносила бы все остальные. Сейчас битая запись логируется и пропускается, а колония на двести часов выживает.
- **Единственный безопасный способ изменить поселение — `SettlementManager.update`**, он сам помечает состояние грязным. Забытый `markDirty` — самый коварный баг в этом месте, потому что всё работает до перезахода в мир; игровой тест проверяет это отдельно.
- **Свой интерфейс `Named` вместо ванильного `StringIdentifiable`.** У последнего есть вложенный тип с именем `Codec`, из-за чего внутри перечисления короткое имя разрешается не в мозанговский кодек. Свой интерфейс плюс общий `EnumCodecs.of` убирают ловушку разом для всех перечислений и дают внятную ошибку при опечатке в датапаке.
- Ванильные `BlockPos.CODEC` и `BlockRotation.CODEC` уже существуют — проверено через `javap`, свои писать не пришлось.

---

### Задача 1.3 — Ратуша и основание колонии

**Статус: выполнена 2026-09-05.**

Сделано: `TownHallBlock` с блок-энтити, хранящей идентификатор поселения; `TownHallBlueprintItem` — чертёж с записанным народом; `Founding` с правилами основания и `ColonyFounder` как операция над миром; сообщения игроку на русском и английском; подсказки на предметах в клиентском наборе исходников.

**Приёмка пройдена:** семь модульных тестов правил основания и два игровых — полный путь в живом мире (чертёж ставит ратушу, блок связывается с поселением, поселение попадает в менеджер, ратуша записывается готовым зданием) и отказ при отсутствии опоры. Всего 19 модульных и 6 игровых тестов. Клиент запущен и проверен отдельно: инициализация проходит, ресурсы мода подхватываются, исключений нет.

**Решения, принятые по ходу:**

- **Одна колония на игрока.** Ограничение живёт в `Founding`, а не размазано по коду: когда появится конфиг, снимать его надо будет в одном месте. Проверяется раньше проверки границ, поэтому игрок получает понятное «у тебя уже есть колония», а не «слишком близко».
- **Снос ратуши не распускает колонию.** То же правило, что и для войны: здания повреждаются и восстанавливаются, но двести часов работы не должны исчезать от одного неверного клика. Поселение остаётся, ратушу нужно отстроить.
- **Правила основания отделены от предмета, а операция над миром — от правил.** `Founding` не знает о мире и потому покрыт обычными тестами; `ColonyFounder` трогает мир и вызывается из игровых тестов напрямую, без игрока; предмет остался тонкой оболочкой из сообщений и траты стака. В 1.20.1 у `TestContext` нет фабрики поддельного игрока, так что без этого разделения путь основания было бы нечем проверить.
- **Подсказки на предметах — в клиентском наборе исходников** через `ItemTooltipCallback`, а не переопределением `Item.appendTooltip`: последний тянет клиентский тип в общий код, а это прямой путь к падению выделенного сервера.
- Тип здания ратуши ищется в списке зданий культуры по соглашению об именовании, чтобы датапак не был обязан объявлять его отдельным полем.

---

### Задача 1.4 — Житель: данные и тело

`CitizenEntity`, спавн при загрузке чанка, деспавн при выгрузке, перенос состояния в данные и обратно.

**Приёмка (GameTest):** заспавнить жителя, выгрузить чанк, загрузить обратно — житель на месте, `JobState` не потерян. Это проверка ключевого архитектурного решения всего мода.

---

### Задача 1.5 — Схемы и план стройки

Загрузка `.nbt`, блоки-маркеры, вычисление отсортированного списка блоков (расчистка → фундамент → стены → крыша → декор), кэширование.

**Приёмка:** для тестовой схемы список блоков детерминирован и одинаков между запусками; маркеры распознаны и превращены в точки интереса.

---

### Задача 1.6 — Билдер строит

`BuildJob`: билдер идёт по списку, требует материалы, ставит блоки по одному.

**Приёмка (GameTest):** дать билдеру схему и полный склад — через N тиков здание построено полностью и совпадает со схемой поблочно. Отдельный тест: материалов не хватает — билдер ждёт, а не ломается.

---

### Задача 1.7 — Склад и курьер

Виртуальный склад поверх реальных сундуков, заявки на материалы, `HaulJob`.

**Приёмка:** курьер носит материалы со склада на стройку, билдер строит, игрок за этим наблюдает.

---

### Задача 1.8 — Дом, распорядок, еда, счастье

Привязка кровати, расписание суток, потребление еды, расчёт счастья.

**Приёмка:** житель уходит спать ночью, ест днём, при отсутствии еды счастье падает, при длительном голоде житель уходит из колонии.

---

### Задача 1.9 — Профессии: лесоруб и фермер

`GatherJob` и `FarmJob` поверх общей машины состояний.

**Приёмка:** лесоруб валит деревья и сдаёт брёвна на склад, фермер сеет и жнёт; оба переживают выгрузку чанка.

---

### Задача 1.10 — Интерфейс ратуши

Экран на owo-lib: обзор, здания, жители, склад. Синхронизация по подписке.

**Приёмка:** экран открывается, показывает актуальные данные, кнопка постройки ставит здание в очередь; на выделенном сервере всё работает так же, как в одиночной игре.

---

### Задача 1.11 — Голограмма схемы

Клиентский рендер выбранного здания с поворотом до подтверждения.

**Приёмка:** игрок видит призрак здания, крутит его, подтверждает — стройка начинается ровно там, где показывала голограмма.

---

### Задача 1.12 — Автономная норманнская деревня

Разметка мест под деревню при генерации мира, создание поселения при приближении игрока, рост по плану.

**Приёмка:** в новом мире находится норманнская деревня с жителями, которые работают; за игровую неделю в ней появляется новое здание.

---

### Задача 1.13 — Квесты и репутация

Определения квестов, старейшина-выдающий, репутация, стартовая цепочка, чертёж ратуши как награда.

**Приёмка:** полный проход входа в мод — найти деревню, выполнить цепочку, получить чертёж, основать колонию. Это приёмочный тест всей версии 0.1.

---

### Задача 1.14 — Конфигурация

Смертность, ставка налога, размеры поселений, расстояние между деревнями.

**Приёмка:** выключение смертности в конфиге действительно останавливает старение.

---

### Задача 1.15 — Нагрузочная проверка

Мир с пятью поселениями, замер времени тика, порог в непрерывной сборке.

**Приёмка:** одно активное поселение среднего размера рядом с игроком укладывается в 5 мс на тик. Если нет — оптимизируем до того, как строить дальше.

---

## Чего в версии 0.1 намеренно нет

Дипломатия, война, боги, торговля, вторая культура, характеры жителей, гражданство в чужой деревне, GeckoLib и свои модели. Всё это идёт в фазы 2–5. Срез 0.1 доказывает, что движок работает; расширять его контентом можно только после этого.
