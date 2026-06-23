# VibePetCore Release Task Prompts

Короткие готовые prompts для будущих координаторов.

Правило:
- один prompt = один маленький проход;
- не смешивать destructive bugfix, anti-chaos refactor и smoke в одной задаче;
- после потери актуальности файл можно удалить.

Смотри также:
- `RELEASE_EXECUTION_QUEUE.md` — в каком порядке запускать `Builder` и `Reviewer`
- `RELEASE_CHAT_LAUNCH.md` — готовые сообщения для новых чатов

## Builder: Quest Save

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-QUEST-SAVE-01

Контекст:
- ветка: codex/refactor-gui-pages
- release triage см. RELEASE_BUGHUNT_PLAN.md
- координатор подтвердил risky destructive save-flow у quest turn-in
- не делать широкий рефакторинг

Проверь файлы:
- src/main/java/dev/li2fox/vibepetcore/quest/QuestManager.java
- src/main/java/dev/li2fox/vibepetcore/economy/EconomyManager.java
- src/main/java/dev/li2fox/vibepetcore/gui/SourceQuestPage.java
- src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java
- src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java

Образцы rollback-контракта:
- src/test/java/dev/li2fox/vibepetcore/box/BoxManagerBasicOpenRollbackTest.java
- src/test/java/dev/li2fox/vibepetcore/pet/PetEngineManagerActivationRollbackTest.java

Что нужно:
1. Подтверди баг по текущему коду.
2. Если баг реален, сделай минимальный fix с immediate save/rollback.
3. Добавь regression-test на save-fail после destructive quest turn-in.
4. Прогони целевые тесты и сборку.
5. Commit/push только после зелёной проверки.

Критерии:
- предметы не теряются при save-fail
- points не дюпаются и не пропадают
- progress/completed не расходятся
- есть regression-test
- compile/test зелёные

Формат ответа:
- findings first
- потом что изменено
- потом тесты и residual risk
```

## Builder: Evolution Save

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Контекст:
- release triage см. RELEASE_BUGHUNT_PLAN.md
- координатор подтвердил risky destructive evolution flow
- не расползаться в общий engine refactor

Проверь файлы:
- src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionFlowSupport.java
- src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java
- src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionRuntimeSupport.java
- src/main/java/dev/li2fox/vibepetcore/api/impl/CoreProgressionAPI.java
- src/test/java/dev/li2fox/vibepetcore/api/impl/CoreProgressionAPITest.java

Что нужно:
1. Подтверди баг по текущему коду.
2. Если реален, сделай минимальный transaction-style flow:
   - проверки
   - списание
   - save
   - rollback
3. Добавь regression-test.
4. Прогони тесты/сборку.
5. Commit/push только после зелёной проверки.

Критерии:
- ресурсы не теряются при save-fail
- stage не меняется без успешного save
- runtime/core/playerdata не расходятся
- есть regression-test
```

## Builder: Forge Save

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-FORGE-SAVE-01

Контекст:
- release triage см. RELEASE_BUGHUNT_PLAN.md
- координатор подтвердил risky destructive forge flow
- не расползаться в большой GUI refactor

Проверь файлы:
- src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java
- src/main/java/dev/li2fox/vibepetcore/gui/SourceForgePage.java
- src/main/java/dev/li2fox/vibepetcore/egg/PetEggService.java
- src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java

Что нужно:
1. Подтверди баг по текущему коду.
2. Если реален, вынеси upgrade в понятный save/rollback flow.
3. Добавь regression-test.
4. Прогони тесты/сборку.
5. Commit/push только после зелёной проверки.

Критерии:
- donor eggs не теряются при save-fail
- rarity не меняется без успешного save
- held core/runtime/playerdata не расходятся
- GUI не врёт о результате
```

## Builder: Core Repair Save

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01

Контекст:
- release triage см. RELEASE_BUGHUNT_PLAN.md
- координатор подтвердил P0-candidate у repair core
- не смешивать с forge/evolution fix

Проверь файлы:
- src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java
- src/main/java/dev/li2fox/vibepetcore/gui/PetOverviewPage.java
- src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java
- src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java

Что нужно:
1. Подтверди баг по текущему коду.
2. Если реален, сделай минимальный save/rollback flow для repair core.
3. Добавь regression-test.
4. Прогони тесты/сборку.
5. Commit/push только после зелёной проверки.

Критерии:
- totem не теряется при save-fail
- durability/core-state не меняются без успешного save
- active runtime/core/playerdata не расходятся
```

## Reviewer: Generic Release Pass

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore

Цель:
Проверить последний diff как release bug hunt pass.

Правила:
- findings first
- искать реальные bugs, rollback gaps, save regressions, GUI mismatch, missing tests
- не предлагать широкий рефакторинг без доказанной пользы

Проверь:
- не появилась ли новая destructive ветка без immediate save/rollback
- покрывает ли новый тест именно тот failure contract, который чинится
- не смешан ли anti-chaos refactor с поведением
- не сломан ли command/GUI flow рядом

Формат:
- severity
- file/line
- почему это важно для релиза
- residual risk
```

## Reviewer: Quest Save

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-QUEST-SAVE-01

Контекст:
- см. VPC-REL-BUGHUNT-QUEST-SAVE-01.md
- это player-facing destructive P0

Проверь:
- есть ли immediate save/rollback вокруг quest turn-in
- откатываются ли items, points, progress, completed, statistics при save-fail
- не появился ли новый dupe path через GUI или /pet quest turnin
- покрывает ли тест именно destructive turn-in failure contract

Формат:
- findings first
- severity + file/line
- residual risk
```

## Reviewer: Evolution Save

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Контекст:
- см. VPC-REL-BUGHUNT-EVOLUTION-SAVE-01.md
- это player-facing destructive P0

Проверь:
- откатываются ли materials, stage, level, subLevel, bond, xp при save-fail
- не расходятся ли runtime, playerdata и held core после rollback
- не сломан ли шанс/near-far contract
- покрывает ли тест именно consume + evolve + save-fail contract

Формат:
- findings first
- severity + file/line
- residual risk
```

## Reviewer: Forge Save

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-FORGE-SAVE-01

Контекст:
- см. VPC-REL-BUGHUNT-FORGE-SAVE-01.md
- это player-facing destructive P0

Проверь:
- откатываются ли donor eggs при save-fail
- не остаётся ли changed rarity без успешного save
- не врёт ли held core / GUI message после rollback
- покрывает ли тест именно forge consume + rarity change failure contract

Формат:
- findings first
- severity + file/line
- residual risk
```

## Reviewer: Core Repair Save

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01

Контекст:
- см. VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01.md
- это player-facing destructive save-flow

Проверь:
- откатывается ли TOTEM_OF_UNDYING при save-fail
- откатываются ли durability, health, satiety, inactiveUntil
- не расходятся ли held core, runtime и playerdata после rollback
- покрывает ли тест именно repair consume + state restore failure contract

Формат:
- findings first
- severity + file/line
- residual risk
```
