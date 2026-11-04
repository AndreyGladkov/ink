# Ink Messages

SMS and MMS app for Android phones with e-ink screens, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md).

Every pixel is pure black or white: text without antialiasing, two-tone icons and emoji, no animations. Pictures in messages are shown as 1-bit dithered thumbnails; the full-screen viewer shows the original. Lists and conversations turn by pages.

[Русская версия ниже](#ink-messages-1)

## Features

- Categories as tabs: Favorites (starred contacts, shared with Ink Phone), Contacts, Other (banks, codes, unknown numbers) and All, each with an unread counter. Choose which one opens at start.
- Conversations open on the newest page; older pages are above. Day headers, time under the last message of a run, "sending…" and "not sent — retry".
- Default SMS app: send and receive SMS, long messages in several parts, a counter near the part boundary, drafts, a SIM choice when two SIMs are active.
- MMS: downloaded automatically, pictures as black-and-white thumbnails, a viewer with Save and Share, contacts open in Ink Phone, audio and video in the app you choose.
- Notifications per category with Reply, Mark read and "Copy 482913" for one-time codes.
- Search by name, number and message text; a found message opens in its conversation.
- Long press a message: Copy, Forward, Delete. Conversation menu: Unread, Block, Delete. Blocking uses the system list shared with the phone app.
- Tap a conversation's name to open the contact in Ink Phone, or to add an unknown number.
- Settings for text size, font weight, button corners, colors, tab labels, list mode and button vibration; optional full panel refresh on the Hisense A9 every N pages through [A9PatchingService](https://github.com/AndreyGladkov/A9PatchingService).

Ink Messages has no internet permission. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkMessages-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Messages `, because the repository also publishes other apps.

Signing certificate SHA-256: `37:48:9C:71:A3:36:CB:3E:FF:F0:FD:6D:7C:25:BE:32:5D:5D:7A:AC:8C:AB:B8:EE:6F:64:C4:44:49:DA:99:DB`.

After installing, open the app and tap "Make default" so it can send and receive messages; until then it only reads them. Android shows the choice once.

## Building

```
./gradlew :ink-messages:testDebugUnitTest :ink-messages:lintRelease :ink-messages:assembleRelease
```

Release signing is read from `local.properties` (`signing.messages.storeFile`, `signing.messages.storePassword`, `signing.messages.keyAlias`, `signing.messages.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Messages is free software under the [GNU General Public License v3](LICENSE).

Bundled components: the MMS parser from AOSP Messaging (`mmslib`, Apache License 2.0), Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Messages

SMS и MMS для Android-телефонов с e-ink экраном, сделано под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1).

Каждый пиксель чисто чёрный или белый: текст без сглаживания, двухцветные иконки и эмодзи, никаких анимаций. Картинки в сообщениях показываются однобитными миниатюрами с растром; полноэкранный просмотр показывает оригинал. Списки и переписки листаются страницами.

- Категории вкладками: Избранное (помеченные контакты, общие с Ink Phone), Контакты, Прочие (банки, коды, незнакомые номера) и Все, у каждой счётчик непрочитанных. Можно выбрать, какая открывается при запуске.
- Переписка открывается на последней странице, более старые выше. Заголовки дней, время под последним сообщением серии, «отправка…» и «не отправлено — повторить».
- Основное приложение для SMS: отправка и приём, длинные сообщения частями, счётчик у границы части, черновики, выбор SIM при двух активных картах.
- MMS: скачиваются сами, картинки чёрно-белыми миниатюрами, просмотр с «Сохранить» и «Поделиться», контакты открываются в Ink Phone, аудио и видео — в выбранном приложении.
- Уведомления по категориям с «Ответить», «Прочитано» и «Копировать 482913» для одноразовых кодов.
- Поиск по имени, номеру и тексту сообщений; найденное сообщение открывается в своей переписке.
- Долгое нажатие на сообщение: Копировать, Переслать, Удалить. Меню переписки: Непрочитано, Заблокировать, Удалить. Блокировка — через системный список, общий со звонилкой.
- Нажатие на имя в переписке открывает контакт в Ink Phone или добавляет незнакомый номер.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов, подписей вкладок, режима списков и вибрации кнопок; полная перерисовка панели Hisense A9 каждые N страниц через [A9PatchingService](https://github.com/AndreyGladkov/A9PatchingService).

У Ink Messages нет разрешения на доступ в интернет. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkMessages-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Messages ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `37:48:9C:71:A3:36:CB:3E:FF:F0:FD:6D:7C:25:BE:32:5D:5D:7A:AC:8C:AB:B8:EE:6F:64:C4:44:49:DA:99:DB`.

После установки откройте приложение и нажмите «Сделать основным», чтобы оно отправляло и принимало сообщения; до этого оно их только показывает.
