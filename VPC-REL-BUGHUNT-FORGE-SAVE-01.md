# VPC-REL-BUGHUNT-FORGE-SAVE-01

Статус: `confirmed by code audit`

Цель:
- закрыть risky destructive save-flow у `forge` rarity upgrade;
- не расползаться в большой GUI refactor;
- дать Builder-чатy точную точку входа с линиями и критерием закрытия.

## Почему это P0

Текущий forge flow может одновременно:
- уничтожить donor eggs;
- изменить rarity активного ядра;
- обновить runtime pet data;
- переписать held core item;
- показать игроку success/fail сообщение.

Но в самом destructive flow не видно явного immediate `save/rollback` контракта.

Если `playerData save` падает после forge attempt, есть риск:
- потери donor eggs без сохранённого результата;
- рассинхрона `held core <-> runtime <-> playerdata`;
- ложного успешного GUI результата;
- некорректного recovery после релога/рестарта.

## Прямые улики по коду

### 1. `attemptRarityUpgrade(...)` сначала собирает donor slots, потом уничтожает donor eggs

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `666-701`

Смысл:
- считается количество нужных donor eggs;
- затем цикл `for (int slot : donorSlots) { consumeOneInventorySlot(player, slot); }`;
- donor eggs уничтожаются до завершения всего flow.

### 2. После уничтожения donor eggs меняется rarity активного pet/core state

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `703-710`

Смысл:
- кидается шанс;
- при success вызывается `petData.setRarity(rarity.next().name())`;
- затем `petEngineManager.replaceActivePetData(player, petData)`;
- затем `setHeldPetCore(player, core, writeCoreForState(core.item(), petData))`.

### 3. `replaceActivePetData(...)` меняет in-memory playerdata/runtime, но сам не сохраняет

Файл:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`

Строки:
- `359-369`

Смысл:
- progression копируется в stored pet и runtime pet;
- вызова `playerDataManager.save(...)` внутри нет.

### 4. `setHeldPetCore(...)` только переписывает предмет в руке игрока

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `467-472`

Смысл:
- предмет ядра меняется прямо в main/off hand;
- это UI/runtime change, а не persistence contract.

### 5. Уничтожение donor eggs реально физическое, без rollback-механизма рядом

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `845-856`

Смысл:
- `consumeOneInventorySlot(...)` просто уменьшает stack или ставит `null`;
- отдельного snapshot/rollback для donor slots рядом не видно.

### 6. `save(...)` существует отдельно и требует явного вызова

Файл:
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Строки:
- `103-116`

Смысл:
- persistence идёт только через отдельный `save(playerId)`;
- в forge flow этот контракт явно не вызывается.

### 7. GUI прямо ведёт игрока в этот destructive flow

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/SourceForgePage.java`

Строки:
- `46-60`
- `67-68`

Смысл:
- слот `22` — это forge attempt button;
- GUI обещает апгрейд и donor chest flow;
- значит ложный success/fail здесь бьёт прямо по первому опыту игрока.

## Что Builder должен сделать

Минимальный безопасный scope:
- не делать большой распил `PetGuiService`;
- закрыть только destructive save-flow forge;
- сохранить текущий GUI contract и rarity logic;
- добавить regression-test.

Ожидаемая форма фикса:
- собрать snapshot donor slots до consume;
- собрать snapshot progression state у target pet до rarity attempt;
- не оставлять уничтоженные donor eggs без успешного save;
- если save не удался:
  - восстановить donor eggs;
  - откатить rarity/progression state;
  - откатить held core item;
  - не оставлять runtime/playerdata/core в разных состояниях;
  - вернуть save-failed результат вместо ложного success.

## Что должен доказать тест

Нужен новый regression-test.

Рекомендуемое место:
- `src/test/java/dev/li2fox/vibepetcore/gui/PetGuiServiceForgeRollbackTest.java`

Полезные ориентиры по стилю:
- `src/test/java/dev/li2fox/vibepetcore/box/BoxManagerBasicOpenRollbackTest.java`
- `src/test/java/dev/li2fox/vibepetcore/pet/PetEngineManagerActivationRollbackTest.java`

Что проверить минимум:
- save-fail после consume не оставляет потерянные donor eggs;
- rarity target pet не меняется после rollback;
- held core item не врёт о rarity после rollback;
- runtime/playerdata остаются согласованными;
- success path по-прежнему работает.

## Команды проверки после фикса

```powershell
./gradlew test
./gradlew processResources compileJava test jar check
```

## Что не делать в этом проходе

- не лезть в `quest turn-in`;
- не лезть в `evolution`;
- не лезть в `core repair`;
- не смешивать bugfix с большим GUI refactor.
