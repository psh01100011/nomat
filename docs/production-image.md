# 운영 ARM64 이미지

## 고정 버전

| 구성 | 버전/고정 방법 |
| --- | --- |
| Java | Temurin 21.0.13+9, JDK Jammy / JRE Noble 공식 multiarch image digest |
| yt-dlp | 2026.10.26, 공식 PyPI wheel SHA256 |
| yt-dlp-ejs | 0.8.0, yt-dlp가 요구하는 버전과 공식 wheel SHA256 |
| Deno | 2.9.8, 공식 bin 이미지 digest |
| ffmpeg | `mwader/static-ffmpeg:9.0.2-1`의 9.0.2 정적 빌드, multiarch digest 고정 |
| Python | 실행 base인 Ubuntu 24.04의 Python 3.12 계열, yt-dlp의 Python 3.11 이상 요구 충족 |
| WebP ImageIO | `com.github.usefulness:webp-imageio:0.11.0`, Linux aarch64 네이티브 포함 |
| PCRE2 | Ubuntu 공식 보안 수정판 `libpcre2-8-0=10.42-4ubuntu2.2` |
| APT | `apt`·`libapt-pkg6.0t64=2.8.4ubuntu0.1` |
| CA 인증서 | `ca-certificates=20260816ubuntu0.24.04.2` |

Java bytecode는 빌드 호스트의 CPU에서 컴파일하고 실행 이미지는 `linux/arm64`로 만든다. 실행 사용자는 UID/GID 10001이다. DB/JWT/메일/AWS 운영 비밀 값은 빌드 인자·이미지에 넣지 않는다. 배포 시 SSM에서 런타임으로 주입한다.

Java·Deno·FFmpeg base digest와 yt-dlp/EJS 해시를 고정했다. OS의 모든 전이 의존성까지 snapshot repository로 동결한 빌드는 아니다. 배포에는 검증 후 ECR에서 조회한 이미지 digest를 사용한다. 고정 버전이 저장소에서 사라지면 자동으로 다른 버전을 설치하지 않고 빌드를 실패시킨다.

