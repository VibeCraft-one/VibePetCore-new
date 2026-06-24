# VibePetCore Release Bug Hunt Plan

Статус: кодовый release bug-hunt закрыт на `2.6.29`, дальше только ручной stack gate и точечные фиксы по факту.

## Снимок на 2026-06-24

- честная оценка готовности до релиза: `~92%`
- локальная ветка и `origin/codex/refactor-gui-pages` синхронны на свежих релизных фиксах
- кодовых незакрытых `P0/P1` сейчас не доказано, но релиз всё ещё `не готов` без ручного server-stack прохода
- закрыты кодом и тестами: `QUEST-SAVE`, `EVOLUTION-SAVE`, `FORGE-SAVE`, `CORE-REPAIR-SAVE`
- дополнительно закрыты: `/pet` active-core targeting, summon/help truthfulness, empty-core overwrite при recall
- следующий обязательный проход: `VPC-REL-STACK-MANUAL-01`
- после него: либо `release candidate`, либо один узкий fix по найденному факту
- подготовлены:
  - `4` точных `P0` handoff-файла
  - manual release gate в `TEST_CHECKLIST.md`
- базовая проверка ветки на текущем релизном состоянии:
  - `./gradlew test` — зелёный
  - `./gradlew processResources compileJava test jar check` — зелёный
  - `checkJarSize`: в лимите

Что это значит:
- кодовый релизный круг больше не надо крутить без новых фактов;
- сейчас нельзя честно говорить "готово к релизу" только потому, что ещё не пройден живой server-stack gate;
- structural cleanup жирных классов не является текущим release blocker без нового доказанного бага.

Цель:
- довести плагин до безопасного релиз-кандидата;
- закрывать только реальные `P0/P1`;
- не делать большой рефакторинг ради красоты;
- дробить работу на маленькие проверяемые проходы;
- после потери актуальности файл можно удалить.

## Быстрый triage

Смотри также:
- `RELEASE_TASK_PROMPTS.md` — готовые prompts для `Builder` и `Reviewer`
- `RELEASE_EXECUTION_QUEUE.md` — порядок `Builder -> Reviewer -> smoke`
- `VPC-REL-BUGHUNT-QUEST-SAVE-01.md` — точный handoff по первому `P0`
- `VPC-REL-BUGHUNT-EVOLUTION-SAVE-01.md` — точный handoff по второму `P0`
- `VPC-REL-BUGHUNT-FORGE-SAVE-01.md` — точный handoff по третьему `P0`
- `VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01.md` — точный handoff по четвёртому destructive save-flow

### Уже закрыто первым кругом

- `VPC-REL-BUGHUNT-QUEST-SAVE-01`
- `VPC-REL-BUGHUNT-EVOLUTION-SAVE-01`
- `VPC-REL-BUGHUNT-FORGE-SAVE-01`
- `VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01`
- `VPC-REL-SMOKE-DESTRUCTIVE-GUI-01`

### Уже подтверждено кодом, тестами или живым smoke

- `QuestManager` risky destructive save-flow
- `PetEvolutionFlowSupport` / `PetEngineManager` risky evolution save-flow
- `PetGuiService` forge upgrade risky save-flow
- `PetGuiService` core repair risky save-flow
- `Source offhand conflict`
- `quest accept/turn-in`
- `forge upgrade spend path`
- `Source box spend path`
- `actual evolution button`
- `core repair`
- personal `/pet` core targeting
- summon hand/timer truthfulness
- recall в пустое ядро больше не может затереть второго питомца того же типа

### Ещё не доказано и действительно важно

- реальный proxy/lobby/plugin stack без first-player UX конфликтов
- нет заметных MSPT spikes на `2-3` игроках в summon/Source/GUI/quest/forge/evolution
- нет конфликтов по inventory/gui/offhand flow с соседними плагинами

### Не путать

- `P0/P1` багфиксы идут раньше structural cleanup
- ручной stack gate идёт раньше нового structural cleanup
- migration/archive/storage backend не трогать без доказанного бага

## Что уже подтверждено

- ветка: `codex/refactor-gui-pages`
- удалённая ветка синхронна на текущих релизных фиксах
- smoke есть в:
  - `SMOKE-2.6.23.txt`
  - `SMOKE-2.6.26.txt`
  - `TEST_CHECKLIST.md`
  - `CODEX_CONTEXT.md`

## Что не делать

- не переносить проект на Maven;
- не делать архитектурный ремонт всего плагина перед релизом;
- не удалять `PetMaster` целиком без доказанной пользы;
- не смешивать багфикс destructive-flow, GUI-рефакторинг и TPS-аудит в один проход.

## Приоритетный порядок проходов

### 1. VPC-REL-STACK-MANUAL-01

Тип: `release gate`

Что доказать:
- этот же `jar` на реальном server stack не даёт неприятный first-player UX;
- нет заметных MSPT spikes при `2-3` одновременных игроках;
- нет конфликтов с соседними inventory/gui/offhand плагинами.

