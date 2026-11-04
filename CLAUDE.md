# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`mdt-cli`는 Manufacturing DigitalTwin(MDT) 시스템의 커맨드라인 클라이언트이다. picocli 기반
단일 진입점 명령 `mdt`(`mdt.cli.MDTCommandsMain`)가 여러 하위 명령 그룹을 제공하며, 실행 중인
MDTManager에 HTTP로 접속하여 인스턴스/서브모델/AAS 요소/워크플로우/시뮬레이션을 제어한다.

소스 주석과 문서는 한국어로 작성한다(기존 코드 컨벤션).

## mdt CLI 명령 사용 (에이전트가 직접 `mdt`를 호출할 때)

Claude/에이전트가 `mdt` CLI 명령을 **직접 조립·실행**해야 할 때는 아래 가이드를 따른다. 참조식
(element-ref) 문법, 작업→명령 매핑, 출력 형식(파싱용 `-o json/value/csv`), 자주 틀리는 지점을
정리해 두었다. 사람용 전체 설명서는 [docs/commands.md](docs/commands.md)에 있다.

@docs/CLAUDE.md

## 빌드 / 실행 / 테스트

```bash
gradle shadowJar                          # fat jar 생성 → build/libs/mdt-cli-<버전>-all.jar
MDT_BUILD_VERSION=1.7.0 gradle shadowJar  # 버전 지정 빌드 (env 미지정 시 "unknown")
gradle test                               # JUnit 5 전체 테스트
gradle test --tests 'mdt.cli.PeriodicRefreshingConsoleTest'  # 단일 테스트 클래스
```

- Java 21 toolchain이 강제된다. Gradle wrapper는 없으므로 시스템/SDKMAN의 `gradle`을 사용한다.
- 빌드 버전(`MDT_BUILD_VERSION`)은 shadow jar manifest의 `Implementation-Version`으로 기록되고,
  `mdt -V`가 [`MDTVersionProvider`](src/main/java/mdt/cli/MDTVersionProvider.java)를 통해 이를 출력한다
  (jar가 아닌 클래스 디렉토리에서 실행하면 `unknown`).
- 실행은 [`sbin/mdt`](sbin/mdt)(`java -Dfile.encoding=UTF-8 -jar $MDT_CLIENT_HOME/mdt-cli-all.jar "$@"`)를
  통한다. `MDT_CLIENT_HOME`이 배포 디렉토리를 가리켜야 하며, 빌드 산출물은 배포 디렉토리에
  `mdt-cli-all.jar` 이름으로 복사한다.

## 멀티 프로젝트 의존성

이 디렉토리는 독립 git 저장소가 아니라 더 큰 워크스페이스의 일부이며, [`settings.gradle`](settings.gradle)에서
형제 프로젝트를 상대 경로로 포함한다.

- `:utils` → `../../common/utils` — `FOption`, `FStream`, `Picoclies`, `LogbackConfigLoader` 등 공통 유틸
- `:mdt-client` → `../mdt-client` — `HttpMDTManager`, `MDTClientConfig`, `MDTInstance` 등 MDT 도메인/HTTP 클라이언트

CLI 코드가 사용하는 도메인 모델·HTTP 통신 로직 대부분은 `:mdt-client`에 있다. CLI가 호출하는
타입의 동작을 이해하려면 그 프로젝트를 함께 읽어야 한다.

## 아키텍처

### 명령 계층 구조

- `MDTCommandsMain` — 루트 `mdt` 명령. `subcommands`에 최상위 그룹을 등록한다.
- 그룹 명령(`get`, `list`, `add`, `set`, `remove`, `start`, `stop`, `run`, `resolve` …)은
  [`CommandCollection`](src/main/java/mdt/cli/CommandCollection.java)을 상속한다. 자체 로직 없이
  인자 없이 호출되면 usage를 출력하고, `subcommands`로 실제 명령을 묶는 컨테이너 역할만 한다.
  새 하위 명령을 추가하려면 해당 그룹의 `@Command(subcommands={...})` 목록에도 등록해야 한다.
- 실제 동작 명령은 [`AbstractMDTCommand`](src/main/java/mdt/cli/AbstractMDTCommand.java)을 상속한다.

### AbstractMDTCommand 부트스트랩

`AbstractMDTCommand.run()`이 공통 절차를 처리한 뒤 서브클래스의 `run(MDTManager)`를 호출한다.
따라서 새 명령은 비즈니스 로직만 `run(MDTManager mdt)`에 구현하면 된다.

