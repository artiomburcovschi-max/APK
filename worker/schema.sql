-- Одна строка на (день, версия). Только счётчики, ни идентификаторов, ни адресов.
CREATE TABLE IF NOT EXISTS daily (
  day     TEXT    NOT NULL,            -- «2026-11-19», UTC, ставит сервер
  version TEXT    NOT NULL,            -- версия приложения, например «0.8.2»
  d       INTEGER NOT NULL DEFAULT 0,  -- сколько устройств открыли приложение в этот день (DAU)
  w       INTEGER NOT NULL DEFAULT 0,  -- сколько из них впервые на этой неделе (сумма за неделю = WAU)
  m       INTEGER NOT NULL DEFAULT 0,  -- впервые в этом месяце (сумма за месяц = MAU)
  f       INTEGER NOT NULL DEFAULT 0,  -- впервые после включения статистики (новые участники)
  PRIMARY KEY (day, version)
);
