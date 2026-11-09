#!/usr/bin/env bash
# Prove link-preview bots get PublicSeoController copy and browsers keep the SPA.
# Uses the nginx configs shipped in the web images. No remote host.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
html="$work/html"
mkdir -p "$html" "$work/client_body" "$work/proxy" "$work/fastcgi" "$work/uwsgi" "$work/scgi"

cat >"$html/index.html" <<'EOF'
<!doctype html>
<html lang="ru">
<head>
<title>Лига ИТМО</title>
<meta name="description" content="Турниры, таблица, календарь и матчи студенческой лиги ИТМО." />
</head>
<body><div id="app"></div></body>
</html>
EOF

python3 - "$work/preview.py" <<'PY'
import sys
path = sys.argv[1]
open(path, "w", encoding="utf-8").write(r'''
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

COPIES = {
    "/": "Лига ИТМО: бомбардиры и лучшие по передачам",
    "/table": "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам",
    "/statistics": "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам",
    "/calendar": "Лига ИТМО: календарь матчей",
    "/matches": "Лига ИТМО: календарь матчей",
    "/live": "Матчи, которые идут сейчас.",
    "/tournaments": "Лига ИТМО: турниры, вид спорта, сезон и формат",
    "/teams": "Лига ИТМО: команды",
    "/players": "Лига ИТМО: поиск игроков",
    "/privacy": "Как Лига ИТМО обрабатывает персональные данные.",
    "/terms": "Лига ИТМО: условия использования сайта",
    "/login": "Лига ИТМО: вход",
    "/register": "Лига ИТМО: регистрация",
    "/forgot-password": "Лига ИТМО: восстановление пароля",
    "/reset-password": "Лига ИТМО: новый пароль",
    "/my-team": "Лига ИТМО: моя команда",
    "/profile": "Лига ИТМО: профиль",
    "/admin": "Лига ИТМО: управление лигой",
    "/referee": "Лига ИТМО: матчи судьи",
    "/analyst": "Лига ИТМО: матчи аналитика",
    "/forbidden": "Лига ИТМО: недостаточно прав",
}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        header = self.headers.get("X-Preview-Path")
        query = parse_qs(parsed.query).get("path", [None])[0]
        raw = header if header else query
        if parsed.path != "/api/v1/link-preview" or not raw or raw not in COPIES:
            body = "<title>Такой страницы нет</title><p>Страница не найдена.</p>"
            status = 404
        else:
            text = COPIES[raw]
            body = (
                "<!doctype html><html><head><title>preview</title>"
                f'<meta name="description" content="{text}">'
                f'<meta property="og:description" content="{text}">'
                f"</head><body><p>{text}</p></body></html>"
            )
            status = 200
        data = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        return

if __name__ == "__main__":
    import os
    server = ThreadingHTTPServer(("127.0.0.1", int(os.environ["PREVIEW_PORT"])), Handler)
    server.serve_forever()
''')
PY

