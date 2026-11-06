#!/usr/bin/env python3
"""로컬 ARM64 검증 이미지 빌드·기존 ECR 업로드. 비밀 값은 출력하지 않는다."""
import hashlib
import json
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RECORD = ROOT / 'build/production-image.json'
REGION = 'ap-northeast-2'
ACCOUNT = '713881824287'
REPOSITORY = 'nomat-prod-backend'
REGISTRY = f'{ACCOUNT}.dkr.ecr.{REGION}.amazonaws.com'


def command(args, capture=False, content=None):
    result = subprocess.run(args, cwd=ROOT, input=content, text=True,
                            stdout=subprocess.PIPE if capture else None, check=True)
    return result.stdout.strip() if capture else None


def source_hash():
    paths = [ROOT / name for name in ('Dockerfile', '.dockerignore', 'gradlew', 'build.gradle', 'settings.gradle')]
    for name in ('src', 'gradle', 'docker'):
        paths.extend(path for path in (ROOT / name).rglob('*') if path.is_file())
    digest = hashlib.sha256()
    for path in sorted(paths):
        digest.update(str(path.relative_to(ROOT)).encode() + b'\0')
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def aws(*args):
    return command(['aws', *args, '--profile', 'nomat-prod', '--region', REGION, '--output', 'json', '--no-cli-pager'], capture=True)


def build():
    command(['./gradlew', 'test', '--no-daemon'])
    fingerprint = source_hash()
    revision = command(['git', 'rev-parse', 'HEAD'], capture=True)
    dirty = bool(command(['git', 'status', '--porcelain'], capture=True))
    tag = 'candidate-' + fingerprint[:20] + '-' + revision[:12] + ('-dirty' if dirty else '')
    image = 'nomat-backend:' + tag
    command(['docker', 'buildx', 'build', '--platform', 'linux/arm64', '--load',
             '--tag', image, '--label', 'io.nomat.source.sha256=' + fingerprint,
             '--label', 'io.nomat.source.base-revision=' + revision,
             '--label', 'io.nomat.source.dirty=' + str(dirty).lower(), '.'])
    metadata = json.loads(command(['docker', 'image', 'inspect', image], capture=True))[0]
    if metadata['Architecture'] != 'arm64' or metadata['Os'] != 'linux' or source_hash() != fingerprint:
        raise RuntimeError('빌드 대상 또는 빌드 중 소스 변경 확인 실패')
    record = dict(image=image, tag=tag, image_id=metadata['Id'], platform='linux/arm64',
                  source_sha256=fingerprint, base_revision=revision, dirty=dirty,
                  validation_passed=False)
    RECORD.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record))


def publish():
    record = json.loads(RECORD.read_text())
    if not record.get('validation_passed') or record['source_sha256'] != source_hash():
        raise RuntimeError('현재 소스와 같은 이미지의 실행 검증이 필요합니다')
    metadata = json.loads(command(['docker', 'image', 'inspect', record['image']], capture=True))[0]
    if metadata['Id'] != record['image_id'] or metadata['Architecture'] != 'arm64':
        raise RuntimeError('검증한 이미지와 업로드 대상 불일치')
    if json.loads(aws('sts', 'get-caller-identity'))['Account'] != ACCOUNT:
        raise RuntimeError('AWS 계정 불일치')
    repository = json.loads(aws('ecr', 'describe-repositories', '--repository-names', REPOSITORY))['repositories'][0]
    if repository['imageTagMutability'] != 'IMMUTABLE' or not repository['imageScanningConfiguration']['scanOnPush']:
        raise RuntimeError('ECR 태그 불변·스캔 설정 불일치')
    # The token is passed directly over stdin, never as an argument or log.
    token = command(['aws', 'ecr', 'get-login-password', '--profile', 'nomat-prod', '--region', REGION], capture=True)
    command(['docker', 'login', '--username', 'AWS', '--password-stdin', REGISTRY], content=token)
    remote = f'{REGISTRY}/{REPOSITORY}:{record["tag"]}'
    command(['docker', 'tag', record['image'], remote])
    command(['docker', 'push', remote])
    details = json.loads(aws('ecr', 'describe-images', '--repository-name', REPOSITORY,
                             '--image-ids', 'imageTag=' + record['tag']))['imageDetails'][0]
    record.update(ecr_tag=remote, ecr_digest=f'{REGISTRY}/{REPOSITORY}@{details["imageDigest"]}')
    RECORD.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps({'ecr_tag': record['ecr_tag'], 'ecr_digest': record['ecr_digest']}))


def scan():
    record = json.loads(RECORD.read_text())
    digest = record['ecr_digest'].split('@', 1)[1]
    response = json.loads(aws('ecr', 'describe-image-scan-findings', '--repository-name', REPOSITORY,
                              '--image-id', 'imageDigest=' + digest))
    status = response['imageScanStatus']['status']
    counts = response.get('imageScanFindings', {}).get('findingSeverityCounts', {})
    (ROOT / 'build/production-image-scan.json').write_text(json.dumps(response, indent=2) + '\n')
    record['ecr_basic_scan'] = {'status': status, 'counts': counts,
                               'critical_high_clear': status == 'COMPLETE' and not counts.get('CRITICAL', 0) and not counts.get('HIGH', 0),
                               'static_binary_coverage_limited': True}
    RECORD.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record['ecr_basic_scan']))


if __name__ == '__main__':
    os.environ['AWS_EC2_METADATA_DISABLED'] = 'true'
    if sys.argv[1:] == ['build']:
        build()
    elif sys.argv[1:] == ['publish']:
        publish()
    elif sys.argv[1:] == ['scan']:
        scan()
    else:
        raise SystemExit('사용법: python3 scripts/production-image.py build|publish|scan')
