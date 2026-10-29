#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import { basename } from 'node:path';
import { monitorEventLoopDelay } from 'node:perf_hooks';

const args = parseArgs(process.argv.slice(2));
if (args.help) {
  printUsage();
  process.exit(0);
}

const config = validateConfig({
  baseUrl: args['base-url'] ?? 'http://127.0.0.1:8080/api',
  wsUrl: args['ws-url'],
  mapId: integerArg(args, 'map-id'),
  users: integerArg(args, 'users'),
  roomSize: integerArg(args, 'room-size', 10),
  intervalMs: integerArg(args, 'interval-ms', 2000),
  warmupSeconds: integerArg(args, 'warmup-seconds', 120),
  durationSeconds: integerArg(args, 'duration-seconds', 600),
  resultDir: args['result-dir'],
  setupConcurrency: integerArg(args, 'setup-concurrency', 20),
});

const runId = basename(config.resultDir);
const runToken = Date.now().toString(36);
const roomCount = config.users / config.roomSize;
const gameRoomCount = Math.round(roomCount * 0.9);
const waitingRoomCount = roomCount - gameRoomCount;
const clients = [];
const rooms = [];
const sentMessages = new Map();
const latencyHistogram = new Uint32Array(60_002);
const metrics = {
  connected: 0,
  disconnected: 0,
  sent: 0,
  received: 0,
  unrelatedReceived: 0,
  applicationErrors: 0,
  applicationErrorCodes: {},
  stompErrors: 0,
  websocketErrors: 0,
  latencySumMs: 0,
  latencyMaxMs: 0,
};
let phase = 'setup';
let measurementStartedAt = 0;
let measurementCompletedAt = 0;
let progressTimer;
let resourceTimer;
let peakRssBytes = 0;
const eventLoopDelay = monitorEventLoopDelay({ resolution: 20 });

async function runBenchmark() {
  await mkdir(config.resultDir, { recursive: true });
  try {
    const selectedMap = await selectBenchmarkMap();
    config.mapId = selectedMap.mapId;
    config.mapTitle = selectedMap.title;
    config.selectedQuestionCount = requiredQuestionCount();
    console.log(`자동 선택 맵: ${selectedMap.title} (ID=${selectedMap.mapId}, 문제=${selectedMap.questionCount}개)`);
    console.log(
      `채팅 부하 테스트 준비: 사용자 ${config.users}명, 방 ${roomCount}개, 방당 ${config.roomSize}명`
      + `, 게임방 ${gameRoomCount}개, 대기방 ${waitingRoomCount}개`,
    );
    const identities = await createGuestIdentities();
    await createRoomsAndJoin(identities);
    await connectClients(identities);
    await startGameRooms();

    progressTimer = setInterval(printProgress, 10_000);
    clients.forEach((client, index) => startSender(client, index));

    phase = 'warmup';
    console.log(`준비 부하를 ${config.warmupSeconds}초 동안 실행합니다.`);
    await sleep(config.warmupSeconds * 1000);

    phase = 'measure';
    measurementStartedAt = Date.now();
    measurementCompletedAt = 0;
    peakRssBytes = process.memoryUsage().rss;
    eventLoopDelay.reset();
    eventLoopDelay.enable();
    resourceTimer = setInterval(sampleLoadGeneratorResources, 1_000);
    console.log(`본 측정을 ${config.durationSeconds}초 동안 실행합니다.`);
    await sleep(config.durationSeconds * 1000);
    measurementCompletedAt = Date.now();
    clearInterval(resourceTimer);
    sampleLoadGeneratorResources();
    eventLoopDelay.disable();

    phase = 'drain';
    clients.forEach((client) => client.stopSending());
    console.log('마지막 메시지 수신을 10초 동안 기다립니다.');
    await sleep(10_000);

    const result = buildResult();
    await writeResult(result);
    printFinalResult(result);

    if (result.connections.failed > 0
        || result.connections.disconnectedDuringTest > 0
        || result.messages.missingDeliveries > 0
        || result.messages.duplicateDeliveries > 0
        || result.errors.total > 0) {
      process.exitCode = 1;
    }
  } catch (error) {
    process.exitCode = 1;
    console.error(`테스트 실패: ${error instanceof Error ? error.stack : error}`);
    await writeFile(
      `${config.resultDir}/failure.txt`,
      `${error instanceof Error ? error.stack : error}\n`,
      'utf8',
    );
  } finally {
    phase = 'cleanup';
    clearInterval(progressTimer);
    clearInterval(resourceTimer);
    eventLoopDelay.disable();
    clients.forEach((client) => client.close());
    await cleanupRooms();
  }
}

