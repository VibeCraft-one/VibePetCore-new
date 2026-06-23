# VPC-REL-BUGHUNT-QUEST-SAVE-01

Статус: `confirmed by code audit`

Цель:
- закрыть risky destructive save-flow у `quest turn-in`;
- не делать широкий рефакторинг квестовой системы;
- дать Builder-чатy короткую и точную точку входа.

## Почему это P0

`turn-in` может одновременно:
- списать предметы игрока;
- пометить квест завершённым;
- начислить `Pet Points`;
- увеличить quest statistics.

Но в текущем flow нет доказанного immediate `save/rollback` контракта вокруг этой destructive операции.

Если `playerData save` падает после `turn-in`, есть риск:
- потери предметов без сохранённого quest reward/state;
- рассинхрона `inventory <-> playerdata`;
- ложного успешного GUI/command ответа;
- повторного награждения или кривого recovery после рестарта/повторной загрузки.

## Прямые улики по коду

### 1. `turnIn(Player, ...)` сначала тратит предметы и завершает квест

Файл:
- `src/main/java/dev/li2fox/vibepetcore/quest/QuestManager.java`

Строки:
- `226-235`
- `241`

Смысл:
- для `PICKUP_ITEM` квеста вызывается `consume(player, material, quest.amount())`;
- затем `progress.setProgress(...)`;
- затем `complete(...)`;
- явного `playerDataManager.save(...)` в этой ветке нет.

### 2. `complete(...)` сразу меняет player-data и начисляет points

Файл:
- `src/main/java/dev/li2fox/vibepetcore/quest/QuestManager.java`

Строки:
- `306-310`

Смысл:
- `progress.setCompleted(true)`;
- `playerData.statistics().addQuestCompleted()`;
- `economyManager.award(...)`.

### 3. `award(...)` меняет points в памяти, но сам не сохраняет

Файл:
- `src/main/java/dev/li2fox/vibepetcore/economy/EconomyManager.java`

Строки:
- `26-39`

Смысл:
- для `RewardReason.QUEST` вызывается только `playerDataManager.getOrLoad(playerId).addPoints(...)`;
- отдельный `save(...)` в `award(...)` не вызывается.

### 4. `save(...)` существует отдельно и требует явного вызова

Файл:
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Строки:
- `103-116`

Смысл:
- `PlayerDataManager.save(playerId)` реально пишет storage;
- при ошибке возвращает `false`;
- dirty flag при ошибке остаётся;
- но сам `QuestManager.turnIn(...)` этот save-контракт не вызывает.

### 5. GUI и команда показывают успех по boolean из `turnIn(...)`

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/gui/SourceQuestPage.java`
- `src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java`

Строки:
- `SourceQuestPage.java:142-145`
- `VibePetCommandHandler.java:862-864`

Смысл:
- и GUI, и `/pet quest turnin` считают `true` успешным завершением;
- отдельной проверки фактического save нет.

## Что Builder должен сделать

Минимальный безопасный scope:
- не переписывать всю quest-систему;
- закрыть только `turn-in` destructive save-flow;
- сохранить текущий command/GUI contract;
- добавить regression-test.

Ожидаемая форма фикса:
- собрать snapshot изменяемого quest/player state до destructive шага;
- для `PICKUP_ITEM` иметь rollback предметов в inventory при `save-fail`;
- начислять reward/statistics только в transaction-style flow;
- если save не удался:
  - откатить quest progress/completed;
  - откатить points/statistics;
  - откатить списанные предметы;
  - вернуть GUI/command `false` и понятное сообщение.

## Что должен доказать тест

Нужен новый regression-test.

Рекомендуемое место:
- `src/test/java/dev/li2fox/vibepetcore/quest/QuestManagerTurnInRollbackTest.java`

Опорные тесты по стилю:
- `src/test/java/dev/li2fox/vibepetcore/box/BoxManagerBasicOpenRollbackTest.java`
- `src/test/java/dev/li2fox/vibepetcore/pet/PetEngineManagerActivationRollbackTest.java`

Минимум проверить:
- save-fail после destructive `turn-in` не оставляет игрока без предметов;
- `QuestProgressData.completed()` не остаётся `true`;
- points не прибавлены после rollback;
- `statistics().questsCompleted()` не увеличен после rollback;
- success path по-прежнему проходит.

## Команды проверки после фикса

```powershell
./gradlew test
./gradlew processResources compileJava test jar check
```

## Что не делать в этом проходе

- не трогать `evolution`;
- не трогать `forge`;
- не трогать `core repair`;
- не распиливать `VibePetCommandHandler` или весь `QuestManager` ради красоты.
