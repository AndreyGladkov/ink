# Ink Calendar

A calendar for Android phones with e-ink screens, laid out like the month view of the iPhone's Calendar, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md), [Ink Clock](../ink-clock/README.md), [Ink Calculator](../ink-calculator/README.md), [Ink Launcher](../ink-launcher/README.md), [Ink Weather](../ink-weather/README.md), [Ink Gallery](../ink-gallery/README.md) and [Ink Tasks](../ink-tasks/README.md). It shows the calendars already on the phone, so whatever your Google or CalDAV account syncs is there.

Every pixel is pure black or white, with no antialiasing and no animations.

[Русская версия ниже](#ink-calendar-1)

## Features

- The month on top and the chosen day's events below. Today is a filled circle, the chosen day is ringed, and a dot marks every day with something on it.
- Months turn whole, with the arrows or a swipe up or down, instead of scrolling; Today comes back. The month's title opens the year: twelve small months, a tap opens one.
- An event opens a card with its time, calendar, place, notes and reminders.
- «+» next to the chosen day adds an event: title, all day, start and end, calendar and one reminder. An event can be changed or deleted from its card; deleting asks for a second tap instead of a dialog. Repeating events and events of read-only calendars are shown but not changed here.
- With no account calendar to write to, events go to a calendar on the phone alone.
- Reminders come as notifications with In 10 minutes and Close. They can be turned off in Settings when another app, such as Google Calendar, already reminds you.
- A home screen widget drawn as one black-and-white picture: the date, then what is left of today and tomorrow; a tall widget adds the month. It is redrawn only when what it shows changes.
- With [Ink Tasks](../ink-tasks/README.md) installed, its open tasks with a due date follow the day's events, marked with an empty box and showing their list and reminder time, and they are on the widget too. An overdue task stays on its day. A tap opens the task in Ink Tasks. The calendar asks once for access to them; Settings can turn them off.
- Each calendar can be hidden in Settings. Settings for text size, font weight, button corners, colors, lists and vibration are shared with the other Ink apps.

Ink Calendar does not use the internet. It asks for access to the calendars on the phone, to show notifications, for reminders, and, with Ink Tasks installed, to read its tasks. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkCalendar-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Calendar `, because the repository also publishes other apps.

Signing certificate SHA-256: `BC:06:A8:09:8F:E5:A2:CC:EF:C7:A0:BD:14:CC:BF:14:9F:EF:B8:67:F6:38:D6:B9:9B:D9:4D:55:C5:76:CB:00`.

## Building

```
./gradlew :ink-calendar:testDebugUnitTest :ink-calendar:lintRelease :ink-calendar:assembleRelease
```

Release signing is read from `local.properties` (`signing.calendar.storeFile`, `signing.calendar.storePassword`, `signing.calendar.keyAlias`, `signing.calendar.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Calendar is free software under the [GNU General Public License v3](LICENSE).

Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Calendar

Календарь для Android-телефонов с e-ink экраном, устроенный как месяц в Календаре на iPhone, сделан под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1), [Ink Clock](../ink-clock/README.md#ink-clock-1), [Ink Calculator](../ink-calculator/README.md#ink-calculator-1), [Ink Launcher](../ink-launcher/README.md#ink-launcher-1), [Ink Weather](../ink-weather/README.md#ink-weather-1), [Ink Gallery](../ink-gallery/README.md#ink-gallery-1) и [Ink Tasks](../ink-tasks/README.md#ink-tasks-1). Он показывает календари, которые уже есть на телефоне, поэтому всё, что синхронизирует ваш аккаунт Google или CalDAV, сразу на месте.

Каждый пиксель чисто чёрный или белый, без сглаживания и анимаций.

- Сверху месяц, под ним события выбранного дня. Сегодня — залитый круг, выбранный день обведён, под днями с событиями точка.
- Месяцы перелистываются целиком стрелками или свайпом вверх и вниз, без прокрутки; «Сегодня» возвращает к текущему дню. Нажатие на название месяца открывает год: двенадцать маленьких месяцев, нажатие открывает нужный.
- Событие открывается карточкой со временем, календарём, местом, заметками и напоминаниями.
- «+» рядом с выбранным днём добавляет событие: название, весь день, начало и конец, календарь и одно напоминание. Событие можно изменить или удалить из карточки; удаление подтверждается повторным нажатием, без диалогов. Повторяющиеся события и события календарей только для чтения показываются, но не меняются.
- Если писать некуда, события сохраняются в календарь только на телефоне.
- Напоминания приходят уведомлением с кнопками «Через 10 минут» и «Закрыть». Их можно выключить в настройках, если о событиях уже напоминает другое приложение, например Google Календарь.
- Виджет на домашний экран, нарисованный одной чёрно-белой картинкой: дата и то, что осталось сегодня и завтра; высокий виджет показывает ещё и месяц. Он перерисовывается, только когда меняется то, что на нём.
- Если установлен [Ink Tasks](../ink-tasks/README.md#ink-tasks-1), его невыполненные задачи со сроком идут после событий дня — с пустым квадратом, списком и временем напоминания — и попадают на виджет. Просроченная задача остаётся в свой день. Нажатие открывает задачу в Ink Tasks. Доступ к задачам календарь спрашивает один раз; в настройках их можно выключить.
- Любой календарь можно скрыть в настройках. Настройки размера текста, толщины шрифта, скругления кнопок, цветов, списков и вибрации — общие с другими приложениями Ink.

Ink Calendar не ходит в интернет. Приложение просит доступ к календарям на телефоне, показ уведомлений — для напоминаний — и, если установлен Ink Tasks, чтение его задач. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkCalendar-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Calendar ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `BC:06:A8:09:8F:E5:A2:CC:EF:C7:A0:BD:14:CC:BF:14:9F:EF:B8:67:F6:38:D6:B9:9B:D9:4D:55:C5:76:CB:00`.