async function selectBenchmarkMap() {
  const minimumQuestions = requiredQuestionCount();
  if (config.mapId) {
    const response = await api(`/maps/${config.mapId}`);
    if (response.data.questionCount < minimumQuestions) {
      throw new Error(`맵 ${config.mapId}의 문제 수가 부족합니다: 필요=${minimumQuestions}, 실제=${response.data.questionCount}`);
    }
    return response.data;
  }

  let page = 0;
  while (true) {
    const response = await api(`/maps?page=${page}&size=100&sort=latest`);
    const candidate = response.data.maps.find(
      (map) => map.title.startsWith('[테스트]') && map.questionCount >= minimumQuestions,
    );
    if (candidate) return candidate;
    if (!response.data.hasNext) break;
    page += 1;
  }
  throw new Error(
    `제목이 [테스트]로 시작하고 공개·게시 상태이면서 문제가 ${minimumQuestions}개 이상인 전용 맵을 찾지 못했습니다.`,
  );
}

function requiredQuestionCount() {
  return Math.max(Math.ceil((config.warmupSeconds + config.durationSeconds + 10) / 300), 1);
}

async function createGuestIdentities() {
  const identities = new Array(config.users);
  let completed = 0;
  await parallelFor(config.users, config.setupConcurrency, async (index) => {
    const nickname = `부하${runToken.slice(-4)}${index.toString(36)}`.slice(0, 12);
    const response = await api('/auth/guest', {
      method: 'POST',
      body: { nickname },
    });
    identities[index] = {
      index,
      nickname,
      userId: response.data.userId,
      token: response.data.accessToken,
    };
    completed += 1;
    printSetupProgress('게스트 인증', completed, config.users);
  });
  return identities;
}

async function createRoomsAndJoin(identities) {
  let completedRooms = 0;
  await parallelFor(roomCount, Math.min(config.setupConcurrency, 10), async (roomIndex) => {
    const firstIndex = roomIndex * config.roomSize;
    const members = identities.slice(firstIndex, firstIndex + config.roomSize);
    const host = members[0];
    const response = await api('/rooms', {
      method: 'POST',
      token: host.token,
      body: {
        mapId: config.mapId,
        title: `채팅 부하 ${runToken}-${roomIndex + 1}`.slice(0, 30),
        password: null,
        maxPlayers: config.roomSize,
        selectedQuestionCount: config.selectedQuestionCount,
        answerTimeLimitSeconds: 300,
        timeLimitMode: 'FIXED',
        audioRepeatEnabled: false,
        initialHintEnabled: false,
        initialHintTriggerSeconds: 10,
      },
    });
    const room = {
      roomId: response.data.roomId,
      hostToken: host.token,
      members,
    };
    rooms[roomIndex] = room;

    for (let offset = 0; offset < members.length - 1; offset += 1) {
      await api(`/rooms/${room.roomId}/join`, {
        method: 'POST',
        token: members[offset + 1].token,
        body: {},
      });
    }

    const detail = await api(`/rooms/${room.roomId}`);
    if (detail.data.memberCount !== members.length) {
      throw new Error(
        `방 인원 검증 실패: roomId=${room.roomId}, 기대=${members.length}, 실제=${detail.data.memberCount}`,
      );
    }

    members.forEach((member) => {
      member.roomId = room.roomId;
      member.roomSize = members.length;
    });
    completedRooms += 1;
    printSetupProgress('방 생성·입장', completedRooms, roomCount);
  });
}

