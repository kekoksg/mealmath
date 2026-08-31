#!/usr/bin/env bash
# Sobe o MealMath completo e publica uma URL HTTPS pública via Cloudflare Tunnel.
#
#   ./subir.sh          # backend + SSR + túnel
#   ./subir.sh --local  # backend + SSR, sem expor na internet
#
# Ctrl+C derruba tudo.
#
# ENDEREÇO FIXO (necessário para instalar como app — ver docs/publicar.md)
# Com estas duas variáveis o script usa um túnel nomeado, de endereço permanente,
# em vez do efêmero que sorteia um domínio novo a cada execução:
#
#   MEALMATH_TUNEL=mealmath MEALMATH_HOST=app.seudominio.com ./subir.sh
#
# Sem elas, cai no túnel efêmero — que funciona, mas troca de URL e por isso
# invalida qualquer instalação anterior e deixa o service worker órfão.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API="$RAIZ/backend/mealmath-api"
FRONT="$RAIZ/frontend"
PORTA_SSR="${PORT:-4000}"

if [[ ! -f "$API/.env" ]]; then
  echo "Falta $API/.env — copie de .env.example e preencha." >&2
  exit 1
fi

# As credenciais vivem só aqui; nada de segredo em application.properties.
set -a
# shellcheck source=/dev/null
. "$API/.env"
set +a

pids=()
encerrar() {
  echo
  echo "Encerrando..."
  for pid in "${pids[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap encerrar EXIT INT TERM

echo "==> API Spring (:8082)"
# Perfil dev = carga de exemplo (maria@email.com / senha123), idempotente por e-mail.
(cd "$API" && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev) &
pids+=($!)

until curl -sf -o /dev/null "http://localhost:8082/auth/login" -X POST \
        -H 'Content-Type: application/json' -d '{}' 2>/dev/null \
      || curl -s -o /dev/null "http://localhost:8082/" 2>/dev/null; do
  sleep 2
done
echo "    API respondendo."

echo "==> Build de produção do front"
(cd "$FRONT" && npx ng build --configuration production >/dev/null)

echo "==> Servidor SSR (:$PORTA_SSR)"
# O SSR encaminha /api para a :8082, então o navegador fala com uma origem só.
(cd "$FRONT" && PORT="$PORTA_SSR" node dist/frontend/server/server.mjs) &
pids+=($!)

until curl -s -o /dev/null "http://localhost:$PORTA_SSR/"; do sleep 1; done
echo "    SSR respondendo em http://localhost:$PORTA_SSR"

if [[ "${1:-}" == "--local" ]]; then
  echo
  echo "Pronto (somente local): http://localhost:$PORTA_SSR"
  wait
fi

registro="$(mktemp)"

if [[ -n "${MEALMATH_TUNEL:-}" && -n "${MEALMATH_HOST:-}" ]]; then
  echo "==> Túnel nomeado ($MEALMATH_TUNEL -> $MEALMATH_HOST)"
  cloudflared tunnel --no-autoupdate run \
    --url "http://localhost:$PORTA_SSR" "$MEALMATH_TUNEL" >"$registro" 2>&1 &
  pids+=($!)

  # O túnel nomeado não anuncia URL no log — o endereço é o que já está no DNS.
  # Espera ele registrar a conexão com a borda antes de dizer que está pronto.
  until grep -qE "Registered tunnel connection|ERR " "$registro"; do sleep 2; done
  if ! grep -q "Registered tunnel connection" "$registro"; then
    echo "Túnel nomeado falhou. Log em $registro" >&2
    exit 1
  fi
  url="https://$MEALMATH_HOST"
else
  echo "==> Túnel efêmero (endereço novo a cada execução)"
  cloudflared tunnel --url "http://localhost:$PORTA_SSR" --no-autoupdate >"$registro" 2>&1 &
  pids+=($!)

  until grep -qE "trycloudflare\.com|ERR " "$registro"; do sleep 2; done
  url="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$registro" | head -1)"

  if [[ -z "$url" ]]; then
    echo "Túnel falhou. Log em $registro" >&2
    exit 1
  fi
fi

echo
echo "No ar: $url"
echo "Login de exemplo: maria@email.com / senha123"
if [[ -z "${MEALMATH_TUNEL:-}" ]]; then
  echo
  echo "Aviso: endereço temporário. Instalar como app só faz sentido com endereço"
  echo "fixo — veja docs/publicar.md."
fi
echo "Ctrl+C encerra."
wait