Ubuntu Noble의 기본 ffmpeg 6.1.1 후보에서 ECR Critical 18·High 230건을 확인해 배포 대상으로 제외했다. 기본 저장소에는 해당 수정판이 없으며 일부 수정판은 Ubuntu Pro ESM에 있다([Ubuntu CVE 기록](https://ubuntu.com/security/CVE-2026-40962)). 오래된 ffmpeg와 동적 의존성 묶음을 설치하는 대신 [wader/static-ffmpeg](https://github.com/wader/static-ffmpeg)의 9.0.2-1 빌드를 사용한다. 이는 FFmpeg 공식 배포 이미지가 아닌 별도 제공자의 빌드다. digest를 고정하고 구성 버전과 제공 문서를 `/usr/share/doc/ffmpeg-static`에 포함한다. ECR 기본 스캔은 정적 바이너리에 내장된 모든 라이브러리를 검사하지 않으므로, 기본 스캔의 0건을 전체 취약점 부재로 해석하지 않는다.

정적 FFmpeg 후보의 재스캔에서 Critical 0·High 12건(PCRE2 8·pip 4)을 확인했다. PCRE2는 [Ubuntu 2026-11-03 보안 수정판](https://ubuntu.com/security/CVE-2026-103111)을 명시적으로 설치한다. pip/venv 설치는 별도 빌드 stage에서 수행하고 pip/setuptools를 제거한 실행 환경만 복사한다. 최종 stage에는 python3-venv/pip wheel 패키지를 설치하지 않는다. 스캔 DB 파일이나 패키지 식별 정보를 숨겨 수치를 낮추지 않는다.

YouTube의 JavaScript challenge 처리를 위해 EJS와 Deno를 함께 포함한다. 실행 중 EJS를 원격에서 자동 다운로드하지 않는다. yt-dlp는 기본 Python 설치와 명시적으로 추가한 EJS를 사용하며 curl-cffi 등 선택 기능은 설치하지 않는다. 관련 근거: [공식 EJS 안내](https://github.com/yt-dlp/yt-dlp/wiki/EJS), [yt-dlp 공식 패키지](https://pypi.org/project/yt-dlp/2026.10.26/), [Temurin 이미지](https://adoptium.net/installation/containers/).

기존 `org.sejda.imageio:webp-imageio:0.1.6`의 Linux 라이브러리는 ELF machine 62(x86_64)이며 ARM64를 지원하지 않는다. [ARM 지원 ImageIO 포크](https://github.com/usefulness/webp-imageio)의 0.11.0으로 교체했고, 결과 이미지 JAR에서 꺼낸 실제 의존성으로 ARM64 WebP 인코딩·디코딩 왕복 검사를 수행한다. API에서 사용하는 표준 ImageIO 방식은 유지한다.

## 로컬 빌드·검증·업로드

저장소 루트에서 실행한다. Docker Desktop의 ARM64 실행 지원, Java 21, Python 3와 로컬 AWS SSO `nomat-prod`가 필요하다. GitHub Actions는 사용하지 않는다.

```bash
python3 scripts/production-image.py build
python3 scripts/verify-production-image.py
python3 scripts/production-image.py publish
python3 scripts/production-image.py scan
```

build는 기본 Gradle 테스트 후 ARM64 이미지를 만들고 `build/production-image.json`에 결과를 남긴다. 이 파일은 Git ignore 대상이다. Dockerfile·Gradle·src·docker 입력 파일의 SHA256과 base revision으로 `candidate-<해시20자리>-<base SHA12자리>[-dirty]` 태그를 만든다. 미커밋 빌드도 입력 해시와 base revision·dirty label로 명시하며, 현재 HEAD만으로 빌드했다고 표시하지 않는다. 커밋은 사용자가 직접 수행한다. 정식 배포 버전 확정 시 같은 절차로 커밋된 소스에서 다시 빌드·검증한다.

검증은 실제 ARM64 실행 파일·비루트 사용자·Deno 실행·EJS 버전·ffmpeg MP3 변환·WebP 인코딩/디코딩을 확인한다. 이어서 외부 통신과 호스트 포트가 없는 Docker internal network에서 임시 MySQL/Redis와 앱을 실행하고, 오디오 워커가 켜진 `prod` 프로필의 health UP과 Flyway V3를 확인한다. 앱 root는 읽기 전용이며 capability 제거·no-new-privileges를 적용한다. 가짜 설정 13개는 별도 읽기 전용 volume에 Spring configtree로 전달하고 컨테이너 환경 변수에 남지 않는지 검사한다. ffmpeg/Deno의 `/tmp` 쓰기도 확인한다. 운영 SSM은 읽지 않는다. 임시 컨테이너·설정/DB 볼륨·네트워크는 finally에서 정리한다.

검증용 관리자 키와 JWT도 운영 설정 검사의 32바이트 최소 길이를 충족한다. 로컬 검증에는 메일 서버가 없으므로 `MANAGEMENT_HEALTH_MAIL_ENABLED=false`로 메일 연결 health만 제외한다. 이 옵션은 검증 도구에서만 전달하며 운영 이미지의 기본 설정은 바꾸지 않는다. 메일 전송이나 SES 연결을 검증한 것으로 기록하지 않는다.

로컬 ARM64 에뮬레이션에서는 Redis 연결 초기화가 기본 2초를 넘길 수 있어 검증 도구에서만 Redis timeout/connect-timeout을 30초로 설정한다. JVM도 로컬 검증에서만 `TieredStopAtLevel=1`을 사용한다. 운영 기본 timeout/JVM 설정을 검증한 결과나 성능 벤치마크로 취급하지 않는다. 실제 Graviton에서 기본 설정과 자원 사용량을 별도로 확인한다.

publish는 입력 해시·검증 성공·검증한 로컬 이미지 ID/아키텍처·AWS 계정·ECR 불변 태그/scan-on-push를 검사한 뒤 기존 `nomat-prod-backend`에 업로드한다. ECR 로그인 토큰은 stdin으로 전달하며 출력하지 않는다. ECR에서 최종 digest를 조회해 결과 파일에 저장한다. 이미 존재하는 불변 태그는 덮어쓰지 않는다.

scan은 해당 digest의 ECR 기본 스캔 응답을 `build/production-image-scan.json`에 저장하고 상태·심각도별 건수를 결과 파일에 기록한다. IN_PROGRESS이면 완료 후 다시 조회한다. `critical_high_clear`는 기본 스캔의 Critical/High 건수만 표현하며 배포 승인이나 전체 보안 검증을 대신하지 않는다.

이 단계는 로컬 에뮬레이션 기반 ARM64 실행 검증이다. 실제 Graviton EC2에서의 YouTube 다운로드·전체 오디오 추출, CPU credit/자원 사용량, 운영 S3/SES 접근과 HTTPS 연결 검증은 후속 백엔드 배포 단계에서 수행한다. SES API 연동은 `c89ab5ca911f`에 포함됐으며 실제 메일 수신은 별도 검증한다.

## 2026-11-06 보안 업데이트

커밋 `c89ab5ca911f`의 SES 포함 이미지 재빌드에서 APT 관련 동일 CVE의 High 2건과 CA 인증서 advisory 1건을 확인했다. 이 이미지는 운영 기동 대상에서 제외했다. [Ubuntu CVE-2026-109518](https://ubuntu.com/security/CVE-2026-109518)의 Noble 수정판 `2.8.4ubuntu0.1`과 [USN-9001-1](https://ubuntu.com/security/notices/USN-9001-1)의 인증서 수정판을 명시적으로 설치한다. 오래된 APT로 업데이트를 시작하는 단계에서는 `Acquire::Retries=0`을 사용한다. 검증 도구도 패키지의 실제 설치 버전을 검사한다. 미커밋 수정 후보는 검증·스캔 용도로만 사용하며 사용자 커밋 후 clean 소스로 정식 이미지를 다시 만든다.

수정 후보 `candidate-f1830a8a6949a64cc528-c89ab5ca911f-dirty`, digest `sha256:e39242cbb2e29e12feea566defe0fb7a225db7a169ff9eba3a3017bd14bbe894`의 기본 스캔은 COMPLETE/탐지 0건이었다. 기본 테스트 416건 및 위 ARM64 실행 검증을 통과했고, configtree 13개·읽기 전용 root 상태에서도 health UP/Flyway V3·오디오 worker·ffmpeg/Deno 임시 파일 쓰기를 확인했다. 운영 앱·DB에는 적용하지 않았다.

## 2026-11-04 검증 결과

- 기본 Gradle 테스트 411건 통과. ARM64 Java/Deno/yt-dlp/EJS·ffmpeg 꼬리 구간 MP3 변환·WebP/Java2D 변환·prod health UP·Flyway V3·오디오 워커 기동 확인.
- 최종 ECR 후보 태그 `candidate-006dcb2dbcbd744d88b0-2d7e57e3a752-dirty`, digest `sha256:454c764c70c09f7103525d318883ab862db476f924ac5dd0a812308e49bd7846`, 저장소 `713881824287.dkr.ecr.ap-northeast-2.amazonaws.com/nomat-prod-backend`.
- ECR에서 ARM64·UID 10001·입력 해시 일치 확인. 기본 스캔 COMPLETE·탐지 0건. 정적 바이너리 검사 범위 제한은 위 설명을 적용한다. 임시 검증 컨테이너·네트워크 잔존 0개.
- 미커밋 변경 포함 후보이므로 커밋된 소스에서 정식 배포 이미지를 확정한다. 현재 운영 배포·실제 YouTube/SES 연결은 수행하지 않았다.
