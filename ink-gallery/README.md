# Ink Gallery

A photo and video gallery for Android phones with e-ink screens, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md), [Ink Clock](../ink-clock/README.md), [Ink Calculator](../ink-calculator/README.md), [Ink Launcher](../ink-launcher/README.md) and [Ink Weather](../ink-weather/README.md).

Everything but the photos and videos is pure black or white, with no antialiasing and no animations. Nothing scrolls: photos are turned like pages.

[Русская версия ниже](#ink-gallery-1)

## Features

- All photos and videos on the phone, newest first, as square thumbnails three to a row, a page at a time. A video is marked with a play sign and its length. The next page's thumbnails are fetched ahead, so turning the page draws them at once.
- A long press on a thumbnail starts picking: taps pick or drop more, on any page, and the bar in place of the header shares or deletes all the picked at once. Cancel or back ends it.
- Albums are the folders the photos and videos are in, listed by name and how many they hold, newest first.
- A photo opens on the whole screen. A tap on the left or right third or a sideways swipe turns to the previous or next photo of the grid it was opened from; a tap in the middle shows the counter and the buttons.
- A double tap in the middle doubles the size around it. While zoomed a drag moves the photo, which is redrawn once the finger lifts, and a double tap zooms back out.
- Share, Details (date taken, resolution, length of a video, size, file and folder) and Delete. Delete asks for a second tap; the system then asks once more, unless Ink Gallery is allowed to manage media in the settings.
- A video tapped in the grid plays at once on the whole screen; turned to among photos it shows its first frame with a play button. A tap shows the time, Share, Details and Delete, a progress line to tap a place on, −10 s, pause and +10 s; the controls stay until tapped away, and come back when the video ends. The screen stays on while it plays, and leaving the app pauses it until you come back.
- With A9PatchingService a video plays in SPEED mode; back gives the mode it was opened from: CLEAR for a photo, the usual one for the grid.
- Other apps can open pictures and videos in Ink Gallery; such a picture is shown alone, and a video plays.
- Wide photos can be turned to fill the tall screen, in the settings.
- With [A9PatchingService](https://github.com/AndreyGladkov/A9PatchingService) and its "Allow Control via Broadcasts" on, opening a photo puts the panel in CLEAR mode, which shows 16 real greys, and the photo is dithered to them with blue noise so smooth tones do not band. Going back to the grid or leaving the app brings the previous mode back; the photo's details keep CLEAR. Switching to CLEAR and dithering are set apart in the settings: dithering follows CLEAR by default, and can be kept on for a panel set to CLEAR by hand in A9PatchingService, or turned off to see CLEAR's own steps between greys instead of grain.
- With only some photos and videos allowed (Android 14 and newer), or photos without videos, the grid says so and asks again, where all can be allowed.
- The list refreshes each time the app comes back to the screen.
- Settings for text size, font weight, button corners, colors, lists, tab labels and vibration, shared with the other Ink apps.

Ink Gallery does not use the internet. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkGallery-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Gallery `, because the repository also publishes other apps.

Signing certificate SHA-256: `50:B8:5F:0A:96:2E:33:B4:92:DF:88:73:DA:C7:A8:BC:52:CA:B5:89:C1:42:D4:73:8D:F5:6C:6C:AA:42:4E:FA`.

## Building

```
./gradlew :ink-gallery:testDebugUnitTest :ink-gallery:lintRelease :ink-gallery:assembleRelease
```

Release signing is read from `local.properties` (`signing.gallery.storeFile`, `signing.gallery.storePassword`, `signing.gallery.keyAlias`, `signing.gallery.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Gallery is free software under the [GNU General Public License v3](LICENSE).

Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Gallery

Галерея фото и видео для Android-телефонов с e-ink экраном, сделана под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1), [Ink Clock](../ink-clock/README.md#ink-clock-1), [Ink Calculator](../ink-calculator/README.md#ink-calculator-1), [Ink Launcher](../ink-launcher/README.md#ink-launcher-1) и [Ink Weather](../ink-weather/README.md#ink-weather-1).

Всё, кроме самих фото и видео, чисто чёрное или белое, без сглаживания и анимаций. Ничего не прокручивается: фото листаются страницами.

- Все фото и видео на телефоне, новые сверху: квадратные миниатюры по три в ряд, страница за страницей. Видео отмечено значком воспроизведения и длительностью. Миниатюры следующей страницы загружаются заранее, поэтому при перелистывании они появляются сразу.
- Долгое нажатие на миниатюру включает выбор: нажатия добавляют или убирают снимки на любой странице, а панель вместо шапки делится всеми выбранными или удаляет их разом. «Отмена» или «назад» выключает выбор.
- Альбомы — это папки, в которых лежат фото и видео: список с названием и количеством, свежие альбомы выше.
- Фото открывается на весь экран. Нажатие на левую или правую треть экрана или свайп вбок листает к предыдущему или следующему фото той сетки, из которой его открыли; нажатие в середине показывает счётчик и кнопки.
- Двойное нажатие в середине увеличивает фото вдвое вокруг этой точки. В увеличенном виде фото двигается пальцем и перерисовывается, когда палец отпущен; двойное нажатие возвращает обычный вид.
- «Поделиться», «Сведения» (дата съёмки, разрешение, длительность видео, размер, файл и папка) и «Удалить». Удаление просит второе нажатие, затем система спрашивает ещё раз — если в настройках не разрешить Ink Gallery управлять медиафайлами.
- Видео из сетки сразу играет на весь экран; при листании фото видео показывается первым кадром с кнопкой воспроизведения. Нажатие показывает время, «Поделиться», «Сведения» и «Удалить», полосу прогресса для перемотки нажатием, −10 с, паузу и +10 с; панели остаются, пока их не скрыть нажатием, и появляются сами, когда видео закончилось. Пока видео играет, экран не гаснет, а при уходе из приложения видео встаёт на паузу до возвращения.
- С A9PatchingService видео играет в режиме SPEED; «назад» возвращает режим, из которого его открыли: CLEAR для фото, обычный для сетки.
- Другие приложения могут открывать в Ink Gallery картинки и видео; картинка показывается одна, видео сразу играет.
- Горизонтальные фото можно поворачивать на весь вытянутый экран — в настройках.
- С [A9PatchingService](https://github.com/AndreyGladkov/A9PatchingService) и включённым в нём «Allow Control via Broadcasts» открытие фото переводит панель в режим CLEAR, где видны 16 настоящих оттенков серого, а фото дизерится до них синим шумом, чтобы на плавных переходах не было полос. При возврате к сетке или уходе из приложения возвращается прежний режим; в сведениях о фото CLEAR остаётся. Переключение в CLEAR и дизеринг настраиваются отдельно: по умолчанию дизеринг следует за CLEAR, его можно оставить включённым, если CLEAR задан вручную в A9PatchingService, или выключить, чтобы вместо зерна были видны собственные ступени CLEAR между оттенками.
- Если разрешён доступ только к части фото и видео (Android 14 и новее) или к фото без видео, над сеткой об этом написано и есть кнопка, которая снова спрашивает доступ — там можно разрешить всё.
- Список обновляется каждый раз, когда приложение возвращается на экран.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов, списков, подписей вкладок и вибрации — общие с другими приложениями Ink.

Ink Gallery не пользуется интернетом. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkGallery-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Gallery ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `50:B8:5F:0A:96:2E:33:B4:92:DF:88:73:DA:C7:A8:BC:52:CA:B5:89:C1:42:D4:73:8D:F5:6C:6C:AA:42:4E:FA`.
