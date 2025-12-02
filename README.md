# 🚀 SPIRE Docker Compose Manual

### (Server + Agent + Java Workload – SVID 발급까지)

---

# 0. 전체 폴더 구조

```
spire-demo/
 ├── docker-compose.yml
 ├── spire/
 │    ├── server/
 │    │     └── server.conf
 │    ├── agent/
 │    │     ├── agent.conf
 │    │     └── bootstrap.crt  (옵션: insecure_bootstrap 사용 시 필요 X)
 │    └── data/
 │          ├── server/
 │          └── agent/
 └── app/
      ├── Dockerfile
      └── (Spring Boot 소스)
```

---

# 1. Docker Compose

**핵심 포인트**

- agent와 java 앱은 **같은 volume의 unix socket을 공유**해야 한다
- `pid: "host"`는 로컬 UID/GID 보존 및 unix attestor가 낮은 난이도로 작동하게 하는 핵심
- join_token은 매번 새로 발급해야 한다

```yaml
services:
  spire-server:
    image: ghcr.io/spiffe/spire-server:1.13.3
    container_name: spire-server
    command: ["-config", "/opt/spire/conf/server/server.conf"]
    volumes:
      - ./spire/server:/opt/spire/conf/server
      - ./spire/data/server:/opt/spire/data/server
    ports:
      - "8082:8082"
    networks:
      - spire-net

  spire-agent:
    image: ghcr.io/spiffe/spire-agent:1.13.3
    container_name: spire-agent
    depends_on:
      - spire-server
    pid: "host"
    command: [
      "-config", "/opt/spire/conf/agent/agent.conf",
      "-joinToken", "${JOIN_TOKEN}"
    ]
    volumes:
      - ./spire/agent:/opt/spire/conf/agent
      - ./spire/data/agent:/opt/spire/data/agent
      - spire-agent-socket:/tmp/spire-agent/public
    networks:
      - spire-net

  java-app:
    build:
      context: ./app
      dockerfile: Dockerfile
    container_name: java-app
    depends_on:
      - spire-agent
    pid: "host"
    environment:
      - SPIFFE_ENDPOINT_SOCKET=unix:///tmp/spire-agent/public/api.sock
    volumes:
      - spire-agent-socket:/tmp/spire-agent/public
    ports:
      - "8080:8080"
    networks:
      - spire-net

networks:
  spire-net:
    driver: bridge

volumes:
  spire-agent-socket:
```

---

# 2. 서버 설정 — `server.conf`

```hcl
server {
  bind_address = "0.0.0.0"
  bind_port    = "8082"
  socket_path  = "/tmp/spire-server/private/api.sock"

  trust_domain = "example.org"
  data_dir     = "/opt/spire/data/server"
  log_level    = "DEBUG"
}

plugins {
  DataStore "sql" {
    plugin_data {
      database_type      = "sqlite3"
      connection_string  = "/opt/spire/data/server/datastore.sqlite3"
    }
  }

  NodeAttestor "join_token" {
    plugin_data {}
  }

  KeyManager "memory" {
    plugin_data {}
  }
}
```

---

# 3. 에이전트 설정 — `agent.conf`

여기서 두 버전 제공:

---

## (A) **정식 운영 모드 — insecure_bootstrap 안 씀**

bootstrap.crt 필요함

(= 서버의 bundle을 agent에게 복사해둬야 함)

1. 먼저 서버 bundle 추출:

```bash
docker exec spire-server \
  /opt/spire/bin/spire-server bundle show > spire/agent/bootstrap.crt
```

이걸 agent.conf에서 trust_bundle_path로 읽는다.

```hcl
agent {
  data_dir = "/opt/spire/data/agent"
  log_level = "DEBUG"

  server_address = "spire-server"
  server_port    = "8082"

  socket_path = "/tmp/spire-agent/public/api.sock"

  trust_domain = "example.org"

  # 정식 모드
  trust_bundle_path = "/opt/spire/conf/agent/bootstrap.crt"
}

plugins {
  NodeAttestor "join_token" {
    plugin_data {}
  }

  KeyManager "disk" {
    plugin_data {
      directory = "/opt/spire/data/agent"
    }
  }

  WorkloadAttestor "unix" {
    plugin_data {}
  }
}
```

