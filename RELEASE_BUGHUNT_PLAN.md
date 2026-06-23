# VibePetCore Release Bug Hunt Plan

Статус: рабочий backlog для координаторов перед релизом `2.6.23`.

Цель:
- довести плагин до безопасного релиз-кандидата;
- закрывать только реальные `P0/P1`;
- не делать большой рефакторинг ради красоты;
- дробить работу на маленькие проверяемые проходы;
- после потери актуальности файл можно удалить.

## Что уже подтверждено

- ветка: `codex/refactor-gui-pages`
- удалённая ветка была синхронна на момент аудита: `6b15524 Document GUI armor smoke`
- smoke есть в:
  - `SMOKE-2.6.23.txt`
  - `TEST_CHECKLIST.md`
  - `CODEX_CONTEXT.md`

## Что не делать

- не переносить проект на Maven;
- не делать архитектурный ремонт всего плагина перед релизом;
- не удалять `PetMaster` целиком без доказанной пользы;
- не смешивать багфикс destructive-flow, GUI-рефакторинг и TPS-аудит в один проход.

## Приоритетный порядок проходов

### 1. VPC-REL-BUGHUNT-QUEST-SAVE-01

Тип: `P0`

Подозрение:
- `quest accept/turn-in` меняет прогресс, предметы и очки без явного immediate save/rollback.

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/quest/QuestManager.java`
- `src/main/java/dev/li2fox/vibepetcore/economy/EconomyManager.java`
- `src/main/java/dev/li2fox/vibepetcore/gui/SourceQuestPage.java`
- `src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java`
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Критерии закрытия:
- при `save-fail` после `turn-in` предметы не теряются;
- `Pet Points` не дюпаются и не пропадают;
- `progress/completed` не расходятся;
- есть regression-test на rollback-контракт;
- `test/build` зелёные.

### 2. VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Тип: `P0`

Подозрение:
- ресурсы эволюции списываются до доказанного save/rollback;
- возможен рассинхрон `materials/stage/runtime/core`.

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionFlowSupport.java`
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionRuntimeSupport.java`
- `src/main/java/dev/li2fox/vibepetcore/api/impl/CoreProgressionAPI.java`
- `src/test/java/dev/li2fox/vibepetcore/api/impl/CoreProgressionAPITest.java`

Критерии закрытия:
- ресурсы не теряются при `save-fail`;
- стадия не меняется без успешного сохранения;
- `runtime/core/playerdata` не расходятся;
- есть regression-test;
- `test/build` зелёные.

### 3. VPC-REL-BUGHUNT-FORGE-SAVE-01

Тип: `P0`

Подозрение:
- donor eggs уничтожаются без явного save/rollback;
- rarity/core-state могут измениться при частично неуспешном flow.

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`
- `src/main/java/dev/li2fox/vibepetcore/gui/SourceForgePage.java`
- `src/main/java/dev/li2fox/vibepetcore/egg/PetEggService.java`
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Критерии закрытия:
- donor eggs не теряются при `save-fail`;
- rarity не меняется без успешного сохранения;
- `held core/runtime/playerdata` не расходятся;
- GUI не врёт о результате;
- есть regression-test;
- `test/build` зелёные.

### 4. VPC-REL-REFAC-EGG-LISTENER-01

Тип: `structural risk`

Зона:
- `src/main/java/dev/li2fox/vibepetcore/egg/PetEggController.java`

Почему важно:
- слишком много обязанностей в одном listener;
- это не доказанный TPS-баг, но опасная зона для регрессий и тяжёлой поддержки.

Что делать:
- вынести `inventory sync/sanitize`;
- вынести `death/respawn core return`;
- вынести `interact / legacy-core guard`;
- оставить listener тонким маршрутизатором;
- не менять поведение без теста или доказанной необходимости.

Критерии закрытия:
- поведение не меняется;
- offhand/drop/death/relog smoke не ломается;
- listener заметно короче и понятнее.

### 5. VPC-REL-REFAC-GUI-SERVICE-01

Тип: `structural risk`

Зона:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Почему важно:
- в одном классе смешаны listener, menu routing, held-core lookup, quest selection, Source actions и forge flow;
- это не главный TPS-риск, но плохая точка для последующих багфиксов и GUI-регрессий.

Что делать:
- продолжать выносить страницы и action-handler'ы;
- вынести destructive `forge` и `box` action flow из GUI-слоя;
- сократить строковую маршрутизацию `menuId` там, где можно безопасно заменить page/service-обёрткой.

Критерии закрытия:
- `PetGuiService` остаётся listener/facade, но не хранит business-логику destructive операций;
- GUI smoke не регрессит;
- новые страницы и действия читаются отдельно.

### 6. VPC-REL-REFAC-COMMAND-HANDLER-01

Тип: `structural risk`

Зона:
- `src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java`

Почему важно:
- жирный command-router держит admin/source/quest/pet/help/recovery сценарии в одном месте;
- это не доказанный perf-баг, но опасная точка для ошибок в командах и трудной навигации по коду.

Что делать:
- выносить команды малыми support-классами по доменам;
- не ломать текущий command contract;
- приоритет на `quest/source/admin diagnostics`, потому что они чаще нужны в релизном bug hunt.