cleanup() {
  if [[ -n "${nginx_conf:-}" && -f "$nginx_conf" ]]; then
    nginx -c "$nginx_conf" -s quit >/dev/null 2>&1 || true
  fi
  if [[ -n "${py_pid:-}" ]]; then
    kill "$py_pid" >/dev/null 2>&1 || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT

pick_port() {
  python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
}

assert_contains() {
  local file=$1 needle=$2
  if ! grep -F -q -- "$needle" "$file"; then
    echo "missing [$needle] in:" >&2
    cat "$file" >&2
    exit 1
  fi
}

assert_absent() {
  local file=$1 needle=$2
  if grep -F -q -- "$needle" "$file"; then
    echo "unexpected [$needle] in:" >&2
    cat "$file" >&2
    exit 1
  fi
}

run_conf() {
  local src=$1
  local label=$2
  local backend front
  backend=$(pick_port)
  front=$(pick_port)
  PREVIEW_PORT=$backend python3 "$work/preview.py" &
  py_pid=$!
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if curl -sf -o /dev/null "http://127.0.0.1:$backend/api/v1/link-preview" ; then
      break
    fi
    # 404 is still a live server
    if curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$backend/api/v1/link-preview" | grep -q 404; then
      break
    fi
    sleep 0.1
  done

  local site="$work/site-$label.conf"
  python3 - "$src" "$site" "$front" "$backend" "$html" "$root/web/nginx-headers.conf" <<'PY'
import sys
src, dst, front, backend, html, headers = sys.argv[1:]
text = open(src, encoding="utf-8").read()
text = text.replace("listen 80;", f"listen 127.0.0.1:{front};")
text = text.replace("http://backend:8080", f"http://127.0.0.1:{backend}")
text = text.replace("http://backend:${SERVER_PORT}", f"http://127.0.0.1:{backend}")
text = text.replace("root /usr/share/nginx/html;", f"root {html};")
text = text.replace("include /etc/nginx/nginx-headers.conf;", f"include {headers};")
open(dst, "w", encoding="utf-8").write(text)
PY

  nginx_conf="$work/nginx-$label.conf"
  cat >"$nginx_conf" <<EOF
worker_processes 1;
error_log $work/error-$label.log warn;
pid $work/nginx-$label.pid;
events { worker_connections 64; }
http {
  access_log $work/access-$label.log;
  client_body_temp_path $work/client_body;
  proxy_temp_path $work/proxy;
  fastcgi_temp_path $work/fastcgi;
  uwsgi_temp_path $work/uwsgi;
  scgi_temp_path $work/scgi;
  include $site;
}
EOF

  if ! nginx -c "$nginx_conf"; then
    echo "nginx failed for $label" >&2
    cat "$work/error-$label.log" >&2 || true
    exit 1
  fi
  local base="http://127.0.0.1:$front"
  local body="$work/body-$label"

  curl -fsS -A 'TelegramBot (like TwitterBot)' "$base/" -o "$body"
  assert_contains "$body" "бомбардиры и лучшие по передачам"
  assert_contains "$body" 'property="og:description"'
  assert_absent "$body" "Страница не найдена"
  assert_absent "$body" 'id="app"'
  assert_absent "$body" "Турниры, таблица, календарь и матчи студенческой лиги ИТМО."

  curl -fsS -A 'facebookexternalhit/1.1' "$base/table" -o "$body"
  assert_contains "$body" "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам"
  assert_contains "$body" 'name="description"'
  assert_absent "$body" "Страница не найдена"
  assert_absent "$body" 'id="app"'

  curl -fsS -A 'TelegramBot (like TwitterBot)' "$base/table" -o "$body"
  assert_contains "$body" "Лига ИТМО: турнирная таблица, результаты, бомбардиры и лидеры по передачам"
  assert_absent "$body" "Страница не найдена"

  curl -fsS -A 'TelegramBot (like TwitterBot)' "$base/login" -o "$body"
  assert_contains "$body" "Лига ИТМО: вход"
  assert_contains "$body" 'property="og:description"'
  assert_absent "$body" "Страница не найдена"
  assert_absent "$body" 'id="app"'

  curl -fsS -A 'Twitterbot/1.0' "$base/register" -o "$body"
  assert_contains "$body" "Лига ИТМО: регистрация"
  assert_absent "$body" 'id="app"'

  for path in /calendar /live /tournaments /teams /players /privacy /terms \
      /forgot-password /reset-password /profile /my-team /admin /referee /analyst /forbidden; do
    curl -fsS -A 'TelegramBot (like TwitterBot)' "$base$path" -o "$body"
    assert_absent "$body" "Страница не найдена"
    assert_absent "$body" 'id="app"'
    assert_contains "$body" 'name="description"'
  done

  local missing="$work/missing-$label"
  local code
  code=$(curl -s -o "$missing" -w '%{http_code}' -A 'TelegramBot (like TwitterBot)' "$base/no-such-page")
  if [[ "$code" != "404" ]]; then
    echo "bot unknown path status $code" >&2
    cat "$missing" >&2
    exit 1
  fi
  assert_contains "$missing" "Страница не найдена"
  assert_absent "$missing" 'id="app"'

  curl -fsS -A 'Mozilla/5.0' "$base/" -o "$body"
  assert_contains "$body" 'id="app"'
  assert_contains "$body" "Турниры, таблица, календарь и матчи студенческой лиги ИТМО."
  assert_absent "$body" "лучшие по передачам"

  curl -fsS -A 'Mozilla/5.0' "$base/table" -o "$body"
  assert_contains "$body" 'id="app"'
  assert_contains "$body" "Турниры, таблица, календарь и матчи студенческой лиги ИТМО."
  assert_absent "$body" "лидеры по передачам"

  curl -fsS -A 'Mozilla/5.0' "$base/login" -o "$body"
  assert_contains "$body" 'id="app"'
  assert_absent "$body" "Лига ИТМО: вход"

  curl -fsS -A 'Mozilla/5.0' "$base/forbidden" -o "$body"
  assert_contains "$body" 'id="app"'
  assert_absent "$body" "Лига ИТМО: недостаточно прав"

  nginx -c "$nginx_conf" -s quit
  nginx_conf=
  kill "$py_pid" >/dev/null 2>&1 || true
  wait "$py_pid" || true
  py_pid=
  echo "ok $label"
}

run_conf "$root/web/nginx.conf" web
run_conf "$root/deploy/nginx.conf" deploy
echo "link preview nginx ok"
