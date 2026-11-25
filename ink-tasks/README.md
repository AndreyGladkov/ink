# Ink Tasks

A to-do list for Android phones with e-ink screens, in the manner of Google Tasks, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md), [Ink Clock](../ink-clock/README.md), [Ink Calculator](../ink-calculator/README.md), [Ink Launcher](../ink-launcher/README.md), [Ink Weather](../ink-weather/README.md) and [Ink Gallery](../ink-gallery/README.md). Tasks are kept in plain Markdown files in the format of the [Obsidian Tasks](https://publish.obsidian.md/tasks/) plugin, so a folder of them in an Obsidian vault syncs with a computer.

Every pixel is pure black or white, with no antialiasing and no animations.

[Русская версия ниже](#ink-tasks-1)

## Features

- Several lists, each a Markdown file: "Groceries.md" is the list "Groceries". The arrows at the bottom of a list go to the next or previous one, and dots under the page number show which is open, as on Ink Launcher's home screens.
- A task has a title, notes, a due date and one level of subtasks. A tap on its box marks it done; done tasks go to a page of their own, from where they come back or are deleted together.
- Due dates are set with Today, Tomorrow and In a week, or on a month calendar. An overdue date is drawn white on black.
- A task with a due date can get a time, typed on a keypad; at that time a notification comes with Done and In an hour. The time is written in the format of the [Reminder](https://uphy.github.io/obsidian-reminder/) plugin, so reminders set in Obsidian ring on the phone too; with the lists in a chosen folder the app reads them again about every half hour to catch those.
- A list is shown in its own order or by due date. A task is moved by tapping one of the "Here" marks between the others, with no dragging.
- Deleting asks for a second tap of the same button instead of a dialog.
- Without setup the lists are kept only on the phone. In Settings → Storage a folder can be chosen in the system picker, for example Vault/Tasks in an Obsidian vault; the lists on the phone move there, and from then on the files are synced by whatever syncs the vault (Obsidian Sync, Syncthing and so on).
- The app reads a file anew whenever it is opened and before each change, and rewrites only the lines it changes: headings, paragraphs, frontmatter, code and other plugins' tags stay as they are, byte for byte.
- Settings for text size, font weight, button corners, colors, lists and vibration, shared with the other Ink apps.

The file format:

```
- [ ] Buy milk ⏰ 2026-11-25 18:00 📅 2026-11-25
	2.5%, not UHT
	- [ ] And bread
- [x] Call mum ✅ 2026-11-24
```

Ink Tasks does not use the internet. It asks only to show notifications, for reminders, and sets alarms to show them on time. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkTasks-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Tasks `, because the repository also publishes other apps.

Signing certificate SHA-256: `3F:24:1B:4F:6F:12:03:4B:D3:2D:6F:26:46:F5:3B:0E:91:B2:83:52:33:4C:80:8F:06:6D:A5:8E:6E:4F:13:A1`.

## Building

```
./gradlew :ink-tasks:testDebugUnitTest :ink-tasks:lintRelease :ink-tasks:assembleRelease
```

Release signing is read from `local.properties` (`signing.tasks.storeFile`, `signing.tasks.storePassword`, `signing.tasks.keyAlias`, `signing.tasks.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Tasks is free software under the [GNU General Public License v3](LICENSE).

Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Tasks

Список задач для Android-телефонов с e-ink экраном в духе Google Tasks, сделан под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1), [Ink Clock](../ink-clock/README.md#ink-clock-1), [Ink Calculator](../ink-calculator/README.md#ink-calculator-1), [Ink Launcher](../ink-launcher/README.md#ink-launcher-1), [Ink Weather](../ink-weather/README.md#ink-weather-1) и [Ink Gallery](../ink-gallery/README.md#ink-gallery-1). Задачи хранятся в обычных md-файлах в формате плагина [Obsidian Tasks](https://publish.obsidian.md/tasks/), поэтому папка с ними в хранилище Obsidian синхронизируется с компьютером.

Каждый пиксель чисто чёрный или белый, без сглаживания и анимаций.

- Несколько списков, каждый — md-файл: «Покупки.md» — это список «Покупки». Стрелки внизу списка листают списки, а точки под номером страницы показывают, какой открыт, как экраны в Ink Launcher.
- У задачи есть название, заметка, срок и один уровень подзадач. Нажатие на квадрат отмечает её выполненной; выполненные уходят на свою страницу, откуда их можно вернуть или удалить все разом.
- Срок ставится кнопками «Сегодня», «Завтра», «Через неделю» или по календарю месяца. Просроченный срок — белым по чёрному.
- Задаче со сроком можно задать время, набрав его цифрами; в это время приходит уведомление с кнопками «Выполнено» и «Через час». Время записывается в формате плагина [Reminder](https://uphy.github.io/obsidian-reminder/), поэтому напоминания, поставленные в Obsidian, звонят и на телефоне; когда списки лежат в выбранной папке, приложение перечитывает их примерно раз в полчаса, чтобы такие не пропустить.
- Список показывается в своём порядке или по сроку. Задача перемещается нажатием на одну из отметок «Сюда» между другими, без перетаскивания.
- Удаление подтверждается повторным нажатием той же кнопки, без диалогов.
- Без настройки списки хранятся только на телефоне. В «Настройках» → «Хранение» можно выбрать папку через системный выбор, например Vault/Tasks в хранилище Obsidian; списки с телефона переносятся туда, и дальше файлы синхронизирует то же, что синхронизирует хранилище (Obsidian Sync, Syncthing и т. п.).
- Файл перечитывается при каждом открытии приложения и перед каждым изменением, а переписываются только изменённые строки: заголовки, абзацы, свойства в начале файла, код и метки других плагинов остаются как были, байт в байт.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов, списков и вибрации — общие с другими приложениями Ink.

Ink Tasks не ходит в интернет. Из разрешений приложение просит только показ уведомлений — для напоминаний — и ставит будильники, чтобы показать их вовремя. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkTasks-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Tasks ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `3F:24:1B:4F:6F:12:03:4B:D3:2D:6F:26:46:F5:3B:0E:91:B2:83:52:33:4C:80:8F:06:6D:A5:8E:6E:4F:13:A1`.