async function startGameRooms() {
  if (gameRoomCount === 0) return;
  let completed = 0;
  await parallelFor(gameRoomCount, Math.min(config.setupConcurrency, 10), async (roomIndex) => {
    const room = rooms[roomIndex];
    await api(`/rooms/${room.roomId}/start`, {
      method: 'POST',
      token: room.hostToken,
    });
    room.playing = true;
    completed += 1;
    printSetupProgress('게임 시작', completed, gameRoomCount);
  });
  await sleep(2_000);
}

async function connectClients(identities) {
  let completed = 0;
  await parallelFor(identities.length, config.setupConcurrency, async (index) => {
    const identity = identities[index];
    const client = new StompClient({
      wsUrl: config.wsUrl,
      token: identity.token,
      roomId: identity.roomId,
      clientIndex: index,
      onEvent: recordEvent,
      onStompError: () => { metrics.stompErrors += 1; },
      onApplicationError: recordApplicationError,
      onWebSocketError: () => { metrics.websocketErrors += 1; },
      onClose: () => { metrics.disconnected += 1; },
    });
    clients[index] = client;
    await client.connect();
    metrics.connected += 1;
    completed += 1;
    printSetupProgress('WebSocket 연결', completed, config.users);
  });
  await sleep(2_000);
}

function recordApplicationError(error) {
  metrics.applicationErrors += 1;
  const code = typeof error?.message === 'string' ? error.message : 'unknown';
  metrics.applicationErrorCodes[code] = (metrics.applicationErrorCodes[code] ?? 0) + 1;
}

function startSender(client, clientIndex) {
  const initialDelayMs = Math.floor(Math.random() * config.intervalMs);
  let sequence = 0;
  let timeoutId;
  let intervalId;

  const send = () => {
    if (phase !== 'warmup' && phase !== 'measure') return;
    const sentAt = Date.now();
    const currentPhase = phase;
    const messageId = `cb:${runToken}:${currentPhase}:${clientIndex.toString(36)}:${sequence.toString(36)}:${sentAt}`;
    sequence += 1;
    if (currentPhase === 'measure') {
      sentMessages.set(messageId, { expected: client.roomSize, received: 0 });
      metrics.sent += 1;
    }
    client.sendChat(`load-${messageId}`, messageId);
  };

  timeoutId = setTimeout(() => {
    send();
    intervalId = setInterval(send, config.intervalMs);
  }, initialDelayMs);

  client.stopSending = () => {
    clearTimeout(timeoutId);
    clearInterval(intervalId);
  };
}

function recordEvent(event) {
  if (event?.type !== 'CHAT_MESSAGE' || typeof event.clientMessageId !== 'string') {
    metrics.unrelatedReceived += 1;
    return;
  }
  const sent = sentMessages.get(event.clientMessageId);
  if (!sent) return;

  sent.received += 1;
  metrics.received += 1;
  const sentAt = Number(event.clientMessageId.slice(event.clientMessageId.lastIndexOf(':') + 1));
  const latencyMs = Math.max(Date.now() - sentAt, 0);
  metrics.latencySumMs += latencyMs;
  metrics.latencyMaxMs = Math.max(metrics.latencyMaxMs, latencyMs);
  latencyHistogram[Math.min(latencyMs, latencyHistogram.length - 1)] += 1;
}

