# syntax=docker/dockerfile:1.7
# Java bytecode is architecture independent; compile on the builder's native CPU.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

FROM denoland/deno:bin-2.9.8@sha256:b507583d3c68ba69f3ed2ce5969ae5b8b70f43421c781279c7aea06435263543 AS deno

FROM mwader/static-ffmpeg:9.0.2-1@sha256:f5d100184884eb3e82b0eb14bb5ebf98da99826ac824989720297fb9ef21c822 AS ffmpeg

FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f AS python-tools

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

COPY docker/yt-dlp-requirements.txt /tmp/yt-dlp-requirements.txt

RUN python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --disable-pip-version-check --no-cache-dir --no-deps --require-hashes -r /tmp/yt-dlp-requirements.txt \
    && /opt/yt-dlp/bin/pip uninstall -y pip setuptools

FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f

# Apply the PCRE2 security fix even when the pinned base predates its release.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl python3 libpcre2-8-0=10.42-4ubuntu2.2 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=python-tools /opt/yt-dlp /opt/yt-dlp
COPY --from=deno /deno /usr/local/bin/deno
COPY --from=ffmpeg /ffmpeg /ffprobe /usr/local/bin/
COPY --from=ffmpeg /versions.json /usr/share/doc/ffmpeg-static/versions.json
COPY --from=ffmpeg /doc /usr/share/doc/ffmpeg-static/doc

RUN ln -s /opt/yt-dlp/bin/yt-dlp /usr/local/bin/yt-dlp \
    && test "$(yt-dlp --version)" = "2026.10.26" \
    && deno --version \
    && ffmpeg -version

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

RUN groupadd --system --gid 10001 nomat \
    && useradd --system --uid 10001 --gid 10001 --create-home nomat \
    && chown -R nomat:nomat /app

USER 10001

ENV DENO_NO_UPDATE_CHECK=1 DENO_NO_PROMPT=1 SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt

EXPOSE 8080 9091

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