Критерии закрытия:
- логические группы команд читаются по отдельным support-классам;
- help/usage/permissions не ломаются;
- smoke ключевых команд остаётся зелёным.

### 7. VPC-REL-REFAC-PET-ENGINE-01

Тип: `structural risk`

Зона:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`

Почему важно:
- класс одновременно держит activation, runtime update, feeding, combat, damage, evolution, refresh и recovery;
- рядом уже находятся реальные destructive `P0`-цепочки, поэтому дальнейшие фиксы в этом файле будут рискованны без локального распила.

Что делать:
- не переписывать весь engine;
- выносить только самостоятельные зоны: `activation/save-flow`, `evolution flow`, `incoming damage / combat reactions`, `owner control actions`;
- оставлять orchestration в manager, но убирать детали из монолита.

Критерии закрытия:
- risky subflows читаются отдельно;
- regression-тесты на activation/evolution не ломаются;
- runtime/smoke не регрессит.

### 8. VPC-REL-REFAC-SOURCE-INFRA-01

Тип: `structural risk`

Зоны:
- `src/main/java/dev/li2fox/vibepetcore/master/PetMasterManager.java`
- `src/main/java/dev/li2fox/vibepetcore/box/LootBoxManager.java`

Почему важно:
- Источник питомцев и старые алтари держат interact flow, teleport flow, visual tick, save/load и open guards;
- это не выглядит как немедленный `P0`, но здесь легко спрятать GUI/open regressions и лишнюю нагрузку на живом сервере.

Что делать:
- не менять механику без доказанного повода;
- при необходимости вынести отдельно `teleport flow`, `visual tick/open guards`, `altar/source interaction`;
- проверить, что visual tick и nearby checks остаются простыми и не превращаются в тяжёлый hot-path.

Критерии закрытия:
- `/vpc source` и altar/source interactions не ломаются;
- teleport flow читается отдельно;
- visual/source tick не разрастается без guards;
- smoke у Источника/алтарей остаётся зелёным.

### 9. VPC-REL-SMOKE-DESTRUCTIVE-GUI-01

Тип: `release evidence`

Что ещё не закрыто живым доказательством:
- destructive GUI click у `Source box`;
- `quest accept/turn-in`;
- `forge upgrade`;
- actual evolution attempt button;
- прямой `ПКМ` по установленному Источнику, когда core в offhand.

Критерии закрытия:
- controlled smoke записан в `SMOKE-2.6.23.txt` или новый smoke-файл;
- нет VibePetCore ошибок в логе;
- нет ложного расхода ресурсов/предметов.

### 10. VPC-REL-TPS-RISK-AUDIT-01

Тип: `release evidence`

Что проверить:
- нагрузочные риски на `50-100` online;
- частые события около `PetEngineListener`, `PetEggController`, `PetGuiService`;
- нет ли явного `O(n)`/`O(n^2)` в hot-path, который ещё не зажат guards/throttle.

Критерии закрытия:
- есть код-аудит hot-path;
- если найден perf-risk, он выделен в отдельный узкий проход;
- если не найден, это явно записано как "не подтверждено".

Hot-path watchlist:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetAbilityService.java`
- `src/main/java/dev/li2fox/vibepetcore/pet/PetInterestLocator.java`
- `src/main/java/dev/li2fox/vibepetcore/task/TaskManager.java`
- `src/main/java/dev/li2fox/vibepetcore/master/PetMasterManager.java`
- `src/main/java/dev/li2fox/vibepetcore/box/LootBoxManager.java`

Что уже видно по коду:
- `TaskManager` не выглядит страшной зоной сам по себе: тики завернуты в guarded runner и slow-log уже есть;
- `PetAbilityService` выглядит как реальный hot-path candidate, но уже имеет nearby-cache и cooldown cleanup;
- `PetInterestLocator` использует `owner.getNearbyEntities(...)` и локальные циклы, поэтому его нельзя расширять без perf-проверки;
- `PetMasterManager` и `LootBoxManager` имеют свои `runTaskTimer` visual tick, значит их визуальные эффекты и nearby-checks нельзя раздувать без доказательства.

## Уже замеченные страшные места

### Реальный релизный риск

- `QuestManager` destructive save-flow
- `PetEvolutionFlowSupport` / `PetEngineManager` destructive evolution flow
- `PetGuiService` forge upgrade destructive flow

### Structural risk

- `PetEggController` жирный listener и смешанные обязанности
- `PetGuiService` смешивает GUI listener и business-flow
- `PetEngineManager` слишком большой orchestration-класс
- `VibePetCommandHandler` жирный command-router
- `PetMasterManager` и `LootBoxManager` смешивают interaction, visual tick и infra-flow

## Как работать дальше

Для каждого прохода:
1. подтвердить баг или риск по текущему коду;
2. сделать минимальный фикс или узкий рефакторинг;
3. добавить regression-test, если меняется поведение;
4. прогнать `test/build`;
5. обновить `CODEX_CONTEXT.md`, `TEST_CHECKLIST.md`, `SMOKE-*.txt` только если это реально полезно;
6. commit/push.

Если риск не подтвердился:
- записать это прямо в commit/отчёте;
- убрать проход из backlog или пометить как `not proven`.
