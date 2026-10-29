#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SCENARIO="${1:-}"
USERS="${2:-100}"
ROOM_SIZE="${3:-10}"
INTERVAL_SECONDS="${4:-2}"
DURATION_MINUTES="${5:-10}"
WARMUP_SECONDS="${CHAT_BENCHMARK_WARMUP_SECONDS:-120}"
WORKER_COUNT="${CHAT_BENCHMARK_WORKER_COUNT:-2}"

if [[ ! "$SCENARIO" =~ ^[ABC]$ ]]; then
  echo "사용법: $0 <A|B|C> [사용자 수] [방 인원] [메시지 간격(초)] [측정 시간(분)]" >&2
  exit 1
fi

for value in "$USERS" "$ROOM_SIZE" "$INTERVAL_SECONDS" "$DURATION_MINUTES" "$WARMUP_SECONDS"; do
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "숫자 인자는 양의 정수여야 합니다: $value" >&2
    exit 1
  fi
done

if (( ROOM_SIZE < 2 || ROOM_SIZE > 12 )); then
  echo "방 인원은 2명 이상 12명 이하여야 합니다." >&2
  exit 1
fi
if (( USERS % ROOM_SIZE != 0 )); then
  echo "사용자 수는 방 인원으로 나누어떨어져야 합니다." >&2
  exit 1
fi
if [[ ! -f .env ]]; then
  echo ".env 파일이 필요합니다." >&2
  exit 1
fi

for command in docker curl node lsof; do
  command -v "$command" >/dev/null || { echo "$command 명령이 필요합니다." >&2; exit 1; }
done

NODE_MAJOR="$(node -p 'Number(process.versions.node.split(".")[0])')"
if (( NODE_MAJOR < 22 )); then
  echo "내장 WebSocket을 사용할 수 있는 Node.js 22 이상이 필요합니다. 현재: $(node --version)" >&2
  exit 1
fi

node scripts/room-chat-benchmark.mjs \
  --self-check \
  --users 2 \
  --room-size 2 \
  --result-dir /tmp/nomat-chat-benchmark-self-check

if [[ -z "${DOCKER_SOCKET_PATH:-}" ]]; then
  if [[ -S "$HOME/.docker/run/docker.sock" ]]; then
    export DOCKER_SOCKET_PATH="$HOME/.docker/run/docker.sock"
  else
    export DOCKER_SOCKET_PATH="/var/run/docker.sock"
  fi
fi

export BENCHMARK_SCENARIO="$SCENARIO"
export BENCHMARK_WORKER_CONCURRENCY="$WORKER_COUNT"
export BENCHMARK_AUDIO_PROCESSING_ENABLED="false"
export BENCHMARK_ROOM_GAME_TIMER_INITIAL_DELAY_MS="1000"
export BENCHMARK_REDIS_DATABASE="15"
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
INFRA_SERVICES=(mysql redis mysql-exporter redis-exporter cadvisor prometheus)
STATS_PID=""
BENCHMARK_STARTED=0
ROOM_COUNT=$((USERS / ROOM_SIZE))
DURATION_SECONDS=$((DURATION_MINUTES * 60))
INTERVAL_MS=$((INTERVAL_SECONDS * 1000))
RUN_ID="$(date '+%Y%m%d-%H%M%S')-${SCENARIO}-chat-u${USERS}-r${ROOM_SIZE}-i${INTERVAL_SECONDS}s"
RESULT_DIR="$ROOT_DIR/benchmark-results/$RUN_ID"

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
echo "사용자 ${USERS}명, 방 ${ROOM_COUNT}개, 방당 ${ROOM_SIZE}명"
echo "방의 약 90%는 게임을 시작하고 약 10%는 대기 상태로 유지합니다."
echo "사용자당 ${INTERVAL_SECONDS}초에 채팅 1개, 준비 ${WARMUP_SECONDS}초, 측정 ${DURATION_MINUTES}분"
echo "오디오 처리는 끈 상태로 채팅 부하만 측정합니다."
echo "기존 Compose 컨테이너는 재생성하지만 DB와 Redis 볼륨은 삭제하지 않습니다."

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

"${COMPOSE[@]}" up -d --build --force-recreate "${INFRA_SERVICES[@]}"

echo "채팅 벤치마크 전용 Redis DB를 초기화합니다."
redis_deadline=$((SECONDS + 120))
until docker exec nomat-redis redis-cli -n "$BENCHMARK_REDIS_DATABASE" ping 2>/dev/null | grep -q PONG; do
  if (( SECONDS >= redis_deadline )); then
    echo "Redis가 제한 시간 안에 준비되지 않았습니다." >&2
    exit 1
  fi
  sleep 2
done
docker exec nomat-redis redis-cli -n "$BENCHMARK_REDIS_DATABASE" flushdb >/dev/null

"${COMPOSE[@]}" up -d --build --force-recreate --no-deps backend-benchmark

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

mkdir -p "$RESULT_DIR"
cat > "$RESULT_DIR/metadata.txt" <<EOF
run_id=$RUN_ID
scenario=$SCENARIO
description=$DESCRIPTION
map_id=auto
users=$USERS
rooms=$ROOM_COUNT
room_size=$ROOM_SIZE
interval_seconds=$INTERVAL_SECONDS
warmup_seconds=$WARMUP_SECONDS
duration_minutes=$DURATION_MINUTES
docker_cpus=$DOCKER_CPUS
host_arch=$(uname -m)
backend_memory=$BENCHMARK_BACKEND_MEMORY
mysql_memory=$BENCHMARK_MYSQL_MEMORY
redis_memory=$BENCHMARK_REDIS_MEMORY
EOF

