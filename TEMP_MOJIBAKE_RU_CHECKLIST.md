# Mojibake RU Checklist

Дата: 2026-04-26

Искал типичные следы битой русской кодировки:
- `\u00c3`
- `\u0090`
- `\u00d0`
- `\u00d1`

Найдено массово:

1. `C:\Users\Li2Fox\IdeaProjects\VibePetCore\src\main\java\dev\li2fox\vibepetcore\core\VibePetHelpSupport.java`
- около 159 срабатываний
- начинать с первых строк файла
- первые проблемные места: 18, 19, 20, 21, 22
- файл выглядит как главный очаг битых русских help/message строк

2. `C:\Users\Li2Fox\IdeaProjects\VibePetCore\src\main\java\dev\li2fox\vibepetcore\core\VibePetCommandHandler.java`
- около 120 срабатываний
- первые проблемные места: 112, 114, 120, 124, 135
- тоже массово забит битыми русскими сообщениями

Что проверять руками:
- `sender.sendMessage(...)`
- списки help/guide строк
- описания ролей питомцев
- evolution/help summaries
- строки с `DANGER-DELETE`, `lootbox`, `master`, `npc`, `help`

Быстрый порядок чистки:
1. `VibePetHelpSupport.java`
2. `VibePetCommandHandler.java`

Приметы, что строка битая:
- длинные цепочки `\u00c3\u0090...`
- в тексте видны куски вида `Ð`, `Ñ`, `Ã`
- русский смысл явно должен быть, но строка выглядит как мусор

Примеры стартовых мест:

`VibePetHelpSupport.java`
- 18
- 19
- 20
- 21
- 22
- 23
- 24
- 25

`VibePetCommandHandler.java`
- 112
- 114
- 120
- 124
- 135
- 145
- 149
- 154

Замечание:
- этот список временный и специально узкий
- пока явная масса проблемы локализуется в двух файлах выше
