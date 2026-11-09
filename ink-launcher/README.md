# Ink Launcher

A home screen for Android phones with e-ink screens, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md), [Ink Clock](../ink-clock/README.md) and [Ink Calculator](../ink-calculator/README.md).

Every pixel the launcher draws is pure black or white, with no antialiasing and no animations. Pages change at once, without sliding, and only the paged rows are redrawn.

[Русская версия ниже](#ink-launcher-1)

## Features

- One grid of equal cells, 4 × 7 by default. Rows pinned on top and at the bottom stay the same on every page; the rows between them page with the arrows or a swipe.
- Apps, folders and widgets go into any cell. A widget spans several cells and is resized with + and − instead of dragging.
- A long press on a tile opens its menu: rename, change the icon, hide the label, move, put into a folder, remove. Moving takes two taps: Move, then the cell; dropped on an app, an app makes a folder.
- A long press on an empty cell adds an app, a widget or a folder there.
- A widget whose app lets it be set up again, like [Ink Weather](../ink-weather/README.md), has Configure… in its menu.
- Icons come from an ADW or Nova compatible icon pack, from the app's themed monochrome icon, or from its ordinary icon; colors are dithered to black and white. Any icon can be replaced with a picture or with text, and every icon can get a frame.
- Left of the first page is a notification screen, grouped by app, with a number or a dot next to the left arrow instead of badges on icons, or nothing, as set in the settings. Right of the last page is the list of all apps by name.
- A screen time tile, among the widgets, shows today's time split in two: Drifting, for the apps you mark as such (a reader, a feed), and On purpose, for everything else. Launchers and the keyboard in use start as Don't count; any app can be set to that too. Below them is the number of unlocks. A tap opens the last seven days, by day and by app; an app's category is chosen from its menu or from that list. The tile needs usage access, which you grant yourself in system settings.
- Making the grid smaller moves whatever no longer fits to the end; nothing is removed.
- Settings for text size, font weight, button corners, colors, lists and vibration, shared with the other Ink apps.

Ink Launcher has no internet permission. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer. After installing, choose Ink Launcher as the home app in its settings or in the system settings.

- **GitHub:** download `InkLauncher-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Launcher `, because the repository also publishes other apps.

Signing certificate SHA-256: `24:E6:D3:4B:08:E4:D2:BF:BB:A0:2F:C4:2F:D8:8A:08:E8:EF:99:E0:E1:1A:44:9A:24:65:4A:AA:3C:2E:04:D5`.

## Building

```
./gradlew :ink-launcher:testDebugUnitTest :ink-launcher:lintRelease :ink-launcher:assembleRelease
```

Release signing is read from `local.properties` (`signing.launcher.storeFile`, `signing.launcher.storePassword`, `signing.launcher.keyAlias`, `signing.launcher.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Launcher is free software under the [GNU General Public License v3](LICENSE).

Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Launcher

Домашний экран для Android-телефонов с e-ink экраном, сделан под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1), [Ink Clock](../ink-clock/README.md#ink-clock-1) и [Ink Calculator](../ink-calculator/README.md#ink-calculator-1).

Всё, что рисует лаунчер, чисто чёрное или белое, без сглаживания и анимаций. Страницы меняются сразу, без прокрутки, и перерисовываются только листаемые ряды.

- Одна сетка из одинаковых ячеек, по умолчанию 4 × 7. Закреплённые ряды сверху и снизу одинаковы на всех страницах; ряды между ними листаются стрелками или свайпом.
- В любую ячейку можно положить приложение, папку или виджет. Виджет занимает несколько ячеек, его размер меняется кнопками + и −, без перетаскивания.
- Долгое нажатие на иконку открывает меню: переименовать, сменить иконку, скрыть подпись, переместить, положить в папку, убрать. Перемещение — в два нажатия: «Переместить», потом ячейка; приложение, положенное на приложение, создаёт папку.
- Долгое нажатие на пустую ячейку добавляет туда приложение, виджет или папку.
- Если приложение позволяет перенастроить свой виджет, как [Ink Weather](../ink-weather/README.md#ink-weather-1), в меню виджета есть «Настроить…».
- Иконки берутся из набора, совместимого с ADW или Nova, из монохромной тематической иконки приложения или из обычной; цвета переводятся в чёрно-белые дизерингом. Любую иконку можно заменить картинкой или текстом, а вокруг иконок можно включить обводку.
- Слева от первой страницы — экран уведомлений, сгруппированных по приложениям; вместо значков на иконках — число или точка рядом с левой стрелкой, а можно и ничего: это выбирается в настройках. Справа от последней страницы — список всех приложений по алфавиту.
- Среди виджетов есть плитка экранного времени: время за сегодня в ней делится на «Залип» — в приложениях, которые вы так отметили (читалка, лента), — и «По делу» — во всех остальных, включая сам лаунчер. Под ними — число разблокировок. Нажатие открывает последние семь дней по дням и по приложениям; отметить приложение можно в его меню или в этом списке. Плитке нужен доступ к истории использования, который вы даёте сами в системных настройках.
- Если уменьшить сетку, всё, что перестало помещаться, переезжает в конец; ничего не удаляется.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов, списков и вибрации — общие с другими приложениями Ink.

У Ink Launcher нет разрешения на доступ в интернет. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее. После установки выберите Ink Launcher домашним экраном в его настройках или в настройках системы.

- **GitHub:** скачайте `InkLauncher-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Launcher ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `24:E6:D3:4B:08:E4:D2:BF:BB:A0:2F:C4:2F:D8:8A:08:E8:EF:99:E0:E1:1A:44:9A:24:65:4A:AA:3C:2E:04:D5`.
