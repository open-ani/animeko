#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
default_repo="$(cd "$script_dir/../../../.." && pwd)"
repo="${ANIMEKO_REPO:-$default_repo}"
api_server="${ANI_API_SERVER:-http://localhost:4394}"
jbr="${ANI_COMPOSE_JAVA_HOME:-$("$script_dir/find_jcef_jbr.sh")}"

cd "$repo"

echo "Repository: $repo"
echo "ANI_COMPOSE_JAVA_HOME=$jbr"
echo "ani.api.server=$api_server"

ANI_COMPOSE_JAVA_HOME="$jbr" ./gradlew :app:desktop:createDistributable \
  -Pani.api.server="$api_server" \
  "$@"
