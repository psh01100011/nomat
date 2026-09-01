# nomat
스타크래프트 유즈맵인 노래맞히기 맵을 웹에서 즐길 수 있도록 하는 프로젝트

## Local Development

MySQL과 Redis를 Docker Compose로 실행한다.

```bash
docker compose up -d
```

백엔드는 `local` profile로 실행한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬 profile은 `localhost:3307`의 `nomat` MySQL database와 `localhost:6379` Redis를 사용한다.
이미지 업로드는 로컬에서도 실제 S3를 사용하므로 `.env.example`을 참고해 `AWS_S3_BUCKET`,
`AWS_REGION`, `AWS_S3_PUBLIC_BASE_URL`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 설정한다.
아직 Flyway migration이 없으므로 로컬에서는 Hibernate `ddl-auto=update`로 테이블을 생성한다.
