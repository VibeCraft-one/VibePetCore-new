# VibePetCore Release Execution Queue

Статус: кодовый release bug-hunt закрыт на `2.6.33`, в очереди остался только ручной stack gate.

Цель:
- не спорить о порядке;
- не смешивать несколько destructive фиксов в один проход;
- пропускать следующий шаг только после маленькой проверяемой победы.

## Текущая отправная точка

- ветка: `codex/refactor-gui-pages`
- базовая сборка зелёная
- закрыты кодом и тестами:
  - `VPC-REL-BUGHUNT-QUEST-SAVE-01`
  - `VPC-REL-BUGHUNT-EVOLUTION-SAVE-01`
  - `VPC-REL-BUGHUNT-FORGE-SAVE-01`
  - `VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01`
- дополнительно закрыты player-facing регрессии:
  - `offhand/main-hand truthfulness` для summon/help
  - personal `/pet` core targeting
  - empty-core overwrite при recall второго питомца того же типа
- живым smoke уже подтверждены:
  - `Source offhand conflict`
  - `quest accept/turn-in`
  - `forge upgrade spend path`
  - `Source box spend path`
  - `actual evolution button`
  - `core repair`
- следующий обязательный проход: `VPC-REL-STACK-MANUAL-01`
- launch-файл для новых чатов: `RELEASE_CHAT_LAUNCH.md`
- handoff уже подготовлены для:
  - `VPC-REL-BUGHUNT-QUEST-SAVE-01`
  - `VPC-REL-BUGHUNT-EVOLUTION-SAVE-01`
  - `VPC-REL-BUGHUNT-FORGE-SAVE-01`
  - `VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01`

## Обязательный порядок

### 1. Stack / Manual Release Gate

Стартовать только если `SMOKE-2.6.26.txt` зелёный и кодовый bug-hunt не дал новых `P0/P1`.

Что осталось доказать руками:
- тот же plugin jar на реальном proxy/lobby/plugin stack не даёт неприятный first-player UX;
- 2-3 игрока не ловят заметных MSPT spikes при summon/Source/GUI/quest/forge/evolution;
- рядом с реальными соседними плагинами нет конфликтов по inventory/gui/offhand flow.

Результат:
- exact pass/fail шаги зафиксированы в `TEST_CHECKLIST.md`;
- если найден `P0/P1`, сначала узкий fix, потом повтор;
- если pass, решение можно поднимать до `release candidate`.

### 2. Только если stack gate найдёт новый баг

- взять один конкретный `P0/P1`;
- сделать узкий fix;
- добавить regression-test, если это возможно без live server;
- прогнать:
  - `./gradlew test`
  - `./gradlew processResources compileJava test jar check`
- обновить `CODEX_CONTEXT.md` и `TEST_CHECKLIST.md`;
- повторить stack/manual pass только по затронутой цепочке.

## Stop-rules

- если manual gate не дал нового `P0/P1`, не открывать новый code-audit ради красоты;
- если найден новый баг, брать только одну цепочку за проход;
- если нет regression-test на новый rollback-contract, проход не считается закрытым;
- если `./gradlew test` или полный build красный, дальше не двигаться;
- если живой smoke противоречит коду или тестам, сначала фикс/док, потом следующий шаг.

## Минимальный done для следующего реального pass

- либо `stack/manual gate` прошёл без нового `P0/P1`;
- либо найден ровно один новый `P0/P1`, узко закрыт кодом и тестом;
- `./gradlew test` зелёный;
- `./gradlew processResources compileJava test jar check` зелёный;
- если поведение для игрока изменилось, docs/smoke обновлены;
- после этого ветка снова чистая.

## Чего не делать

- не возобновлять старые `quest/evolution/forge/repair` очереди без нового доказательства;
- не тащить structural refactor до ручного релизного gate;
- не поднимать честный `% до релиза` до `100`, пока stack/manual pass не завершён.
