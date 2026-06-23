# VibePetCore Release Execution Queue

Статус: рабочая очередь исполнения после code-audit handoff.

Цель:
- не спорить о порядке;
- не смешивать несколько destructive фиксов в один проход;
- пропускать следующий шаг только после маленькой проверяемой победы.

## Текущая отправная точка

- ветка: `codex/refactor-gui-pages`
- базовая сборка зелёная
- launch-файл для новых чатов: `RELEASE_CHAT_LAUNCH.md`
- handoff уже подготовлены для:
  - `VPC-REL-BUGHUNT-QUEST-SAVE-01`
  - `VPC-REL-BUGHUNT-EVOLUTION-SAVE-01`
  - `VPC-REL-BUGHUNT-FORGE-SAVE-01`
  - `VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01`

## Обязательный порядок

### 1. Builder: Quest Save

- взять только `VPC-REL-BUGHUNT-QUEST-SAVE-01`
- сделать минимальный fix
- добавить regression-test
- прогнать:
  - `./gradlew test`
  - `./gradlew processResources compileJava test jar check`

Потом сразу:
- `Reviewer` на свежий diff

### 2. Builder: Evolution Save

Стартовать только если:
- quest pass зелёный;
- reviewer не оставил незакрытый `P0/P1`;
- ветка снова зелёная после правок reviewer.

Потом сразу:
- `Reviewer` на свежий diff

### 3. Builder: Forge Save

Стартовать только если:
- evolution pass зелёный;
- reviewer не оставил незакрытый `P0/P1`;
- ветка снова зелёная после правок reviewer.

Потом сразу:
- `Reviewer` на свежий diff

### 4. Builder: Core Repair Save

Стартовать только если:
- forge pass зелёный;
- reviewer не оставил незакрытый `P0/P1`;
- ветка снова зелёная после правок reviewer.

Потом сразу:
- `Reviewer` на свежий diff

### 5. Controlled Destructive Smoke

Стартовать только если все 4 save-flow pass закрыты кодом и тестами.

Что проверять живьём:
- `quest accept/turn-in`
- actual evolution attempt button
- forge upgrade
- core repair
- прямой `ПКМ` по установленному Источнику, когда core в offhand

Результат:
- обновить `SMOKE-2.6.23.txt` или новый smoke-файл

### 6. Admin Mutation Save Audit

Только после 4 player-facing destructive pass и smoke.

### 7. TPS / Structural Follow-up

Только после закрытия player-facing destructive `P0`.

## Stop-rules

- если Builder полез в соседний destructive flow, проход остановить и сузить scope;
- если нет regression-test на новый rollback-contract, проход не считается закрытым;
- если reviewer нашёл `P0/P1`, следующий Builder-pass не начинать;
- если `./gradlew test` или полный build красный, дальше не двигаться;
- если живой smoke противоречит коду или тестам, сначала фикс/док, потом следующий шаг.

## Минимальный done для каждого Builder-pass

- узкий diff под один task ID;
- regression-test на конкретный save-fail/rollback contract;
- `./gradlew test` зелёный;
- `./gradlew processResources compileJava test jar check` зелёный;
- reviewer-pass выполнен;
- если поведение для игрока изменилось, docs/smoke обновлены.

## Чего не делать

- не объединять `quest + evolution` в один commit;
- не тащить structural refactor до закрытия этих четырёх save-flow;
- не поднимать честный `% до релиза` выше, пока эти 4 pass не закрыты кодом и review.
