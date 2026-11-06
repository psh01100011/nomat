#!/usr/bin/env python3
"""ARM64 도구·MP3 변환·격리된 prod 프로필 기동을 검증한다."""
import json
import pathlib
import subprocess
import time
import uuid
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
RECORD = ROOT / 'build/production-image.json'


def run(args, capture=True, content=None):
    result = subprocess.run(args, cwd=ROOT, text=True, input=content,
                            stdout=subprocess.PIPE if capture else None, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('검증 명령 실패: ' + ' '.join(args[:3]))
    return result.stdout.strip() if capture else None


def main():
    record = json.loads(RECORD.read_text())
    image = record['image']
    metadata = json.loads(run(['docker', 'image', 'inspect', image]))[0]
    if metadata['Id'] != record['image_id'] or metadata['Architecture'] != 'arm64' or metadata['Config']['User'] != '10001':
        raise RuntimeError('검증 이미지 ID·ARM64·비루트 사용자 확인 실패')
    # Exercise the target CPU's actual executables, not just image metadata.
    tools = run(['docker', 'run', '--rm', '--platform', 'linux/arm64', '--network', 'none',
                 '--entrypoint', '/bin/sh', image, '-ec',
                 'test "$(uname -m)" = aarch64; java -version 2>&1; '
                 'test "$(yt-dlp --version)" = 2026.10.26; deno --version; '
                 'ffmpeg -version | head -1 | grep -q "ffmpeg version 9.0.2 "; '
                 'ffmpeg -protocols 2>/dev/null | grep -qx "  https"; '
                 '/opt/yt-dlp/bin/python -c "import importlib.metadata as m; assert m.version(\'yt-dlp-ejs\') == \'0.8.0\'"; '
                 'test "$(dpkg-query -W -f=\'${Version}\' libpcre2-8-0)" = 10.42-4ubuntu2.2; '
                 'test ! -e /usr/share/python-wheels/pip-24.0-py3-none-any.whl; '
                 'test ! -e /opt/yt-dlp/bin/pip; '
                 'ffmpeg -v error -f lavfi -i sine=frequency=440:duration=3 -c:a aac /tmp/source.m4a; '
                 'ffmpeg -v error -sseof -2 -i /tmp/source.m4a -t 2 -vn -acodec libmp3lame -b:a 128k -ar 44100 -ac 2 /tmp/check.mp3; '
                 'ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 /tmp/check.mp3; '
                 'deno eval "if (Deno.build.arch !== \'aarch64\') throw Error(\'arch\'); console.log(\'deno-arm64-ok\')"'])
    print(tools)
    suffix = uuid.uuid4().hex[:10]
    probe = ROOT / 'build' / ('image-probe-' + suffix)
    probe.mkdir()
    run(['javac', '--release', '21', '-d', str(probe), str(ROOT / 'docker/WebpProbe.java')])
    stopped = run(['docker', 'create', image])
    try:
        run(['docker', 'cp', stopped + ':/app/app.jar', str(probe / 'app.jar')])
    finally:
        run(['docker', 'rm', stopped])
    libs = probe / 'lib'
    libs.mkdir()
    with zipfile.ZipFile(probe / 'app.jar') as jar:
        for name in jar.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                (libs / pathlib.Path(name).name).write_bytes(jar.read(name))
    print(run(['docker', 'run', '--rm', '--platform', 'linux/arm64', '--network', 'none',
               '--mount', f'type=bind,src={probe},dst=/checks,readonly', '--entrypoint', 'java', image,
               '-Djava.awt.headless=true', '-cp', '/checks:/checks/lib/*', 'WebpProbe']))
    network = 'nomat-image-check-' + suffix
    database, redis, app = [network + '-' + name for name in ('mysql', 'redis', 'app')]
    containers = []
    run(['docker', 'network', 'create', '--internal', network])
    try:
        # Disposable local DBs: no host ports, bind mounts, or production values.
        run(['docker', 'run', '-d', '--name', database, '--network', network, '--network-alias', 'mysql',
             '-e', 'MYSQL_ROOT_PASSWORD=image-check-only', '-e', 'MYSQL_DATABASE=nomat',
             '-e', 'MYSQL_USER=nomat', '-e', 'MYSQL_PASSWORD=image-check-only',
             'mysql@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902'])
        containers.append(database)
        run(['docker', 'run', '-d', '--name', redis, '--network', network, '--network-alias', 'redis',
             'redis@sha256:b6b6e3dc0c0952737748e55d5d885272840c7e42ba41ab1d5489f693419882f5'])
        containers.append(redis)
        for _ in range(90):
            ready = subprocess.run(['docker', 'exec', database, 'mysql', '--protocol=TCP', '-h127.0.0.1',
                                    '-unomat', '-pimage-check-only', 'nomat', '-e', 'SELECT 1;'], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(2)
        else:
            raise RuntimeError('검증용 MySQL 시작 대기 시간 초과')
        environment = {
            'SPRING_PROFILES_ACTIVE': 'prod', 'DB_URL': 'jdbc:mysql://mysql:3306/nomat?useSSL=false&allowPublicKeyRetrieval=true',
            'DB_USERNAME': 'nomat', 'DB_PASSWORD': 'image-check-only', 'REDIS_URL': 'redis://redis:6379',
            'JWT_SECRET': 'local-image-check-only-secret-32-characters', 'ADMIN_API_KEY': 'local-image-check-only-admin-key-32-characters',
            'TRUSTED_PROXY_CIDRS': '127.0.0.1/32', 'FRONTEND_ORIGIN': 'https://example.invalid',
            'MAIL_FROM': 'check@example.invalid',
            'AWS_S3_BUCKET': 'image-check-only', 'AWS_REGION': 'ap-northeast-2', 'AWS_S3_PUBLIC_BASE_URL': 'https://example.invalid',
            'AWS_EC2_METADATA_DISABLED': 'true', 'AUDIO_PROCESSING_ENABLED': 'true',
            'APP_ASSET_CLEANUP_ENABLED': 'false',
            'JAVA_TOOL_OPTIONS': '-XX:MaxRAMPercentage=65 -XX:TieredStopAtLevel=1',
            'SPRING_DATA_REDIS_TIMEOUT': '30s', 'SPRING_DATA_REDIS_CONNECT_TIMEOUT': '30s',
            'MANAGEMENT_HEALTH_MAIL_ENABLED': 'false',
        }
        args = ['docker', 'run', '-d', '--name', app, '--platform', 'linux/arm64', '--network', network,
                '--memory', '1536m', '--cpus', '2']
        for key, value in environment.items():
            args.extend(['-e', key + '=' + value])
        run(args + [image])
        containers.append(app)
        for _ in range(240):
            response = subprocess.run(['docker', 'exec', app, 'curl', '-fsS', '--max-time', '3',
                                       'http://127.0.0.1:9091/actuator/health'], capture_output=True, text=True)
            if response.returncode == 0 and json.loads(response.stdout).get('status') == 'UP':
                break
            if run(['docker', 'inspect', '--format', '{{.State.Running}}', app]) != 'true':
                raise RuntimeError('검증 앱이 종료됨: ' + app)
            time.sleep(2)
        else:
            raise RuntimeError('ARM64 prod 프로필 health UP 대기 시간 초과: ' + app)
        migrations = run(['docker', 'exec', database, 'mysql', '-unomat', '-pimage-check-only', '-N', '-B',
                          'nomat', '-e', 'SELECT MAX(version) FROM flyway_schema_history WHERE success=1;'])
        if migrations != '3':
            raise RuntimeError('검증 DB의 Flyway V3 적용 확인 실패')
        record.update(validation_passed=True, validation={
            'platform': 'linux/arm64', 'user': '10001', 'mp3_conversion': True, 'webp_roundtrip': True,
            'prod_health': 'UP', 'flyway_version': migrations, 'audio_worker_enabled': True,
            'external_network': False, 'real_youtube_download': False, 'mail_connection_checked': False,
            'ffmpeg_version': '9.0.2', 'ffmpeg_static_components': '/usr/share/doc/ffmpeg-static/versions.json',
            'redis_timeout': '30s', 'jvm_tiered_stop_at_level': 1,
        })
        RECORD.write_text(json.dumps(record, indent=2) + '\n')
        print(json.dumps(record['validation']))
    except Exception:
        if app in containers:
            # This network uses only dummy configuration; do not use real SSM values here.
            logs = run(['docker', 'logs', '--tail', '80', app])
            print(logs)
        raise
    finally:
        for name in reversed(containers):
            subprocess.run(['docker', 'rm', '-f', '-v', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        run(['docker', 'network', 'rm', network])


if __name__ == '__main__':
    main()