✔️ 장점

- 실서비스 구성
- 중간자 공격 방지

✔️ 단점

- bootstrap.crt 파일 공유 필요

---

## (B) **로컬 개발 모드 — insecure_bootstrap = true**

부트스트랩 시 첫 연결에서 서버의 cert를 "그냥 신뢰"

bootstrap.crt 필요 없음.

```hcl
agent {
  data_dir = "/opt/spire/data/agent"
  log_level = "DEBUG"

  server_address = "spire-server"
  server_port    = "8082"

  socket_path = "/tmp/spire-agent/public/api.sock"

  trust_domain = "example.org"

  # 로컬 개발 편의 모드
  insecure_bootstrap = true
}

plugins {
  NodeAttestor "join_token" { plugin_data {} }

  KeyManager "disk" {
    plugin_data { directory = "/opt/spire/data/agent" }
  }

  WorkloadAttestor "unix" { plugin_data {} }
}
```

✔️ 장점

- 설정 파일만 있으면 바로 띄움
- bootstrap.crt 관리 없음

✔️ 단점

- 실제 서비스에 절대 사용하면 안 됨

---

# 전체 실행
```bash
 scripts/init-spire.sh
```

---

# 4. 필요시 서버 개별 실행

```bash
docker compose up -d spire-server
docker logs -f spire-server
```

서버가 기동되면 CA 생성 로그가 보임.

---

# 5. Join Token 발급

```bash
docker exec spire-server \
  /opt/spire/bin/spire-server token generate \
    -spiffeID spiffe://example.org/host/spire-agent-1 \
    -ttl 60000
```

출력되는 토큰 값을 `.env`에 넣어 둔다.

```
JOIN_TOKEN=abcdefg12312312
```

---

# 6. 필요시 에이전트 개별 실행

```bash
docker compose up -d spire-agent
docker logs -f spire-agent
```

성공하면:

```
SVID is not found. Starting node attestation
Node attestation completed
X509 SVID obtained!
```

---

# 7. 에이전트 등록 확인

```bash
docker exec spire-server \
  /opt/spire/bin/spire-server agent list
```

결과 예:

```
SPIFFE ID: spiffe://example.org/host/spire-agent-1
SVID Expires At: 2025-12-02T...
Attestation type: join_token
```

---

# 8. Workload Entry 생성 (가장 중요)

java-app은 컨테이너 PID namespace를 host로 써서 UID=0으로 보이므로

selector는 `unix:uid:0` 기준으로 등록.

```bash
docker exec spire-server \
  /opt/spire/bin/spire-server entry create \
    -parentID spiffe://example.org/host/spire-agent-1 \
    -spiffeID spiffe://example.org/workload/java-app \
    -selector unix:uid:0
```

성공 출력 예:

```
Entry ID: 89xxxxx
SPIFFE ID: spiffe://example.org/workload/java-app
Parent ID: spiffe://example.org/host/spire-agent-1
Selectors: unix:uid:0
```

---

# 9. Java Application 실행

```bash
docker compose up -d java-app
docker logs -f java-app
```

---

# 10. Java에서 SPIFFE Workload API 호출

예시 코드:

```java
WorkloadApiClient client = DefaultWorkloadApiClient.newBuilder()
    .spiffeSocket("unix:///tmp/spire-agent/public/api.sock")
    .build();

X509Context x509Context = client.fetchX509Context();
```

---

# 11. 정상 동작 결과

java-app 로그:

```
SPIFFE_ENDPOINT_SOCKET = unix:///tmp/spire-agent/public/api.sock
Workload API에서 X509Context를 가져오는 중...
발급 받은 SPIFFE ID = spiffe://example.org/workload/java-app
```

agent 로그:

```
PID attested... selectors [unix:uid:0]
Issued new SVID for workload spiffe://example.org/workload/java-app
```

Demo Code

https://github.com/cling2/spire