echo "실제 WebSocket 연결 경로를 게스트 2명으로 먼저 확인합니다."
set +e
node scripts/room-chat-benchmark.mjs \
  --users 2 \
  --room-size 2 \
  --interval-ms 500 \
  --warmup-seconds 1 \
  --duration-seconds 1 \
  --result-dir "$RESULT_DIR/smoke" | tee "$RESULT_DIR/smoke.log"
SMOKE_EXIT_CODE=${PIPESTATUS[0]}
set -e

if (( SMOKE_EXIT_CODE != 0 )); then
  {
    echo "# Room chat benchmark $RUN_ID"
    echo
    echo "- Result: PRECHECK FAILED"
    echo "- The two-user WebSocket smoke test failed before the requested load was started."
    echo "- See \`smoke/summary.md\`, \`smoke/failure.txt\` and \`smoke.log\`."
  } > "$RESULT_DIR/summary.md"
  "${COMPOSE[@]}" logs --no-color --since 10m backend-benchmark > "$RESULT_DIR/backend.log" 2>&1 || true
  ln -sfn "$RUN_ID" "$ROOT_DIR/benchmark-results/latest-chat"
  echo
  echo "WebSocket 사전 확인에 실패해 본 측정을 시작하지 않았습니다."
  echo "결과 위치: $RESULT_DIR"
  exit "$SMOKE_EXIT_CODE"
fi
echo "WebSocket 사전 확인을 통과했습니다. 본 측정을 시작합니다."

container_ids() {
  "${COMPOSE[@]}" ps -q "${SERVICES[@]}"
}

collect_stats() {
  echo "epoch_seconds,timestamp,container,cpu_percent,memory_usage,memory_percent,net_io,block_io,pids"
  while true; do
    epoch_seconds="$(date +%s)"
    timestamp="$(date -Iseconds)"
    ids="$(container_ids | tr '\n' ' ')"
    if [[ -n "$ids" ]]; then
      # shellcheck disable=SC2086
      docker stats --no-stream \
        --format "$epoch_seconds,$timestamp,{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}" \
        $ids || true
    fi
    sleep 5
  done
}

collect_stats > "$RESULT_DIR/container-stats.csv" &
STATS_PID=$!

set +e
node scripts/room-chat-benchmark.mjs \
  --users "$USERS" \
  --room-size "$ROOM_SIZE" \
  --interval-ms "$INTERVAL_MS" \
  --warmup-seconds "$WARMUP_SECONDS" \
  --duration-seconds "$DURATION_SECONDS" \
  --result-dir "$RESULT_DIR" | tee "$RESULT_DIR/benchmark.log"
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

if [[ -f "$RESULT_DIR/summary.md" ]]; then
  measurement_start="$(node -e 'const r=require(process.argv[1]); console.log(Math.floor(r.measurementStartedAtEpochMs / 1000))' "$RESULT_DIR/result.json")"
  measurement_end="$(node -e 'const r=require(process.argv[1]); console.log(Math.ceil(r.measurementCompletedAtEpochMs / 1000))' "$RESULT_DIR/result.json")"
  {
    echo
    echo "## Peak container usage during measurement"
    echo
    echo "| Container | Peak CPU | Peak memory |"
    echo "|---|---:|---:|"
    awk -F',' -v start="$measurement_start" -v end="$measurement_end" 'NR > 1 && $1 >= start && $1 <= end {
        cpu=$4; mem=$6;
        gsub(/%/, "", cpu); gsub(/%/, "", mem);
        if ((cpu + 0) > maxCpu[$3]) maxCpu[$3] = cpu + 0;
        if ((mem + 0) > maxMem[$3]) maxMem[$3] = mem + 0;
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
    echo "Raw files: \`result.json\`, \`container-stats.csv\`, \`container-outcomes.csv\`, \`benchmark.log\`, \`backend.log\`."
  } >> "$RESULT_DIR/summary.md"
fi

ln -sfn "$RUN_ID" "$ROOT_DIR/benchmark-results/latest-chat"
INDEX_FILE="$ROOT_DIR/benchmark-results/chat-runs.csv"
if [[ ! -f "$INDEX_FILE" ]]; then
  echo "run_id,scenario,users,room_size,interval_seconds,duration_minutes,test_exit_code,result_dir" > "$INDEX_FILE"
fi
echo "$RUN_ID,$SCENARIO,$USERS,$ROOM_SIZE,$INTERVAL_SECONDS,$DURATION_MINUTES,$TEST_EXIT_CODE,$RESULT_DIR" >> "$INDEX_FILE"

echo
echo "결과 위치: $RESULT_DIR"
echo "요약 파일: $RESULT_DIR/summary.md"
echo "Grafana 확인이 필요하면 다음 명령으로 실행하세요."
echo "  docker compose --profile monitoring up -d --no-deps prometheus grafana"
echo "  http://127.0.0.1:3001"

exit "$TEST_EXIT_CODE"
