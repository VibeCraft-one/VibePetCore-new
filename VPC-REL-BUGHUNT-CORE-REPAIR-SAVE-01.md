# VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01

Статус: `confirmed by code audit`

Цель:
- закрыть risky destructive save-flow у `core repair`;
- не расползаться в большой GUI/pet refactor;
- дать Builder-чатy точную точку входа с линиями и критерием закрытия.

## Почему это P0 candidate

Текущий repair flow может одновременно:
- потратить `TOTEM_OF_UNDYING`;
- поднять durability ядра;
- снять `inactiveUntil`;
- восстановить satiety и health;
- обновить runtime pet data;
- переписать held core item;
- показать игроку успешный результат.

Но в самом destructive flow не видно явного immediate `save/rollback` контракта.

Если `playerData save` падает после repair attempt, есть риск:
- потери тотема без сохранённого результата;
- рассинхрона `held core <-> runtime <-> playerdata`;
- ложного GUI результата;
- кривого recovery после релога/рестарта.

## Прямые улики по коду

### 1. `repairCore(...)` сначала тратит тотем, потом меняет состояние ядра

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `741-755`

Смысл:
- считается количество тотемов;
- вызывается `consumeOneMaterial(player, Material.TOTEM_OF_UNDYING)`;
- затем меняются:
  - `durability`
  - `inactiveUntilMillis`
  - `satiety`
  - `health`
- затем вызываются `replaceActivePetData(...)` и `setHeldPetCore(...)`.

### 2. Уничтожение тотема реально физическое, без rollback рядом

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `828-842`

Смысл:
- `consumeOneMaterial(...)` уменьшает stack или ставит слот в `null`;
- отдельного snapshot/rollback для inventory рядом не видно.

### 3. `replaceActivePetData(...)` меняет in-memory playerdata/runtime, но сам не сохраняет

Файл:
- `src/main/java/dev/li2fox/vibepetcore/pet/PetEngineManager.java`

Строки:
- `359-369`

Смысл:
- progression/state копируется в stored pet и runtime pet;
- вызова `playerDataManager.save(...)` внутри нет.

### 4. `setHeldPetCore(...)` только переписывает предмет в руке игрока

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetGuiService.java`

Строки:
- `467-472`

Смысл:
- held core item меняется прямо в main/off hand;
- это UI/runtime change, а не persistence contract.

### 5. `save(...)` существует отдельно и требует явного вызова

Файл:
- `src/main/java/dev/li2fox/vibepetcore/player/PlayerDataManager.java`

Строки:
- `103-116`

Смысл:
- persistence идёт только через отдельный `save(playerId)`;
- в repair flow этот контракт явно не вызывается.

### 6. GUI прямо ведёт игрока в этот destructive flow

Файл:
- `src/main/java/dev/li2fox/vibepetcore/gui/PetOverviewPage.java`

Строки:
- `75-77`
- `239-264`

Смысл:
- слот `24` вызывает `gui.repairCore(player)`;
- lore обещает: клик съест `1` тотем и восстановит `1` durability;
- значит ложный успех здесь прямо бьёт по ожиданию игрока.

## Что Builder должен сделать

Минимальный безопасный scope:
- не делать большой распил `PetGuiService`;
- закрыть только destructive save-flow core repair;
- сохранить текущий GUI contract;
- добавить regression-test.

Ожидаемая форма фикса:
- собрать snapshot target pet state до repair;
- собрать snapshot inventory slot/stack с тотемом до consume;
- не оставлять потраченный тотем без успешного save;
- если save не удался:
  - восстановить тотем;
  - откатить durability/health/satiety/inactiveUntil;
  - откатить held core item;
  - не оставлять runtime/playerdata/core в разных состояниях;
  - вернуть save-failed результат вместо ложного success.

## Что должен доказать тест

Нужен новый regression-test.

Рекомендуемое место:
- `src/test/java/dev/li2fox/vibepetcore/gui/PetGuiServiceRepairRollbackTest.java`

Полезные ориентиры по стилю:
- `src/test/java/dev/li2fox/vibepetcore/box/BoxManagerBasicOpenRollbackTest.java`
- `src/test/java/dev/li2fox/vibepetcore/pet/PetEngineManagerActivationRollbackTest.java`

Что проверить минимум:
- save-fail после consume не оставляет потерянный тотем;
- durability/health/satiety/inactiveUntil откатываются;
- held core item не врёт после rollback;
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
- не лезть в `forge`;
- не смешивать bugfix с большим GUI/pet refactor.
