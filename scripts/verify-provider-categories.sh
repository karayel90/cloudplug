#!/usr/bin/env bash

# Проверяет, что tvTypes в build.gradle.kts содержит enum-типы CloudStream.
# Этот контракт защищает фильтр категорий в Settings → Extensions.
set -euo pipefail

declare -A expected=(
    [TabiiProvider]='Live'
    [TrLiveProvider]='Live'
)

failures=0

for module in "${!expected[@]}"; do
    file="$module/build.gradle.kts"
    if [[ ! -f "$file" ]]; then
        printf 'ПОМИЛКА: %s: отсутствует build.gradle.kts\n' "$module"
        failures=$((failures + 1))
        continue
    fi

    block=$(awk '
        /tvTypes[[:space:]]*=[[:space:]]*listOf/ {
            print
            if ($0 ~ /\)/) exit
            reading = 1
            next
        }
        reading {
            print
            if ($0 ~ /\)/) exit
        }
    ' "$file")
    actual=$(printf '%s\n' "$block" | (grep -o '"[^"]*"' || true) | tr -d '"' | sort -u | tr '\n' ' ' | sed 's/[[:space:]]*$//')
    want=$(printf '%s\n' "${expected[$module]}" | tr ' ' '\n' | sort -u | tr '\n' ' ' | sed 's/[[:space:]]*$//')

    if [[ "$actual" != "$want" ]]; then
        printf 'ПОМИЛКА: %s: ожидалось [%s], получено [%s]\n' "$module" "$want" "$actual"
        failures=$((failures + 1))
    fi
done

if (( failures > 0 )); then
    printf '\nНайдено несоответствий категорий: %d\n' "$failures"
    exit 1
fi

printf 'Категории всех %d плагинов и провайдеров соответствуют контракту.\n' "${#expected[@]}"