function buildResult() {
  let expectedDeliveries = 0;
  let missingDeliveries = 0;
  let duplicateDeliveries = 0;
  let fullyDeliveredMessages = 0;

  for (const sent of sentMessages.values()) {
    expectedDeliveries += sent.expected;
    missingDeliveries += Math.max(sent.expected - sent.received, 0);
    duplicateDeliveries += Math.max(sent.received - sent.expected, 0);
    if (sent.received === sent.expected) fullyDeliveredMessages += 1;
  }

  return {
    runId,
    startedAt: new Date(measurementStartedAt).toISOString(),
    completedAt: new Date(measurementCompletedAt || Date.now()).toISOString(),
    measurementStartedAtEpochMs: measurementStartedAt,
    measurementCompletedAtEpochMs: measurementCompletedAt,
    config: {
      ...config,
      roomCount,
      gameRoomCount,
      waitingRoomCount,
      resultDir: undefined,
    },
    connections: {
      requested: config.users,
      connected: metrics.connected,
      failed: config.users - metrics.connected,
      disconnectedDuringTest: metrics.disconnected,
    },
    messages: {
      sent: metrics.sent,
      fullyDeliveredMessages,
      receivedDeliveries: metrics.received,
      expectedDeliveries,
      missingDeliveries,
      duplicateDeliveries,
      deliverySuccessRatePercent: expectedDeliveries === 0
        ? null
        : round(((expectedDeliveries - missingDeliveries) / expectedDeliveries) * 100),
      sentPerSecond: round(metrics.sent / config.durationSeconds),
      receivedDeliveriesPerSecond: round(metrics.received / config.durationSeconds),
    },
    latencyMs: {
      average: metrics.received === 0 ? null : round(metrics.latencySumMs / metrics.received),
      p50: percentile(0.50),
      p95: percentile(0.95),
      p99: percentile(0.99),
      max: metrics.received === 0 ? null : metrics.latencyMaxMs,
    },
    errors: {
      stomp: metrics.stompErrors,
      websocket: metrics.websocketErrors,
      application: metrics.applicationErrors,
      applicationCodes: metrics.applicationErrorCodes,
      total: metrics.stompErrors + metrics.websocketErrors + metrics.applicationErrors,
    },
    loadGenerator: {
      peakRssMiB: round(peakRssBytes / 1024 / 1024),
      eventLoopDelayP95Ms: nanosecondsToMilliseconds(eventLoopDelay.percentile(95)),
      eventLoopDelayMaxMs: nanosecondsToMilliseconds(eventLoopDelay.max),
    },
  };
}

