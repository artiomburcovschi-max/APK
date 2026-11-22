#!/usr/bin/env bash
# Проверка Worker'а «как есть», на локальной копии:
#   1) npx wrangler d1 execute agenda-stats --local --file=schema.sql
#   2) в .dev.vars одна строка: STATS_TOKEN=test-secret-123
#   3) npx wrangler dev --local --port 8799   (в другом окне)
#   4) bash test/e2e.sh
B=${1:-http://127.0.0.1:8799}
TOKEN=${2:-test-secret-123}
fail=0
check() { # имя ожидаемый-код фактический-код
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: ждали $2, получили $3"; fail=1; fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

check "обычный сигнал"            204 "$(code -X POST $B/ping -d '{"v":"0.8.2","w":1,"m":1,"f":1}')"
check "версия «?»"                204 "$(code -X POST $B/ping -d '{"v":"?","w":0,"m":0,"f":0}')"
check "битый JSON"                400 "$(code -X POST $B/ping -d 'не json')"
check "массив вместо объекта"     400 "$(code -X POST $B/ping -d '[1]')"
check "версия с пробелом"         400 "$(code -X POST $B/ping -d '{"v":"a b","w":0,"m":0,"f":0}')"
check "версия с кавычкой и SQL"   400 "$(code -X POST $B/ping -d "{\"v\":\"x';--\",\"w\":0,\"m\":0,\"f\":0}")"
check "отметка 2 вместо 0/1"      400 "$(code -X POST $B/ping -d '{"v":"1","w":2,"m":0,"f":0}')"
check "отметка строкой"           400 "$(code -X POST $B/ping -d '{"v":"1","w":"1","m":0,"f":0}')"
check "нет поля"                  400 "$(code -X POST $B/ping -d '{"v":"1","w":1,"m":0}')"
check "слишком большое тело"      413 "$(code -X POST $B/ping -d "$(python3 -c 'print("{\"v\":\"" + "a"*600 + "\"}")')")"
check "GET на /ping"              405 "$(code $B/ping)"
check "POST на /stats"            405 "$(code -X POST $B/stats)"
check "сводка без токена"         401 "$(code $B/stats)"
check "сводка с чужим токеном"    401 "$(code -H 'Authorization: Bearer wrong' $B/stats)"
check "сводка с верным токеном"   200 "$(code -H "Authorization: Bearer $TOKEN" $B/stats)"
check "неизвестный адрес"         404 "$(code $B/nope)"
[ $fail = 0 ] && echo "ВСЁ В ПОРЯДКЕ" || { echo "ЕСТЬ ОШИБКИ"; exit 1; }
