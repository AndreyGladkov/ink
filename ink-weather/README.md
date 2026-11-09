# Ink Weather

A weather app with a home screen widget for Android phones with e-ink screens, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md), [Ink Clock](../ink-clock/README.md), [Ink Calculator](../ink-calculator/README.md) and [Ink Launcher](../ink-launcher/README.md). The forecast comes from [MET Norway](https://api.met.no), and cities are found with [Open-Meteo](https://open-meteo.com).

Every pixel is pure black or white, with no antialiasing and no animations. The whole forecast fits one screen, so nothing scrolls.

[Русская версия ниже](#ink-weather-1)

## Features

- Now: temperature, what it feels like, wind, pressure and precipitation, with a description of the weather.
- The next 24 hours in three-hour steps, and seven days with their lowest and highest temperature and how much rain or snow falls.
- Times are the city's own, not the phone's.
- Cities are found by name, without location access. Several cities are kept; the arrows at the bottom move between them, and the list sets their order.
- A widget for any home screen shows one city, chosen when the widget is placed. It is drawn by the app itself, so it stays black and white in any launcher, and shows more as it grows: the temperature, then the day's range and the city, then the next hours, then the next days. A tap opens the app on that city. In [Ink Launcher](../ink-launcher/README.md) its menu has Configure… to choose another city.
- Widgets are refreshed once an hour while the phone is online, and redrawn only when what they show has changed. The app refreshes the city on screen when its forecast is older than half an hour.
- Without a connection the last forecast stays, marked with the time it was fetched once it is three hours old.
- Temperature units follow the system's regional settings; pressure is in millimetres of mercury in Russian and in hectopascals otherwise.
- Settings for text size, font weight, button corners, colors, lists and vibration, shared with the other Ink apps.

Ink Weather talks only to MET Norway and Open-Meteo. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkWeather-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Weather `, because the repository also publishes other apps.

## Building

```
./gradlew :ink-weather:testDebugUnitTest :ink-weather:lintRelease :ink-weather:assembleRelease
```

Release signing is read from `local.properties` (`signing.weather.storeFile`, `signing.weather.storePassword`, `signing.weather.keyAlias`, `signing.weather.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Weather is free software under the [GNU General Public License v3](LICENSE).

Forecast: [MET Norway](https://api.met.no/doc/License), under CC BY 4.0. City search: [Open-Meteo.com](https://open-meteo.com/en/license), under CC BY 4.0. Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Weather

Погода с виджетом для Android-телефонов с e-ink экраном, сделана под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1), [Ink Clock](../ink-clock/README.md#ink-clock-1), [Ink Calculator](../ink-calculator/README.md#ink-calculator-1) и [Ink Launcher](../ink-launcher/README.md#ink-launcher-1). Прогноз берётся у [MET Norway](https://api.met.no), города ищутся через [Open-Meteo](https://open-meteo.com).

Каждый пиксель чисто чёрный или белый, без сглаживания и анимаций. Весь прогноз помещается на один экран, прокручивать ничего не нужно.

- Сейчас: температура, как она ощущается, ветер, давление и осадки, с описанием погоды.
- Ближайшие сутки с шагом в три часа и семь дней с минимальной и максимальной температурой и количеством осадков.
- Время — местное для города, а не телефона.
- Города ищутся по названию, без доступа к геолокации. Городов может быть несколько: между ними переключают стрелки внизу, порядок задаётся в списке.
- Виджет для любого домашнего экрана показывает один город, его выбирают, когда ставят виджет. Виджет рисует само приложение, поэтому он чёрно-белый в любом лаунчере и с ростом показывает больше: температуру, затем разброс за день и город, затем ближайшие часы, затем следующие дни. Нажатие открывает приложение на этом городе. В [Ink Launcher](../ink-launcher/README.md#ink-launcher-1) в меню виджета есть «Настроить…», чтобы выбрать другой город.
- Виджеты обновляются раз в час, когда есть сеть, и перерисовываются, только если изменилось то, что они показывают. Приложение обновляет город на экране, если прогнозу больше получаса.
- Без сети остаётся последний прогноз; если ему больше трёх часов, рядом видно, когда он получен.
- Единицы температуры берутся из региональных настроек системы; давление на русском — в миллиметрах ртутного столба, на других языках — в гектопаскалях.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов, списков и вибрации — общие с другими приложениями Ink.

Ink Weather обращается только к MET Norway и Open-Meteo. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkWeather-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Weather ` — в репозитории публикуются и другие приложения.
