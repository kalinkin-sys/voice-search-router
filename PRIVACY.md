# Политика конфиденциальности

Voice Search Router использует службу специальных возможностей Android только для
определения активного приложения, чтения результата системного голосового поиска
и передачи этого результата в поисковый интерфейс выбранного приложения.

## Что хранится на устройстве

- выбранное приложение по умолчанию;
- список приложений с включённой маршрутизацией;
- выбранный язык интерфейса;
- случайный идентификатор установки;
- ограниченный журнал технических событий Router.

Распознанные поисковые фразы в журнал не записываются.

## Что отправляется при нажатии «Отправить диагностику»

- модель и идентификаторы модели устройства;
- версия Android;
- версия Voice Search Router;
- версии выбранных приложений;
- состояние службы специальных возможностей;
- настройки маршрутизации;
- последние технические события Router.

## Что не собирается

- звук с микрофона;
- распознанные поисковые фразы;
- пароли;
- содержимое поисковых и других текстовых полей;
- контакты, фотографии и файлы пользователя.

Диагностика отправляется только по явному нажатию кнопки пользователем. Отчёт
передаётся по HTTPS через Cloudflare Worker и доставляется разработчику проекта.
Worker ограничивает размер и частоту отчётов.

Вопросы: [@KALINKIN](https://t.me/KALINKIN).

---

# Privacy policy (English)

Voice Search Router uses the Android accessibility service only to identify the
active app, read the result of the system voice search, and place that result in
the selected app's search interface.

## Data stored on the device

- the selected default app;
- the set of apps for which routing is enabled;
- the selected interface language;
- a random installation identifier;
- a limited log of technical Router events.

Recognized search queries are not written to the log.

## Data sent after selecting “Send diagnostics”

- device model identifiers;
- Android version;
- Voice Search Router version;
- versions of selected apps;
- accessibility-service state;
- routing preferences;
- recent technical Router events.

## Data that is not collected

- microphone audio;
- recognized search queries;
- passwords;
- the contents of search fields or other text fields;
- contacts, photos, or user files.

Diagnostics are sent only after an explicit button press. The report is delivered
over HTTPS through a Cloudflare Worker to the project developer. The Worker limits
report size and submission frequency.

Questions: [@KALINKIN](https://t.me/KALINKIN).