Инструкция:
- шаги и PASS/FAIL уже зафиксированы в `TEST_CHECKLIST.md`;
- если проходит, можно поднимать статус до `release candidate`;
- если находит новый `P0/P1`, открыть один узкий fix-pass и не возобновлять общий audit.

Критерии закрытия:
 - manual steps дали pass;
 - если найден баг, он оформлен как один точный task ID;
 - после фикса `./gradlew processResources compileJava test jar check` снова зелёный.

### 2. Structural cleanup только после нового доказательства

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

### 6. VPC-REL-REFAC-GUI-SERVICE-01

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

### 7. VPC-REL-REFAC-COMMAND-HANDLER-01

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

### 8. VPC-REL-REFAC-PET-ENGINE-01

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

### 9. VPC-REL-AUDIT-ADMIN-MUTATION-SAVE-01

Тип: `operational risk`

Подозрение:
- часть admin-команд меняет points/core-state без явного save в той же ветке выполнения;
- это не первый player-facing приоритет, но опасно для тестов, ручного восстановления и отладки релизных багов.

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java`
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Что проверить:
- `admin addpoints/takepoints`
- `setevolution/setlevel/setrarity/setsatiety/repaircore/fixegg`

Критерии закрытия:
- admin mutation-команды либо явно сохраняют изменения, либо это сознательно documented contract;
- ручное восстановление не создаёт ложный рассинхрон;
- если найдётся баг, он чинится отдельным узким проходом.

### 10. VPC-REL-REFAC-SOURCE-INFRA-01

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

### 11. VPC-REL-SMOKE-DESTRUCTIVE-GUI-01

Тип: `release evidence`

Что уже закрыто живым доказательством:
- destructive GUI click у `Source box`;
- `quest accept/turn-in`;
- `forge upgrade`;
- actual evolution attempt button;
- `core repair`;
- прямой `ПКМ` по установленному Источнику, когда core в offhand.

Критерии закрытия:
- controlled smoke записан в `SMOKE-2.6.26.txt`;
- нет VibePetCore ошибок в логе;
- нет ложного расхода ресурсов/предметов.

Остаток после `SMOKE-2.6.26.txt`:
- нужен только реальный stack/manual gate `VPC-REL-STACK-MANUAL-01`;
- player-facing destructive механики считаются доказанными.

### 12. VPC-REL-TPS-RISK-AUDIT-01

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
- на текущем `2026-06-24` code audit новых очевидных release-blocker `P0/P1` в этих местах не дал; остаток риска теперь только на живом server stack.

### 13. VPC-REL-AUDIT-PERSISTENCE-01

Тип: `foundational risk`

Зоны:
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`
- `src/main/java/dev/li2fox/vibepetcore/player/SqlitePlayerStorage.java`
- `src/main/java/dev/li2fox/vibepetcore/player/MysqlPlayerStorage.java`

Почему важно:
- все destructive `P0` в итоге упираются в поведение `save(playerId)` и dirty tracking;
- storage работает синхронно и является источником истины для rollback-контрактов;
- это не выглядит как отдельный текущий баг, но любой fix в `quest/evolution/forge` нужно проверять через этот слой.

Что уже видно по коду:
- `PlayerDataManager.save(playerId)` сохраняет только dirty players и не удаляет dirty flag при ошибке;
- `saveAll()` просто итерирует dirty set, то есть это скорее infra-фундамент, чем место для сложной логики;
- `SqlitePlayerStorage` и `MysqlPlayerStorage` выглядят прямолинейно и синхронно, без отдельной очереди или async-buffer;
- fallback/migration/archiving логика есть, но это отдельная зона осторожности при SQL-migration.

Критерии закрытия:
- при фиксе destructive-flow тесты доказывают контракт именно через текущий persistence layer;
- если найдётся backend-specific bug, он выносится в отдельный узкий проход;
- без доказательства не трогать migration/archive логику перед релизом.

## Уже замеченные страшные места

### Реальный релизный риск

- живой server-stack конфликт
- MSPT spikes под `2-3` ручными тестерами
- соседний plugin conflict по inventory/gui/offhand flow

### Structural risk

- `PetEggController` жирный listener и смешанные обязанности
- `PetGuiService` смешивает GUI listener и business-flow
- `PetEngineManager` слишком большой orchestration-класс
- `VibePetCommandHandler` жирный command-router
- `PetMasterManager` и `LootBoxManager` смешивают interaction, visual tick и infra-flow
- persistence layer критичен для всех save/rollback фиксов, даже если сам по себе пока не выглядит сломанным

## Как работать дальше

Для каждого следующего прохода:
1. сначала доказать новый баг на ручном stack gate или логами;
2. сделать минимальный фикс без большого рефакторинга;
3. добавить regression-test, если меняется поведение;
4. прогнать `test/build`;
5. обновить `CODEX_CONTEXT.md`, `TEST_CHECKLIST.md`, `SMOKE-*.txt` только если это реально полезно;
6. commit/push.

Если риск не подтвердился:
- записать это прямо в commit/отчёте;
- убрать проход из backlog или пометить как `not proven`.
