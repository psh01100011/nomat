#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SCENARIO="${1:-B}"
ROOM_SIZE="${CHAT_BENCHMARK_ROOM_SIZE:-10}"
INTERVAL_SECONDS="${CHAT_BENCHMARK_INTERVAL_SECONDS:-2}"
DURATION_MINUTES="${CHAT_BENCHMARK_DURATION_MINUTES:-10}"
USER_STAGES=(100 300 500)

if [[ ! "$SCENARIO" =~ ^[ABC]$ ]]; then
  echo "사용법: $0 [A|B|C]" >&2
  exit 1
fi

for value in "$ROOM_SIZE" "$INTERVAL_SECONDS" "$DURATION_MINUTES"; do
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "환경 설정값은 양의 정수여야 합니다: $value" >&2
    exit 1
  fi
done

SUITE_ID="$(date '+%Y%m%d-%H%M%S')-${SCENARIO}-chat-suite"
SUITE_DIR="$ROOT_DIR/benchmark-results/$SUITE_ID"
SUITE_LOG="$SUITE_DIR/suite.log"
mkdir -p "$SUITE_DIR"
PROGRESS_PID=""

stop_progress() {
  if [[ -n "${PROGRESS_PID:-}" ]] && kill -0 "$PROGRESS_PID" 2>/dev/null; then
    kill "$PROGRESS_PID" 2>/dev/null || true
    wait "$PROGRESS_PID" 2>/dev/null || true
  fi
  PROGRESS_PID=""
}

start_progress() {
  local users="$1"
  local started_at=$SECONDS
  (
    while true; do
      sleep 15
      elapsed=$((SECONDS - started_at))
      printf '[연속 진행] %s명 단계 실행 중 · 경과 %02d:%02d:%02d\n' \
        "$users" "$((elapsed / 3600))" "$(((elapsed % 3600) / 60))" "$((elapsed % 60))"
    done
  ) > >(tee -a "$SUITE_LOG") &
  PROGRESS_PID=$!
}

trap stop_progress EXIT
trap 'stop_progress; exit 130' INT TERM

echo "채팅 부하 연속 측정"
echo "시나리오: $SCENARIO"
echo "사용자 단계: ${USER_STAGES[*]}"
echo "방 인원: $ROOM_SIZE, 메시지 간격: ${INTERVAL_SECONDS}초, 단계별 측정: ${DURATION_MINUTES}분"
echo "각 단계는 서버와 모니터링 컨테이너를 새로 시작해 이전 단계의 영향을 분리합니다."
echo "앞 단계가 실패하면 다음 단계는 실행하지 않습니다."

if [[ "${BENCHMARK_ASSUME_YES:-0}" != "1" ]]; then
  read -r -p "세 단계를 순서대로 실행할까요? [y/N] " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    exit 0
  fi
fi

{
  echo "# Room chat benchmark suite $SUITE_ID"
  echo
  echo "- Scenario: \`$SCENARIO\`"
  echo "- User stages: \`${USER_STAGES[*]}\`"
  echo "- Room size: $ROOM_SIZE"
  echo "- Message interval: ${INTERVAL_SECONDS}s per user"
  echo "- Measurement per stage: ${DURATION_MINUTES}m"
  echo
  echo "| Users | Result | Run |"
  echo "|---:|---|---|"
} > "$SUITE_DIR/summary.md"

SUITE_EXIT_CODE=0
for users in "${USER_STAGES[@]}"; do
  echo | tee -a "$SUITE_LOG"
  echo "===== ${users}명 단계 시작 =====" | tee -a "$SUITE_LOG"
  previous_run="$(readlink "$ROOT_DIR/benchmark-results/latest-chat" 2>/dev/null || true)"
  start_progress "$users"

  set +e
  BENCHMARK_ASSUME_YES=1 ./scripts/run-room-chat-benchmark.sh \
    "$SCENARIO" "$users" "$ROOM_SIZE" "$INTERVAL_SECONDS" "$DURATION_MINUTES" \
    2>&1 | tee -a "$SUITE_LOG"
  stage_exit_code=${PIPESTATUS[0]}
  set -e
  stop_progress

  current_run="$(readlink "$ROOT_DIR/benchmark-results/latest-chat" 2>/dev/null || true)"
  if [[ -n "$current_run" && "$current_run" != "$previous_run" ]]; then
    run_link="[summary](../$current_run/summary.md)"
  else
    run_link="결과 폴더 생성 전 실패"
  fi

  if (( stage_exit_code == 0 )); then
    echo "| $users | PASS | $run_link |" >> "$SUITE_DIR/summary.md"
  else
    echo "| $users | FAIL | $run_link |" >> "$SUITE_DIR/summary.md"
    SUITE_EXIT_CODE=$stage_exit_code
    echo "${users}명 단계가 실패해 이후 단계를 중단합니다." | tee -a "$SUITE_LOG"
    break
  fi
done

ln -sfn "$SUITE_ID" "$ROOT_DIR/benchmark-results/latest-chat-suite"

echo
echo "연속 측정 결과 위치: $SUITE_DIR"
echo "전체 요약: $SUITE_DIR/summary.md"
echo "각 단계의 상세 결과는 요약 파일에 기록되어 있습니다."

exit "$SUITE_EXIT_CODE"
