# Ink Calculator

A calculator for Android phones with e-ink screens, built for the Hisense A9 and made to look and behave like [Ink Phone](../ink-phone/README.md), [Ink Messages](../ink-messages/README.md) and [Ink Clock](../ink-clock/README.md).

Every pixel is pure black or white, with no antialiasing and no animations. Only the expression and its result change while you type.

[Русская версия ниже](#ink-calculator-1)

## Features

- Multiplication and division before addition and subtraction; decimals are exact, so 0.1 + 0.2 is 0.3.
- Percent as on a desk calculator: 200 + 10% is 220, 200 × 10% is 20.
- The result of the expression shows below it while you type; = moves it to the tape above.
- The tape keeps the last calculations; a tap puts a result into the expression.
- A result is shown with 15 digits but carried on exactly, so 1 ÷ 3 = × 3 = gives 1.
- A long press on the expression copies its value.
- The expression and the tape survive leaving the app.
- Settings for text size, font weight, button corners, colors and vibration, shared with the other Ink apps.

Ink Calculator has no internet permission. See [PRIVACY.md](PRIVACY.md).

## Installation

Requires Android 11 or newer.

- **GitHub:** download `InkCalculator-<version>-release.apk` from [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** add `https://github.com/AndreyGladkov/ink` as an app and set "Filter release titles by regular expression" to `^Ink Calculator `, because the repository also publishes other apps.

Signing certificate SHA-256: `05:49:E3:FD:56:A5:B7:E5:7D:C2:E3:B0:D9:B4:1F:68:39:22:C1:D0:2F:83:70:02:DF:0E:EA:0C:F0:47:92:BA`.

## Building

```
./gradlew :ink-calculator:testDebugUnitTest :ink-calculator:lintRelease :ink-calculator:assembleRelease
```

Release signing is read from `local.properties` (`signing.calculator.storeFile`, `signing.calculator.storePassword`, `signing.calculator.keyAlias`, `signing.calculator.keyPassword`); without these keys the release APK is built unsigned.

## License

Ink Calculator is free software under the [GNU General Public License v3](LICENSE).

Bundled components: Noto Emoji font (SIL Open Font License 1.1) and Material icons (Apache License 2.0). Their license texts are in `ink-ui/src/main/assets/licenses` and in the app under Settings → About.

---

## Ink Calculator

Калькулятор для Android-телефонов с e-ink экраном, сделан под Hisense A9 и выглядит и ведёт себя как [Ink Phone](../ink-phone/README.md#ink-phone-1), [Ink Messages](../ink-messages/README.md#ink-messages-1) и [Ink Clock](../ink-clock/README.md#ink-clock-1).

Каждый пиксель чисто чёрный или белый, без сглаживания и анимаций. Пока вы набираете, меняются только выражение и его результат.

- Умножение и деление выполняются раньше сложения и вычитания; дроби считаются точно, 0,1 + 0,2 = 0,3.
- Процент как на настольном калькуляторе: 200 + 10% = 220, 200 × 10% = 20.
- Результат выражения виден под ним прямо при наборе; «=» переносит вычисление на ленту выше.
- На ленте последние вычисления; нажатие подставляет результат в выражение.
- Результат показан с 15 знаками, но в следующее действие идёт точным, поэтому 1 ÷ 3 = × 3 = даёт 1.
- Долгое нажатие на выражение копирует его значение.
- Выражение и лента сохраняются, если выйти из приложения.
- Настройки размера текста, толщины шрифта, скругления кнопок, цветов и вибрации — общие с другими приложениями Ink.

У Ink Calculator нет разрешения на доступ в интернет. Подробнее — в [PRIVACY.md](PRIVACY.md).

### Установка

Нужен Android 11 или новее.

- **GitHub:** скачайте `InkCalculator-<версия>-release.apk` в [Releases](https://github.com/AndreyGladkov/ink/releases).
- **[Obtainium](https://github.com/ImranR98/Obtainium):** добавьте `https://github.com/AndreyGladkov/ink` и в поле «Filter release titles by regular expression» укажите `^Ink Calculator ` — в репозитории публикуются и другие приложения.

SHA-256 сертификата подписи: `05:49:E3:FD:56:A5:B7:E5:7D:C2:E3:B0:D9:B4:1F:68:39:22:C1:D0:2F:83:70:02:DF:0E:EA:0C:F0:47:92:BA`.
