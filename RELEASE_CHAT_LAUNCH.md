# VibePetCore Release Chat Launch

Статус: быстрый запуск следующих чатов без ручной сборки prompt.

Правило:
- один новый чат = одна роль;
- один Builder-чат = один task ID;
- после каждого Builder-pass сразу отдельный Reviewer-чат;
- не пропускать reviewer перед следующим `P0`.

## Запуск 1: Builder Quest Save

Вставить в новый Builder-чат:

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-QUEST-SAVE-01

Прочитай сначала:
- AGENTS / repo instructions if present
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-QUEST-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Работай строго в scope task ID.
Не трогай evolution/forge/core repair.
После фикса:
- regression-test
- ./gradlew test
- ./gradlew processResources compileJava test jar check
- commit/push

Формат ответа:
- findings first
- что изменено
- тесты
- residual risk
```

## Запуск 2: Reviewer Quest Save

Запускать только после завершения Builder Quest Save.

Вставить в новый Reviewer-чат:

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-QUEST-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-QUEST-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Проверь последний diff как release pass.
Ищи только реальные bugs, rollback gaps, GUI/command regressions и missing tests.
Если P0/P1 найден, Builder Evolution Save не запускать.

Формат ответа:
- findings first
- severity + file/line
- residual risk
```

## Запуск 3: Builder Evolution Save

Запускать только если Quest Builder + Quest Reviewer закрыты без незакрытого `P0/P1`.

Вставить в новый Builder-чат:

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-EVOLUTION-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Работай строго в scope task ID.
Не трогай quest/forge/core repair.
После фикса:
- regression-test
- ./gradlew test
- ./gradlew processResources compileJava test jar check
- commit/push
```

## Запуск 4: Reviewer Evolution Save

Вставить в новый Reviewer-чат:

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-EVOLUTION-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-EVOLUTION-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Проверь последний diff как release pass.
Если P0/P1 найден, Builder Forge Save не запускать.
```

## Запуск 5: Builder Forge Save

Запускать только если Evolution Builder + Evolution Reviewer закрыты без незакрытого `P0/P1`.

Вставить в новый Builder-чат:

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-FORGE-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-FORGE-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Работай строго в scope task ID.
Не трогай quest/evolution/core repair.
После фикса:
- regression-test
- ./gradlew test
- ./gradlew processResources compileJava test jar check
- commit/push
```

## Запуск 6: Reviewer Forge Save

Вставить в новый Reviewer-чат:

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-FORGE-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-FORGE-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Проверь последний diff как release pass.
Если P0/P1 найден, Builder Core Repair Save не запускать.
```

## Запуск 7: Builder Core Repair Save

Запускать только если Forge Builder + Forge Reviewer закрыты без незакрытого `P0/P1`.

Вставить в новый Builder-чат:

```text
Use project-conductor.
Role: Builder.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Работай строго в scope task ID.
Не трогай quest/evolution/forge.
После фикса:
- regression-test
- ./gradlew test
- ./gradlew processResources compileJava test jar check
- commit/push
```

## Запуск 8: Reviewer Core Repair Save

Вставить в новый Reviewer-чат:

```text
Use project-conductor.
Role: Reviewer.
Project: C:\Users\Li2Fox\IdeaProjects\VibePetCore
Task ID: VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01

Прочитай сначала:
- RELEASE_EXECUTION_QUEUE.md
- VPC-REL-BUGHUNT-CORE-REPAIR-SAVE-01.md
- RELEASE_TASK_PROMPTS.md

Проверь последний diff как release pass.
Если P0/P1 найден, destructive smoke не запускать.
```

## Что потом

Если все 4 Builder/Reviewer цикла зелёные:
- запускать controlled destructive smoke;
- только потом admin mutation save audit;
- только потом TPS / structural follow-up.
