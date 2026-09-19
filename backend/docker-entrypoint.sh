#!/bin/sh
set -eu

urldecode() {
  # shellcheck disable=SC2059
  printf '%b' "$(printf '%s' "$1" | sed 's/+/ /g; s/%\([0-9A-Fa-f][0-9A-Fa-f]\)/\\x\1/g')"
}

# Render/Heroku: postgres://user:pass@host:port/db → JDBC + user/pass
if [ -n "${DATABASE_URL:-}" ]; then
  case "$DATABASE_URL" in
    postgres://*|postgresql://*)
      without_scheme="${DATABASE_URL#*://}"
      userinfo="${without_scheme%%@*}"
      hostpath="${without_scheme#*@}"
      DB_USER="$(urldecode "${userinfo%%:*}")"
      DB_PASS="$(urldecode "${userinfo#*:}")"
      hostport="${hostpath%%/*}"
      dbname_and_query="${hostpath#*/}"
      dbname="${dbname_and_query%%\?*}"
      query=""
      case "$dbname_and_query" in
        *\?*) query="${dbname_and_query#*?}" ;;
      esac
      case "$hostport" in
        *:*) host="${hostport%%:*}"; port="${hostport#*:}" ;;
        *) host="$hostport"; port="5432" ;;
      esac
      jdbc="jdbc:postgresql://${host}:${port}/${dbname}"
      if [ -n "$query" ]; then
        jdbc="${jdbc}?${query}"
      fi
      case "$jdbc" in
        *sslmode=*) ;;
        *\?*) jdbc="${jdbc}&sslmode=require" ;;
        *) jdbc="${jdbc}?sslmode=require" ;;
      esac
      export DATABASE_URL="$jdbc"
      export DATABASE_USERNAME="${DATABASE_USERNAME:-$DB_USER}"
      export DATABASE_PASSWORD="${DATABASE_PASSWORD:-$DB_PASS}"
      export SPRING_DATASOURCE_URL="$jdbc"
      export SPRING_DATASOURCE_USERNAME="${DATABASE_USERNAME}"
      export SPRING_DATASOURCE_PASSWORD="${DATABASE_PASSWORD}"
      ;;
  esac
fi

exec java ${JAVA_OPTS:-} -jar /app/app.jar