async function writeResult(result) {
  await writeFile(`${config.resultDir}/result.json`, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  const summary = `# Room chat benchmark ${result.runId}\n\n`
    + `- Users: ${result.config.users}\n`
    + `- Rooms: ${result.config.roomCount} (${result.config.roomSize} users per room)\n`
    + `- Room states: ${result.config.gameRoomCount} playing, ${result.config.waitingRoomCount} waiting\n`
    + `- Map: ${result.config.mapTitle} (ID=${result.config.mapId}, selected questions=${result.config.selectedQuestionCount})\n`
    + `- Message interval: ${result.config.intervalMs}ms per user\n`
    + `- Warmup: ${result.config.warmupSeconds}s\n`
    + `- Measurement: ${result.config.durationSeconds}s\n`
    + `- Connections: ${result.connections.connected}/${result.connections.requested}\n`
    + `- Sent messages: ${result.messages.sent}\n`
    + `- Expected deliveries: ${result.messages.expectedDeliveries}\n`
    + `- Received deliveries: ${result.messages.receivedDeliveries}\n`
    + `- Missing deliveries: ${result.messages.missingDeliveries}\n`
    + `- Duplicate deliveries: ${result.messages.duplicateDeliveries}\n`
    + `- Delivery success rate: ${result.messages.deliverySuccessRatePercent}%\n`
    + `- Throughput: sent=${result.messages.sentPerSecond}/s, received=${result.messages.receivedDeliveriesPerSecond}/s\n`
    + `- Latency: avg=${formatMs(result.latencyMs.average)}, p50=${formatMs(result.latencyMs.p50)}, p95=${formatMs(result.latencyMs.p95)}, p99=${formatMs(result.latencyMs.p99)}, max=${formatMs(result.latencyMs.max)}\n`
    + `- STOMP errors: ${result.errors.stomp}\n`
    + `- WebSocket errors: ${result.errors.websocket}\n`
    + `- Application errors: ${result.errors.application} ${JSON.stringify(result.errors.applicationCodes)}\n`
    + `- Unexpected disconnects: ${result.connections.disconnectedDuringTest}\n`
    + `- Other room events received: ${metrics.unrelatedReceived}\n`
    + `- Load generator: peak RSS=${result.loadGenerator.peakRssMiB}MiB, event-loop p95=${formatMs(result.loadGenerator.eventLoopDelayP95Ms)}, max=${formatMs(result.loadGenerator.eventLoopDelayMaxMs)}\n`;
  await writeFile(`${config.resultDir}/summary.md`, summary, 'utf8');
}

function printProgress() {
  const elapsed = phase === 'measure' ? Math.floor((Date.now() - measurementStartedAt) / 1000) : 0;
  console.log(
    `[${phase}] ${elapsed}s 연결=${metrics.connected - metrics.disconnected}/${config.users}`
    + ` 전송=${metrics.sent} 수신=${metrics.received} p95=${formatMs(percentile(0.95))}`
    + ` 오류=${metrics.stompErrors + metrics.websocketErrors + metrics.applicationErrors}`,
  );
}

function printFinalResult(result) {
  console.log('측정 완료');
  console.log(`전송 ${result.messages.sent}, 기대 수신 ${result.messages.expectedDeliveries}, 실제 수신 ${result.messages.receivedDeliveries}`);
  console.log(`누락 ${result.messages.missingDeliveries}, 중복 ${result.messages.duplicateDeliveries}`);
  console.log(`지연 p50=${formatMs(result.latencyMs.p50)}, p95=${formatMs(result.latencyMs.p95)}, p99=${formatMs(result.latencyMs.p99)}`);
}

async function cleanupRooms() {
  if (rooms.length === 0) return;
  console.log(`테스트 방 ${rooms.length}개를 정리합니다.`);
  const results = await Promise.allSettled(rooms.filter(Boolean).map(cleanupRoom));
  const failures = results.filter((result) => result.status === 'rejected');
  if (failures.length > 0) {
    process.exitCode = 1;
    const details = failures.map((result) => String(result.reason)).join('\n');
    console.error(`테스트 방 ${failures.length}개를 정리하지 못했습니다.`);
    await writeFile(`${config.resultDir}/cleanup-failures.txt`, `${details}\n`, 'utf8');
    try {
      await writeFile(
        `${config.resultDir}/summary.md`,
        `\n- Cleanup failures: ${failures.length} (see \`cleanup-failures.txt\`)\n`,
        { encoding: 'utf8', flag: 'a' },
      );
    } catch {
      // A setup failure can happen before summary.md exists; cleanup-failures.txt remains authoritative.
    }
  }
}

async function cleanupRoom(room) {
  if (!room.playing) {
    await api(`/rooms/${room.roomId}`, {
      method: 'DELETE',
      token: room.hostToken,
    });
    return;
  }

  for (const member of room.members) {
    await api(`/rooms/${room.roomId}/leave`, {
      method: 'POST',
      token: member.token,
      body: {},
    });
  }
}

async function api(path, { method = 'GET', token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch(`${config.baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30_000),
    });
  } catch (error) {
    throw new Error(`${method} ${path} request failed: ${error instanceof Error ? error.message : error}`, {
      cause: error,
    });
  }
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = null;
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} failed (${response.status}): ${payload?.message ?? text}`);
  }
  return payload;
}

class StompClient {
  constructor({ wsUrl, token, roomId, clientIndex, onEvent, onStompError, onApplicationError, onWebSocketError, onClose }) {
    this.wsUrl = wsUrl;
    this.token = token;
    this.roomId = roomId;
    this.roomSize = config.roomSize;
    this.clientIndex = clientIndex;
    this.onEvent = onEvent;
    this.onStompError = onStompError;
    this.onApplicationError = onApplicationError;
    this.onWebSocketError = onWebSocketError;
    this.onClose = onClose;
    this.buffer = '';
    this.connected = false;
    this.closedByClient = false;
    this.stopSending = () => {};
  }

  connect() {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error(`WebSocket connect timeout: client=${this.clientIndex}`)), 15_000);
      this.socket = new WebSocket(this.wsUrl);
      this.socket.addEventListener('open', () => {
        this.sendFrame('CONNECT', {
          'accept-version': '1.2',
          host: 'nomat',
          Authorization: `Bearer ${this.token}`,
          'heart-beat': '0,0',
        });
      });
      this.socket.addEventListener('message', (message) => this.handleData(String(message.data), resolve, reject, timeout));
      this.socket.addEventListener('error', () => {
        this.onWebSocketError();
        if (!this.connected) {
          clearTimeout(timeout);
          reject(new Error(`WebSocket connection failed: client=${this.clientIndex}`));
        }
      });
      this.socket.addEventListener('close', () => {
        if (!this.closedByClient) {
          this.onClose();
          if (!this.connected) {
            clearTimeout(timeout);
            reject(new Error(`WebSocket closed before CONNECTED: client=${this.clientIndex}`));
          }
        }
        this.connected = false;
        clearInterval(this.heartbeatTimer);
      });
    });
  }

  handleData(data, resolve, reject, timeout) {
    this.buffer += data;
    const frames = this.buffer.split('\0');
    this.buffer = frames.pop() ?? '';
    for (const rawFrame of frames) {
      const frame = parseFrame(rawFrame);
      if (!frame) continue;
      if (frame.command === 'CONNECTED') {
        this.connected = true;
        this.sendFrame('SUBSCRIBE', {
          id: `room-${this.clientIndex}`,
          destination: `/topic/rooms/${this.roomId}`,
          ack: 'auto',
          Authorization: `Bearer ${this.token}`,
        });
        this.sendFrame('SUBSCRIBE', {
          id: `errors-${this.clientIndex}`,
          destination: '/user/queue/errors',
          ack: 'auto',
          Authorization: `Bearer ${this.token}`,
        });
        this.startHeartbeat();
        clearTimeout(timeout);
        resolve();
      } else if (frame.command === 'MESSAGE') {
        try {
          const payload = JSON.parse(frame.body);
          if (frame.headers.subscription === `errors-${this.clientIndex}`
              || frame.headers.destination === '/user/queue/errors') {
            this.onApplicationError(payload);
          } else {
            this.onEvent(payload);
          }
        } catch {
          this.onStompError();
        }
      } else if (frame.command === 'ERROR') {
        this.onStompError();
        if (!this.connected) {
          clearTimeout(timeout);
          reject(new Error(`STOMP connection failed: client=${this.clientIndex}, body=${frame.body}`));
        }
      }
    }
  }

  sendChat(content, clientMessageId) {
    if (!this.connected || this.socket.readyState !== WebSocket.OPEN) return;
    this.sendFrame('SEND', {
      destination: `/app/rooms/${this.roomId}/messages`,
      Authorization: `Bearer ${this.token}`,
      'content-type': 'application/json',
    }, JSON.stringify({ content, clientMessageId }));
  }

  startHeartbeat() {
    const send = () => {
      if (!this.connected || this.socket.readyState !== WebSocket.OPEN) return;
      this.sendFrame('SEND', {
        destination: `/app/rooms/${this.roomId}/heartbeat`,
        Authorization: `Bearer ${this.token}`,
        'content-type': 'application/json',
      }, '{}');
    };
    send();
    this.heartbeatTimer = setInterval(send, 180_000);
    this.heartbeatTimer.unref();
  }

  sendFrame(command, headers, body = '') {
    const lines = [command];
    for (const [name, value] of Object.entries(headers)) lines.push(`${name}:${value}`);
    lines.push(`content-length:${Buffer.byteLength(body)}`, '', body);
    this.socket.send(`${lines.join('\n')}\0`);
  }

  close() {
    this.stopSending();
    clearInterval(this.heartbeatTimer);
    this.closedByClient = true;
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.sendFrame('DISCONNECT', {});
      this.socket.close(1000, 'benchmark-complete');
    }
  }
}