1. `--loglevel` 지정 시 `"mdt"` 루트 로거 레벨 변경
2. MDTManager 접속 — `connectMDTManager()`
3. 접속된 매니저를 인자로 `run(MDTManager)` 호출
4. 예외는 `RuntimeException`으로 감싸 전달, 종료 코드는 상위에서 결정

접속 설정 해석 우선순위: `--client-conf <path>` 옵션 → `$MDT_CLIENT_HOME/mdt_client_config.yaml`
(`MDT_CLIENT_HOME`이 없으면 현재 디렉토리) → 환경 변수 `MDT_URL`. 설정 파일이 있는데 파싱에 실패하면
`MDT_URL`로 넘어가지 않고 오류가 난다. 설정 키는 `MDTClientConfig`(`:mdt-client`)의 필드
(`mdtUrl`, `connectTimeout`, `readTimeout`, `mqttEndpoint`, `workflowManagerUrl`)와 정확히 일치해야 한다.

picocli `CommandLine` 구성(`main()` 및 루트)은 enum 대소문자 무시, 옵션/서브커맨드 약어 허용,
`Duration`/`Level` 컨버터 등록, usage 너비 110으로 통일되어 있다. 새 진입점에서도 동일 구성을 따른다.

### 출력 렌더링

명령은 보통 `--output`(CSV/TABLE/TREE/JSON) 옵션으로 출력 형식을 선택한다.

- TABLE: `text-table-formatter` (`org.nocrala.tools.texttablefmt.Table`)
- TREE: `text-tree` (`org.barfuin.texttree`). 트리 노드 구성 로직은 [`mdt.tree`](src/main/java/mdt/tree/)
  패키지(특히 `mdt.tree.node`)에 `*Node` / `NodeFactory`로 모듈화되어 있다.
- JSON: AAS4J `JsonSerializer`
- 주기 갱신(`--repeat`)은 [`PeriodicRefreshingConsole`](src/main/java/mdt/cli/PeriodicRefreshingConsole.java)
  (JLine 기반)이 담당한다. 단위 테스트는 TTY 없이 동작하도록 `performPeriodicAction`을 직접 호출한다.

### task 실행 명령 (`mdt run`)

[`mdt.cli.run`](src/main/java/mdt/cli/run/) 패키지가 `mdt run` 하위 명령(`operation`/`submodel`/`restful`/`set`)을
제공한다. 실제 task 구현(`AASOperationTask`, `OperationSubmodelTask`, `SetTask`)은 `:mdt-client`의
`mdt.task` 패키지에, `restful`이 사용하는 `RESTfulAsyncRpcClient`는 `:utils`의 `utils.rpc.restful`에 있다.

- [`DynamicArgumentsCommand`](src/main/java/mdt/cli/run/DynamicArgumentsCommand.java) — picocli `@Unmatched`로
  받은 `--in.<name>`/`--out.<name>`/`--inout.<name>`(종류 생략 시 input) 동적 인자를 task 인자로 해석한다.
  `--in.x 3`처럼 값을 분리한 형식은 값 토큰이 위치 인자로 소비되면 오류를 낸다.
- [`AbstractRunTaskCommand`](src/main/java/mdt/cli/run/AbstractRunTaskCommand.java) — `operation`/`submodel`의
  공통 기반. `--poll`/`--timeout`/`--show-result`([`OperationRunOptions`](src/main/java/mdt/cli/run/OperationRunOptions.java))를
  제공하고, 서브클래스가 `newTask()`로 만든 task를 실행한다.
- `restful`([`RESTfulAsyncRpcCommand`](src/main/java/mdt/cli/run/RESTfulAsyncRpcCommand.java))과
  `set`([`SetTaskCommand`](src/main/java/mdt/cli/run/SetTaskCommand.java))은 `DynamicArgumentsCommand`를 직접 상속한다.

[`mdt.task.skku`](src/main/java/mdt/task/skku/)의 `SKKUSimulationTaskCommand`는 어느 명령 그룹에도 등록되어 있지 않다.

## 패키징 규칙

`shadowJar`/`jar`는 의존 프로젝트의 `logback-test.xml`, `logback-spring.xml`을 제외하고 이 프로젝트의
`logback.xml`만 포함한다(`build.gradle` 참조). 런타임 로그 설정 변경 시 이 점을 유의한다.
로그 설정은 `$MDT_CLIENT_HOME`(없으면 현재 디렉토리)의 `logback.xml`을 먼저 쓰고, 없으면 기본값
`src/main/resources/logback.xml`(INFO 이상을 표준 에러로 출력)을 쓴다(`MDTCommandsMain.configureLogging()`).
