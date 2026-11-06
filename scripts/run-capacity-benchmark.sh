#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SCENARIO="${1:-}"
QUESTION_COUNT="${2:-100}"
WORKER_COUNT="${3:-2}"
WARMUP_QUESTION_COUNT="${BENCHMARK_WARMUP_QUESTION_COUNT:-10}"
SETTLE_SECONDS="${BENCHMARK_SETTLE_SECONDS:-60}"
TIMEOUT_MINUTES="${BENCHMARK_TIMEOUT_MINUTES:-30}"

if [[ ! "$SCENARIO" =~ ^[ABC]$ ]]; then
  echo "사용법: $0 <A|B|C> [문제 수] [워커 수]" >&2
  exit 1
fi

for value in "$QUESTION_COUNT" "$WORKER_COUNT" "$WARMUP_QUESTION_COUNT" "$SETTLE_SECONDS" "$TIMEOUT_MINUTES"; do
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "문제 수, 워커 수, 대기 시간은 양의 정수여야 합니다: $value" >&2
    exit 1
  fi
done

if [[ ! -f .env ]]; then
  echo ".env 파일이 필요합니다. 실제 S3 설정과 로컬 인증 정보를 먼저 확인하세요." >&2
  exit 1
fi

command -v docker >/dev/null || { echo "Docker가 필요합니다." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl이 필요합니다." >&2; exit 1; }

if [[ -z "${DOCKER_SOCKET_PATH:-}" ]]; then
  if [[ -S "$HOME/.docker/run/docker.sock" ]]; then
    export DOCKER_SOCKET_PATH="$HOME/.docker/run/docker.sock"
  else
    export DOCKER_SOCKET_PATH="/var/run/docker.sock"
  fi
fi

export BENCHMARK_SCENARIO="$SCENARIO"
export BENCHMARK_WORKER_CONCURRENCY="$WORKER_COUNT"
export BENCHMARK_BACKEND_MEMORY="1536m"

case "$SCENARIO" in
  A)
    export BENCHMARK_BACKEND_CPUSET="0,1"
    export BENCHMARK_DATA_CPUSET="2,3"
    export BENCHMARK_MYSQL_MEMORY="2048m"
    export BENCHMARK_REDIS_MEMORY="512m"
    export BENCHMARK_PROMETHEUS_MEMORY="640m"
    export BENCHMARK_MYSQL_EXPORTER_MEMORY="128m"
    export BENCHMARK_REDIS_EXPORTER_MEMORY="128m"
    export BENCHMARK_CADVISOR_MEMORY="256m"
    REQUIRED_DOCKER_CPUS=4
    DESCRIPTION="backend=t4g.small, data=t4g.medium"
    ;;
  B)
    export BENCHMARK_BACKEND_CPUSET="0,1"
    export BENCHMARK_DATA_CPUSET="2,3"
    export BENCHMARK_MYSQL_MEMORY="896m"
    export BENCHMARK_REDIS_MEMORY="192m"
    export BENCHMARK_PROMETHEUS_MEMORY="256m"
    export BENCHMARK_MYSQL_EXPORTER_MEMORY="64m"
    export BENCHMARK_REDIS_EXPORTER_MEMORY="64m"
    export BENCHMARK_CADVISOR_MEMORY="128m"
    REQUIRED_DOCKER_CPUS=4
    DESCRIPTION="backend=t4g.small, data=t4g.small"
    ;;
  C)
    export BENCHMARK_BACKEND_CPUSET="0,1"
    export BENCHMARK_DATA_CPUSET="0,1"
    export BENCHMARK_MYSQL_MEMORY="1024m"
    export BENCHMARK_REDIS_MEMORY="256m"
    export BENCHMARK_PROMETHEUS_MEMORY="384m"
    export BENCHMARK_MYSQL_EXPORTER_MEMORY="64m"
    export BENCHMARK_REDIS_EXPORTER_MEMORY="64m"
    export BENCHMARK_CADVISOR_MEMORY="128m"
    REQUIRED_DOCKER_CPUS=2
    DESCRIPTION="backend+data=t4g.medium"
    ;;
esac

DOCKER_CPUS="$(docker info --format '{{.NCPU}}')"
if (( DOCKER_CPUS < REQUIRED_DOCKER_CPUS )); then
  echo "Docker에 최소 ${REQUIRED_DOCKER_CPUS}개 CPU가 필요합니다. 현재: ${DOCKER_CPUS}" >&2
  exit 1
fi

COMPOSE=(docker compose -f compose.yaml -f compose.benchmark.yaml)
SERVICES=(mysql redis mysql-exporter redis-exporter cadvisor prometheus backend-benchmark)
STATS_PID=""
BENCHMARK_STARTED=0

stop_stats() {
  if [[ -n "${STATS_PID:-}" ]] && kill -0 "$STATS_PID" 2>/dev/null; then
    kill "$STATS_PID" 2>/dev/null || true
    wait "$STATS_PID" 2>/dev/null || true
  fi
  STATS_PID=""
}

