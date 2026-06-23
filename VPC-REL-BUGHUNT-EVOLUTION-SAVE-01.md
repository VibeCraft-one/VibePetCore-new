# VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Статус: `confirmed by code audit`

Цель:
- закрыть risky destructive save-flow у эволюции активного питомца;
- не расползаться в большой `PetEngine` refactor;
- дать Builder-чатy точную точку входа с линиями и критерием закрытия.

## Почему это P0

Текущий evolution flow может одновременно:
- списать эволюционные ресурсы из inventory/vault;
- изменить `evolutionStage`, `level`, `subLevel`, `bond`, `xp`;
- показать игроку успешную или неуспешную попытку;
- обновить runtime name/effects.

Но в самом destructive flow не видно явного immediate `save/rollback` контракта.

Если `playerData save` падает после попытки эволюции, есть риск:
- потери ресурсов без сохранённой новой стадии;
- рассинхрона `runtime <-> playerdata <-> core/vault`;
- ложного успешного GUI/command результата;
- кривого recovery после релога/рестарта.

## Прямые улики по коду

### 1. `tryEvolveActivePet(...)` тратит ресурсы до эволюции

Файл:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionFlowSupport.java`

Строки:
- `78-83`

Смысл:
- для не-creative игрока вызывается `consumeEvolutionMaterials.accept(...)`;
- сразу после этого вызывается `progressionAPI.tryEvolve(runtimePet.data(), chance)`;
- явного `playerDataManager.save(...)` рядом нет.

### 2. `consumeEvolutionMaterials(...)` реально списывает предметы из inventory и vault

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEvolutionRuntimeSupport.java`

Строки:
- `PetEngineManager.java:1357-1358`
- `PetEvolutionRuntimeSupport.java:54-57`
- `PetEvolutionRuntimeSupport.java:74-86`

Смысл:
- materials списываются из инвентаря игрока;
- остаток добирается через `petVaultService.consumeOne(...)`;
- отдельного rollback-механизма в этом flow не видно.

### 3. `tryEvolve(...)` меняет stage и progression state прямо в `OwnedPetData`

Файл:
- `src/main/java/dev/li2fox/vibepetcore/api/impl/CoreProgressionAPI.java`

Строки:
- `126-145`

Смысл:
- при success вызывается:
  - `pet.setEvolutionStage(...)`
  - `pet.setLevel(...)`
  - `pet.setSubLevel(...)`
  - `pet.setBond(0)`
  - `pet.setXp(0L)`
- это изменение происходит до любого доказанного save-контракта в текущем flow.

### 4. `PetEngineManager.tryEvolveActivePet(...)` только прокидывает flow, но не добавляет save/rollback

Файл:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`

Строки:
- `1204-1218`

Смысл:
- manager просто делегирует в `PetEvolutionFlowSupport.tryEvolveActivePet(...)`;
- в этой обвязке save не добавлен.

### 5. success-path заканчивается визуальным эффектом и сообщением

Файл:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`

Строки:
- `1465`
- `1502-1504`

Смысл:
- `finishEvolution(...)` занимается visual/message path;
- по этому куску не видно, что он фиксирует persistence contract.

### 6. Команда и GUI входят в тот же risky flow

Файлы:
- `src/main/java/dev/li2fox/vibepetcore/core/VibePetCommandHandler.java`
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`
- `src/main/java/dev/li2fox/vibepetcore/gui/PetInfoPage.java`

Строки:
- `VibePetCommandHandler.java:655`
- `PetGuiService.java:716-717`
- `PetInfoPage.java:83`

Смысл:
- `/pet evolve` и GUI используют один и тот же `petEngineManager.tryEvolveActivePet(player)`;
- значит баг один и тот же для command и GUI path.

## Что Builder должен сделать

Минимальный безопасный scope:
- не переписывать весь `PetEngineManager`;
- закрыть только destructive save-flow эволюции;
- сохранить текущий UX шанс/near-far/effect contract;
- добавить regression-test.

Ожидаемая форма фикса:
- собрать snapshot `OwnedPetData` до попытки;
- иметь rollback для materials из inventory/vault при `save-fail`;
- не оставлять изменённый `stage/level/bond/xp` без успешного save;
- если save не удался:
  - откатить pet progression state;
  - откатить materials;
  - вернуть false или отдельный save-failed result без ложного success message;
  - не оставлять runtime/core/playerdata в разных состояниях.

## Что должен доказать тест

Нужен новый regression-test.

Рекомендуемое место:
- `src/test/java/dev/li2fox/vibepetcore/pet/PetEvolutionFlowRollbackTest.java`

Полезные ориентиры по стилю:
- `src/test/java/dev/li2fox/vibepetcore/box/BoxManagerBasicOpenRollbackTest.java`
- `src/test/java/dev/li2fox/vibepetcore/pet/PetEngineManagerActivationRollbackTest.java`

Что проверить минимум:
- save-fail после consume + evolve не оставляет списанные materials;
- `evolutionStage/level/subLevel/bond/xp` откатываются;
- runtime/playerdata не расходятся после rollback;
- success path по-прежнему работает;
- existing test `CoreProgressionAPITest` не ломается.

## Команды проверки после фикса

```powershell
./gradlew test
./gradlew processResources compileJava test jar check
```

## Что не делать в этом проходе

- не лезть в `quest turn-in`;
- не лезть в `forge`;
- не лезть в `core repair`;
- не делать общий распил `PetEngineManager` ради красоты.