function parseFrame(rawFrame) {
  const normalized = rawFrame.replace(/^\n+/, '').replace(/\r\n/g, '\n');
  if (!normalized.trim()) return null;
  const separator = normalized.indexOf('\n\n');
  const head = separator >= 0 ? normalized.slice(0, separator) : normalized;
  const body = separator >= 0 ? normalized.slice(separator + 2) : '';
  const lines = head.split('\n');
  const command = lines.shift();
  const headers = {};
  for (const line of lines) {
    const colon = line.indexOf(':');
    if (colon > 0) headers[line.slice(0, colon)] = line.slice(colon + 1);
  }
  return { command, headers, body };
}

async function parallelFor(count, concurrency, task) {
  let next = 0;
  const workers = Array.from({ length: Math.min(count, concurrency) }, async () => {
    while (true) {
      const index = next;
      next += 1;
      if (index >= count) return;
      await task(index);
    }
  });
  await Promise.all(workers);
}

function percentile(ratio) {
  if (metrics.received === 0) return null;
  const target = Math.ceil(metrics.received * ratio);
  let total = 0;
  for (let index = 0; index < latencyHistogram.length; index += 1) {
    total += latencyHistogram[index];
    if (total >= target) return index === latencyHistogram.length - 1 ? 60_001 : index;
  }
  return null;
}

