#!/usr/bin/env bash
# Runs the Java memory demo 20 times per collector and counts the runs
# in which a normal request was killed by the bad request's OutOfMemoryError.
set -euo pipefail
RUNS=${RUNS:-20}
OUT=$(mktemp -d)
javac -d "$OUT" Memory.java
for gc in G1 Z; do
  hit=0; died=0
  for _ in $(seq 1 "$RUNS"); do
    n=$(java -cp "$OUT" -DrunMs=3000 "-XX:+Use${gc}GC" -Xmx512m Memory 2>&1 | grep -c 'normal request . DIED\|Exception in thread' || true)
    died=$((died + n))
    if [ "$n" -gt 0 ]; then hit=$((hit + 1)); fi
  done
  echo "${gc}GC: a normal request was killed in $hit of $RUNS runs ($died of $((RUNS * 4)) normal requests)"
done