stop_benchmark_services() {
  if (( BENCHMARK_STARTED == 0 )) || [[ "${BENCHMARK_KEEP_RUNNING:-0}" == "1" ]]; then
    return
  fi
  echo "벤치마크 컨테이너를 중지합니다. 결과 파일과 Docker 볼륨은 유지됩니다."
  "${COMPOSE[@]}" stop "${SERVICES[@]}" >/dev/null 2>&1 || true
}

cleanup_benchmark() {
  local exit_code=$?
  trap - EXIT INT TERM
  stop_stats
  stop_benchmark_services
  exit "$exit_code"
}

echo "시나리오 $SCENARIO: $DESCRIPTION"
echo "측정 문제: ${QUESTION_COUNT}, 워커: ${WORKER_COUNT}, 준비 문제: ${WARMUP_QUESTION_COUNT}"
echo "기존 NOMAT Compose 컨테이너는 재생성하지만 DB와 Redis 볼륨은 삭제하지 않습니다."

if [[ "${BENCHMARK_ASSUME_YES:-0}" != "1" ]]; then
  read -r -p "계속할까요? [y/N] " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    exit 0
  fi
fi

docker compose --profile monitoring stop grafana >/dev/null 2>&1 || true
"${COMPOSE[@]}" down --remove-orphans

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "8080 포트를 다른 프로세스가 사용 중입니다. 로컬 백엔드를 종료한 뒤 다시 실행하세요." >&2
  exit 1
fi

BENCHMARK_STARTED=1
trap cleanup_benchmark EXIT
trap 'exit 130' INT TERM

"${COMPOSE[@]}" up -d --build --force-recreate \
  mysql redis mysql-exporter redis-exporter cadvisor prometheus backend-benchmark

echo "백엔드가 준비될 때까지 기다립니다."
deadline=$((SECONDS + 240))
until curl --fail --silent http://127.0.0.1:9091/actuator/health >/dev/null; do
  backend_status="$(docker inspect --format '{{.State.Status}}' nomat-backend-benchmark 2>/dev/null || true)"
  if [[ "$backend_status" == "exited" || "$backend_status" == "dead" ]]; then
    echo "백엔드 컨테이너가 시작 중 종료됐습니다." >&2
    "${COMPOSE[@]}" logs --tail=200 backend-benchmark >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    echo "백엔드가 제한 시간 안에 준비되지 않았습니다." >&2
    "${COMPOSE[@]}" logs --tail=200 backend-benchmark >&2
    exit 1
  fi
  sleep 5
done

echo "백엔드 안정화를 위해 ${SETTLE_SECONDS}초 기다립니다."
sleep "$SETTLE_SECONDS"

RUN_ID="$(date '+%Y%m%d-%H%M%S')-${SCENARIO}-q${QUESTION_COUNT}-w${WORKER_COUNT}"
RESULT_DIR="$ROOT_DIR/benchmark-results/$RUN_ID"
mkdir -p "$RESULT_DIR"

cat > "$RESULT_DIR/metadata.txt" <<EOF
run_id=$RUN_ID
scenario=$SCENARIO
description=$DESCRIPTION
questions=$QUESTION_COUNT
workers=$WORKER_COUNT
warmup_questions=$WARMUP_QUESTION_COUNT
docker_cpus=$DOCKER_CPUS
host_arch=$(uname -m)
backend_cpuset=$BENCHMARK_BACKEND_CPUSET
data_cpuset=$BENCHMARK_DATA_CPUSET
backend_memory=$BENCHMARK_BACKEND_MEMORY
mysql_memory=$BENCHMARK_MYSQL_MEMORY
redis_memory=$BENCHMARK_REDIS_MEMORY
prometheus_memory=$BENCHMARK_PROMETHEUS_MEMORY
EOF

echo "${WARMUP_QUESTION_COUNT}문제 준비 실행을 시작합니다."
AUDIO_BENCHMARK_QUESTION_COUNT="$WARMUP_QUESTION_COUNT" \
AUDIO_BENCHMARK_MINIMUM_SUCCESS_COUNT="$WARMUP_QUESTION_COUNT" \
AUDIO_BENCHMARK_COMPLETION_TIMEOUT_MINUTES="$TIMEOUT_MINUTES" \
AUDIO_BENCHMARK_LABEL="${RUN_ID}-warmup" \
./gradlew cleanTest test \
  --tests com.dogdog.nomat.AudioProcessingRealBenchmarkTest \
  -PincludeAudioRealBenchmark \
  --no-daemon | tee "$RESULT_DIR/warmup.log"

container_ids() {
  "${COMPOSE[@]}" ps -q "${SERVICES[@]}"
}

