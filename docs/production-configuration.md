# 운영 환경 설정

## 시작 검증

`prod` 프로필은 다음 설정이 없거나 안전하지 않으면 애플리케이션 시작을 중단한다.

- MySQL URL, 사용자 이름, 비밀번호
- Redis URL
- Gmail SMTP 호스트, 계정, 앱 비밀번호, 발신 주소
- S3 버킷, 리전, 공개 URL
- 32바이트 이상의 JWT secret과 관리자 API key
- 정확한 HTTPS 프론트엔드 Origin
- 오디오 워커가 사용하는 `yt-dlp`와 ffmpeg 실행 파일

필요한 환경 변수는 `.env.example`을 기준으로 관리한다. AWS 자격 증명은 환경 변수 대신 인스턴스 또는 컨테이너 역할로 제공할 수 있다.

## 쿠키와 HTTPS

운영 프론트엔드와 API는 같은 Origin에서 제공한다. 리버스 프록시가 HTTPS를 종료하고 `/api`와 `/api/ws`를 백엔드로 전달한다.

- 인증 쿠키는 `Secure=true`로만 발급한다.
- `SameSite=Lax`를 사용한다.
- HTTP로 직접 접근하는 운영 경로는 제공하지 않는다.
- 프론트엔드와 API를 서로 다른 사이트에 배포하려면 쿠키 및 CSRF 정책을 다시 검토해야 한다.

## WebSocket Origin

`FRONTEND_ORIGIN`에는 사용자가 접속하는 정확한 HTTPS Origin 하나를 지정한다. 경로나 마지막 슬래시는 넣지 않는다.

```text
FRONTEND_ORIGIN=https://example.com
```

와일드카드와 HTTP Origin은 운영 시작 검증에서 거부한다.

## ALB와 사용자 IP

운영 트래픽은 Application Load Balancer를 통해서만 백엔드로 전달한다. ALB는 기본 append 모드로
`X-Forwarded-For` 끝에 실제 클라이언트 IP를 추가한다. 애플리케이션은 직접 연결된 상대가
`TRUSTED_PROXY_CIDRS`에 포함될 때만 이 헤더를 오른쪽부터 해석한다.

```text
TRUSTED_PROXY_CIDRS=10.0.1.0/24,10.0.2.0/24
```

여기에는 ALB가 배치된 VPC 서브넷의 실제 CIDR만 지정한다. 백엔드 보안 그룹은 애플리케이션 포트의
인바운드 소스를 ALB 보안 그룹으로 제한해야 한다. 인터넷이나 다른 보안 그룹에서 백엔드 포트로
직접 접근할 수 있게 열어 두면 안 된다.

ALB의 HTTPS 리스너는 HTTP 요청을 HTTPS로 리디렉션하고 `/api`, `/api/ws`를 백엔드 대상 그룹으로
전달한다. 대상 그룹 상태 검사는 `/api/actuator/health`를 사용한다. WebSocket 연결이 불필요하게
종료되지 않도록 ALB idle timeout은 클라이언트 heartbeat 간격보다 길게 설정한다.

## 서버 역할

현재 운영 정책은 모든 API 서버가 오디오 워커도 함께 실행하는 것이다. 각 서버에서 다음 값을 명시한다.

```text
AUDIO_PROCESSING_ENABLED=true
YT_DLP_PATH=yt-dlp
FFMPEG_PATH=ffmpeg
```

워커가 활성화되면 시작 시 `yt-dlp --version`과 `ffmpeg -version`을 실행한다. 명령을 찾을 수 없거나 실행 결과가 실패이면 해당 서버를 트래픽에 투입하지 않는다.

장애 대응 등으로 API 전용 서버가 필요할 때만 `AUDIO_PROCESSING_ENABLED=false`를 사용한다. 이 경우 외부 실행 파일 검사는 생략되지만 방 타이머, WebSocket, 정리 스케줄 등 다른 서버 기능은 그대로 실행된다.
