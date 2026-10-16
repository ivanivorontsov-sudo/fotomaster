# Сторонние компоненты / Third-party notices

Компоненты, которые входят в собранное приложение. Полные тексты лицензий доступны по ссылкам; лицензия Apache 2.0 приложена в файле `licenses/Apache-2.0.txt`.

## Android Jetpack (AndroidX) / Material Components, © The Android Open Source Project / Google LLC
- Лицензия: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- Артефакты: `androidx.activity:activity-compose:1.9.3`, `androidx.compose.material3:material3`, `androidx.compose.material:material-icons-extended`, `androidx.compose.ui:ui`, `androidx.compose.ui:ui-graphics`, `androidx.compose:compose-bom:2024.10.01`, `androidx.exifinterface:exifinterface:1.3.7`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`, `androidx.webkit:webkit:1.12.1`

## Google ML Kit, © Google LLC
- Лицензия: ML Kit Terms of Service (проприетарная бесплатная лицензия Google) — https://developers.google.com/ml-kit/terms ; Google APIs Terms of Service — https://developers.google.com/terms
- Артефакты: `com.google.mlkit:face-detection:16.1.7`, `com.google.mlkit:segmentation-selfie:16.0.0-beta6`

## Kotlin / kotlinx, © JetBrains s.r.o. and Kotlin contributors
- Лицензия: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- Артефакты: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`

## JSON-java (org.json), Sean Leary and contributors
- Лицензия: Public Domain (версии 20231013 и новее) — https://github.com/stleary/JSON-java/blob/master/LICENSE
- Артефакты: `org.json:json:20240303`

## Транзитивные зависимости
Транзитивные зависимости перечисленных библиотек AndroidX/Kotlin распространяются по Apache License 2.0 (проверено по POM-файлам Google Maven).

## Только для тестов (в приложение не входят)
`androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-test-manifest`, `androidx.test:core:1.6.1`, `org.robolectric:robolectric:4.14.1` — JUnit 4: Eclipse Public License 1.0; Robolectric: MIT; остальные: Apache 2.0.


## Нейросетевые сервисы
Приложение само не содержит моделей Qwen, Алиса AI или ГигаЧат. По выбору пользователя оно открывает их официальные сайты (chat.qwen.ai, alice.yandex.ru, giga.chat) во встроенном браузере под учётной записью пользователя. Использование этих сервисов регулируется их собственными условиями. Qwen — товарный знак Alibaba Group; Алиса и Яндекс — товарные знаки ООО «Яндекс»; GigaChat/ГигаЧат и Сбер — товарные знаки ПАО Сбербанк. ФотоМастер не связан с этими компаниями и не одобрен ими.


## Шрифты, иконки, изображения, звуки
Иконки приложения нарисованы для проекта (векторные). Значки интерфейса — Material Icons (Apache 2.0), если используются. Сторонние изображения, звуки и логотипы чужих брендов в приложении не используются, если выше не указано иное.

## Политика конфиденциальности
См. `PRIVACY.md`; краткая версия — в приложении, кнопка «i» на главном экране.
