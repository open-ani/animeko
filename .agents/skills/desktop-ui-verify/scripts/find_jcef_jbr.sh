#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"

valid_jbr() {
  local home="$1"
  [[ -x "$home/bin/java" && -f "$home/jmods/jcef.jmod" && -f "$home/lib/libjcef.dylib" ]]
}

add_candidate() {
  local home="$1"
  [[ -n "$home" ]] || return 0
  [[ -d "$home" ]] || return 0
  candidates+=("$home")
}

declare -a candidates=()

add_candidate "${ANI_COMPOSE_JAVA_HOME:-}"

# This local JBR/JCEF build passed createRuntimeImage during skill research.
add_candidate "/Users/him188/Library/Java/JavaVirtualMachines/jbrsdk_jcef-21.0.4/Contents/Home"

add_candidate "${JAVA_HOME:-}"

shopt -s nullglob
for home in \
  "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
  /Library/Java/JavaVirtualMachines/*/Contents/Home \
  /Applications/Android\ Studio*.app/Contents/jbr/Contents/Home \
  /Applications/IntelliJ\ IDEA*.app/Contents/jbr/Contents/Home
do
  add_candidate "$home"
done

if [[ "$mode" == "--all" ]]; then
  for home in "${candidates[@]}"; do
    if valid_jbr "$home"; then
      printf "OK   %s\n" "$home"
    else
      printf "MISS %s\n" "$home"
    fi
  done
  exit 0
fi

for home in "${candidates[@]}"; do
  if valid_jbr "$home"; then
    echo "$home"
    exit 0
  fi
done

echo "No JBR/JCEF home found with bin/java, jmods/jcef.jmod, and lib/libjcef.dylib." >&2
echo "Set ANI_COMPOSE_JAVA_HOME to a JBR SDK with JCEF." >&2
exit 1
