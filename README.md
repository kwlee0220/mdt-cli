# mdt-cli

Manufacturing DigitalTwin(MDT) 시스템을 제어하기 위한 커맨드라인 인터페이스(CLI)이다.
실행 중인 MDTManager에 HTTP로 접속하여 MDT 인스턴스, 서브모델, AAS 요소, 워크플로우,
시뮬레이션 등을 조회/등록/실행/제어한다.

`picocli` 기반의 단일 진입점([`mdt`](src/main/java/mdt/cli/MDTCommandsMain.java)) 명령으로,
여러 하위 명령 그룹(`list`, `get`, `add`, `set`, `remove`, `start`, `stop`, `run`, `resolve`,
`simulation` 등)을 제공한다.

## 요구 사항

- Java 21 (Gradle toolchain으로 강제됨)
- Gradle (시스템 또는 SDKMAN 설치본; 프로젝트에 wrapper 없음)
- 형제 프로젝트 `utils`(`../../common/utils`)와 `mdt-client`(`../mdt-client`) — `settings.gradle` 참조

## 빌드

```bash
# 의존 라이브러리를 모두 포함하는 fat jar 생성 → build/libs/mdt-cli-all.jar
gradle shadowJar

# 버전 문자열을 지정하여 빌드 (미지정 시 "unknown")
MDT_BUILD_VERSION=1.4.1 gradle shadowJar
```

## 실행

`MDT_CLIENT_HOME` 환경 변수를 배포 디렉토리로 지정한 뒤 [`sbin/mdt`](sbin/mdt) 스크립트로 실행한다.
스크립트는 `$MDT_CLIENT_HOME/mdt-cli-all.jar`를 구동한다.

```bash
export MDT_CLIENT_HOME=/path/to/deploy
sbin/mdt list instances
sbin/mdt get instance <id>
sbin/mdt --help
```

Windows에서는 [`sbin/mdt.bat`](sbin/mdt.bat)을 사용한다.

## 설정

MDTManager 접속 정보는 다음 우선순위로 결정된다.

1. `--client_conf <path>` 옵션으로 지정한 설정 파일
2. `$MDT_CLIENT_HOME/mdt_client_config.yaml`
3. 환경 변수 `MDT_URL` (endpoint URL)

설정 파일 예([`mdt_client_config.yaml`](mdt_client_config.yaml)):

```yaml
endpoint: "http://localhost:12985"
connectTimeout: "10s"
readTimeout: "30s"
workflowEndpoint: "http://localhost:12989"
```

## 테스트

```bash
gradle test
```

## 주요 명령 그룹

| 그룹 | 설명 |
|------|------|
| `list` | MDT 인스턴스/서브모델/셸/오퍼레이션/워크플로우 목록 조회 |
| `get` | 인스턴스, 요소, 파라미터, 파일, 시계열, 워크플로우 등 상세 조회 |
| `add` | MDT 인스턴스, 워크플로우 모델 등록 |
| `set` | 요소/파라미터/인자/파일 값 설정 |
| `remove` | 인스턴스, 파일, 워크플로우 모델/인스턴스 삭제 |
| `start` / `stop` | 인스턴스 및 워크플로우 기동/중지 |
| `run` | AAS 오퍼레이션, 서브모델 실행 |
| `resolve` | Reference를 URL로 해석 |
| `simulation` | SKKU 시뮬레이션 등 시뮬레이션 제어 |
| `manager` | MDTManager 종료 등 매니저 제어 |
