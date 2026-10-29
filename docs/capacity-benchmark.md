# NOMAT 로컬 용량 벤치마크

## 목적

로컬 Docker 자원 제한으로 운영 EC2 후보를 비교한다. 이 결과는 메모리 적합성, 자원 경합과 상대 처리량을 판단하는 자료이며, Graviton CPU 성능·T4g CPU credit·EBS·AWS 네트워크를 완전히 재현하지 않는다.

## 비교 시나리오

| 시나리오 | 백엔드 | 데이터 | CPU 배치 | 컨테이너 메모리 예산 |
|---|---|---|---|---|
| A | `t4g.small` 상당 | `t4g.medium` 상당 | 백엔드 2코어, 데이터 2코어 분리 | 백엔드 1.5GiB, 데이터 약 3.6GiB |
| B | `t4g.small` 상당 | `t4g.small` 상당 | 백엔드 2코어, 데이터 2코어 분리 | 백엔드 1.5GiB, 데이터 약 1.6GiB |
| C | `t4g.medium` 한 대 | 같은 서버 | 모든 서비스가 같은 2코어 공유 | 전체 약 3.4GiB |

나머지 메모리는 Ubuntu, Docker, CloudWatch Agent와 파일 캐시에 남겨둔다. C는 기존의 백엔드 ASG, 데이터 서버 분리와 무중단 인스턴스 교체 정책을 충족하지 않으므로 성능과 함께 운영상 손실을 별도로 평가한다.

## 사전 준비

1. Docker Desktop에 CPU 4개 이상, 메모리 8GB 이상을 할당한다.
2. `.env`의 실제 S3 버킷, 공개 URL과 AWS 인증이 정상인지 확인한다.
3. 로컬 DB에 이메일 인증된 활성 계정과 성공한 YouTube 오디오 표본이 20개 이상 있어야 한다.
4. 로컬에서 실행 중인 백엔드와 8080·9091 포트를 사용하는 프로그램을 종료한다.
5. 전원을 연결하고 절전, VPN과 대용량 다운로드를 중지한다.

현재 로컬 컴퓨터가 x86_64라면 Docker 이미지도 x86_64로 실행된다. ARM64 실행 호환성과 실제 Graviton 처리속도는 최종 EC2 단기 검증에서 확인한다.

## 실행

스크립트에 실행 권한을 한 번 부여한다.

```bash
chmod +x scripts/run-capacity-benchmark.sh
```

각 시나리오를 같은 문제 수와 워커 수로 실행한다.

```bash
./scripts/run-capacity-benchmark.sh A 100 2
./scripts/run-capacity-benchmark.sh B 100 2
./scripts/run-capacity-benchmark.sh C 100 2
```

스크립트는 기존 Compose 컨테이너를 재생성하지만 MySQL·Redis Docker volume을 삭제하지 않는다. 각 실행은 10문제 준비 처리 후 100문제를 측정하고, 실제 YouTube 추출과 S3 업로드를 수행한다. 정상 종료, 실패, 사용자 중단 시 측정 컨테이너는 자동으로 중지하고 결과 파일과 Docker volume은 유지한다.

문제 해결을 위해 종료 후에도 컨테이너가 꼭 필요할 때만 다음처럼 명시적으로 유지한다.

```bash
BENCHMARK_KEEP_RUNNING=1 ./scripts/run-capacity-benchmark.sh B 100 2
```

외부 다운로드 편차를 줄이려면 하루 중 비슷한 시간에 각 시나리오를 두 번 실행한다. 첫 비교가 끝날 때까지 워커 수를 변경하지 않는다.

## 결과 파일

결과는 Git에서 제외된 `benchmark-results/<실행 ID>/`에 저장된다.

| 파일 | 내용 |
|---|---|
| `metadata.txt` | 시나리오와 적용 자원 제한 |
| `warmup.log` | 10문제 준비 실행 결과 |
| `benchmark.log` | 처리시간, 성공·실패, p50·p95 결과 |
| `container-stats.csv` | 5초 간격 CPU, 메모리, 네트워크와 디스크 I/O |
| `container-outcomes.csv` | 재시작, OOM, 종료 상태 |
| `backend.log` | 측정 시점 백엔드 로그 |
| `summary.md` | 성공 여부, 처리 결과와 컨테이너별 최고 CPU·메모리 요약 |

`benchmark-results/runs.csv`에는 모든 실행 목록이 누적되고, `benchmark-results/latest`는 가장 최근 실행을 가리킨다. 이 디렉터리는 Git에 포함되지 않지만 같은 작업 공간에서 Codex가 직접 읽을 수 있다. A·B·C 실행을 마친 뒤 결과 해석을 요청하면 원본 CSV와 로그까지 함께 비교한다.

측정 후 Prometheus 데이터를 Grafana로 확인할 때만 로컬 Grafana를 실행한다. Grafana 자원은 측정값에 포함하지 않는다.
로컬 모니터링 컨테이너는 Docker Desktop 재시작 시 자동으로 다시 실행되지 않는다.

```bash
docker compose --profile monitoring up -d --no-deps prometheus grafana
```

브라우저에서 `http://127.0.0.1:3001`로 접속한다.

확인이 끝나면 다음 명령으로 다시 중지한다.

```bash
docker compose --profile monitoring stop prometheus grafana
```

## 판정 기준

다음 중 하나라도 발생하면 해당 구성은 운영 후보에서 제외한다.

- OOM 또는 컨테이너 재시작
- 100개 중 실패하거나 제한 시간까지 남은 작업 발생
- 백엔드 또는 데이터 컨테이너 합계가 메모리 예산의 85% 이상을 계속 사용
- CPU가 계속 포화인데 오디오 대기열이 줄지 않음
- 오디오 처리 중 MySQL·Redis 응답이 눈에 띄게 밀림
- 실행을 반복할 때 처리 종료 후 기준 메모리가 계속 증가

통과한 구성끼리는 전체 완료시간, 처리 p95, queue wait p95와 최대 메모리를 비교한다. B가 통과하더라도 여유 메모리가 작으면 A를 우선하고, C는 성능 외에 ASG·배포·장애 복구 제약까지 포함해 판단한다.

## 최종 EC2 검증

Docker 결과로 후보를 하나 또는 두 개로 줄인 뒤 운영 배포 전에 해당 EC2를 몇 시간만 실행한다. 같은 100문제 테스트로 Graviton 호환성, CPU credit, EBS와 AWS 네트워크를 확인하고 즉시 제거한다.

## 확정 결과

- 백엔드 EC2: `t4g.small`
- 데이터 EC2: `t4g.small`
- 서버당 오디오 워커: 2개
- 전체 서버 통합 YouTube 동시 처리 제한: 4개
- 모든 API 서버가 오디오 워커를 겸하며 전용 워커 ASG는 보류

B 시나리오의 워커 2개는 100문제를 약 8분 5초에 모두 처리했다. 워커 3개는 약 7분 11초로 11% 빨라졌지만 평균 CPU와 포화 구간, 개별 처리 p95가 증가해 운영 기본값에서 제외했다. 로컬 x86 Docker 측정이므로 배포 전 ARM64 EC2 단기 검증은 여전히 필요하다.