function printSetupProgress(label, completed, total) {
  if (completed === total || completed % Math.max(Math.floor(total / 10), 1) === 0) {
    console.log(`[준비] ${label}: ${completed}/${total}`);
  }
}

function parseArgs(values) {
  const parsed = {};
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index];
    if (value === '--help' || value === '-h') {
      parsed.help = true;
    } else if (value === '--self-check') {
      parsed.selfCheck = true;
    } else if (value.startsWith('--')) {
      parsed[value.slice(2)] = values[index + 1];
      index += 1;
    }
  }
  return parsed;
}

function integerArg(parsed, name, defaultValue) {
  const raw = parsed[name];
  if (raw === undefined) return defaultValue;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`--${name} must be a positive integer`);
  return value;
}

function validateConfig(value) {
  if (!value.users) throw new Error('--users is required');
  if (!value.resultDir) throw new Error('--result-dir is required');
  if (value.roomSize < 2 || value.roomSize > 12) throw new Error('--room-size must be between 2 and 12');
  if (value.users % value.roomSize !== 0) throw new Error('--users must be divisible by --room-size');
  const baseUrl = value.baseUrl.replace(/\/$/, '');
  const wsUrl = value.wsUrl ?? `${baseUrl.replace(/^http/, 'ws')}/ws`;
  return { ...value, baseUrl, wsUrl };
}

function printUsage() {
  console.log(`Usage:
  node scripts/room-chat-benchmark.mjs \\
    --users 100 --room-size 10 \\
    --interval-ms 2000 --warmup-seconds 120 --duration-seconds 600 \\
    --result-dir benchmark-results/example
`);
}

function round(value) {
  return Math.round(value * 100) / 100;
}

function formatMs(value) {
  return value === null ? 'n/a' : `${value}ms`;
}

function sampleLoadGeneratorResources() {
  peakRssBytes = Math.max(peakRssBytes, process.memoryUsage().rss);
}

function nanosecondsToMilliseconds(value) {
  if (!Number.isFinite(value) || value === 0) return null;
  return round(value / 1_000_000);
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

if (args.selfCheck) {
  const sentFrames = [];
  let resolved = false;
  let applicationErrorCode;
  const client = new StompClient({
    wsUrl: config.wsUrl,
    token: 'self-check',
    roomId: 1,
    clientIndex: 0,
    onEvent: () => {},
    onStompError: () => {},
    onApplicationError: (error) => { applicationErrorCode = error.message; },
    onWebSocketError: () => {},
    onClose: () => {},
  });
  client.socket = {
    readyState: WebSocket.OPEN,
    send: (frame) => { sentFrames.push(frame); },
    close: () => {},
  };
  client.handleData(
    'CONNECTED\nversion:1.2\n\n\0'
      + 'MESSAGE\nsubscription:errors-0\ndestination:/queue/errors-user123\n\n{"message":"self_check_error"}\0',
    () => { resolved = true; },
    (error) => { throw error; },
    undefined,
  );
  if (client.roomSize !== config.roomSize
      || !client.connected
      || !resolved
      || sentFrames.filter((frame) => frame.startsWith('SUBSCRIBE\n')).length !== 2
      || !sentFrames.some((frame) => frame.includes(`/app/rooms/${client.roomId}/heartbeat`))
      || applicationErrorCode !== 'self_check_error') {
    throw new Error('STOMP client self-check failed');
  }
  console.log('채팅 부하 생성기 자체 점검을 통과했습니다.');
  client.close();
} else {
  await runBenchmark();
}
