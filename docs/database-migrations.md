# 데이터베이스 마이그레이션 운영 가이드

## 기본 정책

- 데이터베이스 스키마 변경은 `src/main/resources/db/migration`의 Flyway SQL로 관리한다.
- 애플리케이션은 로컬과 운영 환경 모두 `ddl-auto: validate`를 사용한다. Hibernate가 스키마를 생성하거나 수정하지 않는다.
- 배포된 마이그레이션 파일은 수정하지 않는다. 다음 변경은 `V2__...sql`, `V3__...sql`처럼 새 파일로 추가한다.
- 설정 파일의 `baseline-on-migrate`는 항상 `false`로 유지한다. 기존 스키마를 검증 없이 기준점으로 등록하지 않는다.
- 운영 배포에서는 마이그레이션을 먼저 한 번 실행하고, 성공을 확인한 뒤 애플리케이션 인스턴스를 확장한다.

## 새 로컬 환경

MySQL과 Redis를 실행한 뒤 애플리케이션을 시작하면 Flyway가 빈 `nomat` 데이터베이스에 마이그레이션을 순서대로 적용한다.

```bash
docker compose up -d mysql redis
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

적용 결과는 다음 쿼리로 확인한다.

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 기존 개발 데이터베이스 전환

데이터 보존이 필요하지 않다면 기존 Hibernate 자동 생성 스키마를 삭제하고 다시 만드는 방법이 가장 단순하다. 필요한 데이터가 있다면 먼저 별도로 백업한다.

```bash
docker compose stop mysql
docker compose rm -f mysql
docker volume ls
docker volume rm <nomat-mysql-volume>
docker compose up -d mysql
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

볼륨 이름은 환경마다 다르므로 `docker volume ls` 결과를 확인하고 해당 프로젝트의 MySQL 볼륨만 삭제한다. 볼륨을 유지하면서 데이터베이스만 다시 만들 경우에는 MySQL에서 `nomat` 데이터베이스를 삭제하고 `utf8mb4` 문자 집합으로 재생성한 뒤 애플리케이션을 시작한다.

기존 데이터를 보존할 때는 다음 절차를 따른다.

1. 전체 데이터베이스 덤프를 만든다.
2. 임시 데이터베이스에 `V1`을 적용한다.
3. 테이블, 컬럼, 인덱스, 유일 제약과 외래 키를 현재 데이터베이스와 비교한다.
4. 충돌 데이터를 먼저 보정하고 현재 스키마를 `V1`과 같은 구조로 변경한다.
5. Hibernate `validate`로 엔티티 호환성을 확인한다.
6. 검증한 개발 데이터베이스에 한해 `baseline-on-migrate=true`와 `baseline-version=1`을 실행 인자로 한 번만 지정해 기준선을 등록한다.
7. 일회성 옵션 없이 애플리케이션을 다시 시작하고 `flyway_schema_history`를 확인한다.

기준선 등록 전에는 `baseline-on-migrate`를 사용하지 않는다. `flyway_schema_history`를 직접 작성해서도 안 된다. 운영 데이터베이스는 일회성 기준선 방식 대신 검증된 전환 마이그레이션과 별도 배포 계획을 사용한다.

## 운영 배포 절차

1. 배포 전에 데이터베이스 백업을 만들고 실제 복구가 가능한 위치에 보관한다.
2. 새 애플리케이션 버전의 마이그레이션을 단일 배포 작업 또는 첫 번째 인스턴스에서 적용한다.
3. `flyway_schema_history`의 최신 버전과 `success` 값을 확인한다.
4. 애플리케이션 상태 확인과 핵심 API 확인을 마친 뒤 나머지 인스턴스를 배포한다.

MySQL 백업 예시는 다음과 같다. 인증 정보는 명령행에 직접 적지 않고 비밀번호 프롬프트나 운영 비밀 관리 수단을 사용한다.

```bash
mysqldump --single-transaction --routines --triggers \
  -h <host> -P <port> -u <user> -p <database> > nomat-before-migration.sql
```

## 실패 및 복구

- 마이그레이션 실패 시 추가 인스턴스 배포를 중단한다.
- 실패 원인을 확인하기 전 `flyway_schema_history`를 직접 수정하거나 `repair`를 실행하지 않는다.
- MySQL DDL은 마이그레이션 전체가 하나의 트랜잭션으로 되돌아간다고 보장할 수 없다. 실패한 SQL까지 실제 스키마에 반영된 범위를 먼저 확인한다.
- 운영 데이터베이스를 즉석에서 역방향 수정하기보다 백업을 새 데이터베이스에 복구한 뒤 연결을 전환하거나, 검증된 전진 마이그레이션으로 복구한다.
- 복구 후에는 Flyway 이력, Hibernate 스키마 검증, 애플리케이션 상태 확인을 다시 수행한다.

## 마이그레이션 작성 규칙

- 파일명은 `V<순번>__<설명>.sql` 형식을 사용한다.
- 한 마이그레이션에는 함께 배포되어야 하는 스키마 변경만 포함한다.
- 대용량 테이블의 인덱스나 컬럼 변경은 잠금 시간과 실행 계획을 사전에 복제 DB에서 확인한다.
- 제거 또는 이름 변경은 이전 애플리케이션 버전과의 호환 기간을 고려해 단계적으로 수행한다.
- 새 마이그레이션은 빈 MySQL 적용과 Hibernate `validate` 검증을 통과해야 한다.