collect_stats() {
  echo "timestamp,container,cpu_percent,memory_usage,memory_percent,net_io,block_io,pids"
  while true; do
    timestamp="$(date -Iseconds)"
    ids="$(container_ids | tr '\n' ' ')"
    if [[ -n "$ids" ]]; then
      # shellcheck disable=SC2086
      docker stats --no-stream \
        --format "$timestamp,{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}" \
        $ids || true
    fi
    sleep 5
  done
}

collect_stats > "$RESULT_DIR/container-stats.csv" &
STATS_PID=$!

echo "${QUESTION_COUNT}문제 측정을 시작합니다."
set +e
AUDIO_BENCHMARK_QUESTION_COUNT="$QUESTION_COUNT" \
AUDIO_BENCHMARK_MINIMUM_SUCCESS_COUNT="$QUESTION_COUNT" \
AUDIO_BENCHMARK_COMPLETION_TIMEOUT_MINUTES="$TIMEOUT_MINUTES" \
AUDIO_BENCHMARK_LABEL="$RUN_ID" \
./gradlew cleanTest test \
  --tests com.dogdog.nomat.AudioProcessingRealBenchmarkTest \
  -PincludeAudioRealBenchmark \
  --no-daemon | tee "$RESULT_DIR/benchmark.log"
TEST_EXIT_CODE=${PIPESTATUS[0]}
set -e

stop_stats

{
  echo "container,restart_count,oom_killed,status"
  while IFS= read -r id; do
    docker inspect --format '{{.Name}},{{.RestartCount}},{{.State.OOMKilled}},{{.State.Status}}' "$id" \
      | sed 's#^/##'
  done < <(container_ids)
} > "$RESULT_DIR/container-outcomes.csv"

"${COMPOSE[@]}" logs --no-color --since 30m backend-benchmark > "$RESULT_DIR/backend.log" 2>&1 || true

BENCHMARK_RESULT_LINE="$(grep 'AUDIO_REAL_BENCHMARK label=' "$RESULT_DIR/benchmark.log" | tail -n 1 || true)"
if [[ -z "$BENCHMARK_RESULT_LINE" ]]; then
  TEST_EXIT_CODE=1
fi

{
  echo "# Capacity benchmark $RUN_ID"
  echo
  echo "- Scenario: \`$SCENARIO\` ($DESCRIPTION)"
  echo "- Questions: $QUESTION_COUNT"
  echo "- Workers: $WORKER_COUNT"
  echo "- Test exit code: $TEST_EXIT_CODE"
  echo "- Result: $([[ $TEST_EXIT_CODE -eq 0 ]] && echo PASS || echo FAIL)"
  echo
  echo "## Application result"
  echo
  if [[ -n "$BENCHMARK_RESULT_LINE" ]]; then
    echo '```text'
    echo "$BENCHMARK_RESULT_LINE"
    echo '```'
  else
    echo "The benchmark result line was not found. Check \`benchmark.log\`."
  fi
  echo
  echo "## Peak container usage"
  echo
  echo "| Container | Peak CPU | Peak memory |"
  echo "|---|---:|---:|"
  awk -F',' 'NR > 1 {
      cpu=$3; mem=$5;
      gsub(/%/, "", cpu); gsub(/%/, "", mem);
      if ((cpu + 0) > maxCpu[$2]) maxCpu[$2] = cpu + 0;
      if ((mem + 0) > maxMem[$2]) maxMem[$2] = mem + 0;
    }
    END {
      for (name in maxCpu) {
        printf "| %s | %.2f%% | %.2f%% |\n", name, maxCpu[name], maxMem[name];
      }
    }' "$RESULT_DIR/container-stats.csv" | sort
  echo
  echo "## Container outcomes"
  echo
  echo "| Container | Restarts | OOM killed | Status |"
  echo "|---|---:|---|---|"
  tail -n +2 "$RESULT_DIR/container-outcomes.csv" \
    | awk -F',' '{printf "| %s | %s | %s | %s |\n", $1, $2, $3, $4}'
  echo
  echo "Raw files: \`container-stats.csv\`, \`container-outcomes.csv\`, \`benchmark.log\`, \`backend.log\`."
} > "$RESULT_DIR/summary.md"

ln -sfn "$RUN_ID" "$ROOT_DIR/benchmark-results/latest"

INDEX_FILE="$ROOT_DIR/benchmark-results/runs.csv"
if [[ ! -f "$INDEX_FILE" ]]; then
  echo "run_id,scenario,questions,workers,test_exit_code,result_dir" > "$INDEX_FILE"
fi
echo "$RUN_ID,$SCENARIO,$QUESTION_COUNT,$WORKER_COUNT,$TEST_EXIT_CODE,$RESULT_DIR" >> "$INDEX_FILE"

echo
echo "결과 위치: $RESULT_DIR"
echo "요약 파일: $RESULT_DIR/summary.md"
echo "Grafana 확인이 필요하면 다음 명령으로 실행하세요."
echo "  docker compose --profile monitoring up -d --no-deps prometheus grafana"
echo "  http://127.0.0.1:3001"

exit "$TEST_EXIT_CODE"
