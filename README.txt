Распакуй в корень проекта AgendaApp (где лежит папка app/), согласившись заменить файлы.

Новые:     HostPolicy.kt, HostPolicyTest.kt
Заменяются: MainActivity.kt, GradesRepository.kt, SnapshotCache.kt, AndroidManifest.xml,
            data_extraction_rules.xml, backup_rules.xml, RELEASING.md

Что изменилось:
 - "Сменить аккаунт" стирает ещё и историю оценок, кэш и данные страниц портала
   (раньше чужие оценки оставались и смешивались с новым аккаунтом);
 - убрана отправка оценок на собственный сервер (код удалён, а не выключен);
 - блокируются YouTube, внешние иконки и счётчики внутри окна портала;
 - из манифеста убраны разрешение на http-трафик и network_security_config
   (файл res/xml/network_security_config.xml можно удалить);
 - из облачного бэкапа исключены данные встроенного браузера.
