# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`mdt-cli`는 Manufacturing DigitalTwin(MDT) 시스템의 커맨드라인 클라이언트이다. picocli 기반
단일 진입점 명령 `mdt`(`mdt.cli.MDTCommandsMain`)가 여러 하위 명령 그룹을 제공하며, 실행 중인
MDTManager에 HTTP로 접속하여 인스턴스/서브모델/AAS 요소/워크플로우/시뮬레이션을 제어한다.

소스 주석과 문서는 한국어로 작성한다(기존 코드 컨벤션).

## 빌드 / 실행 / 테스트

```bash
gradle shadowJar                          # fat jar 생성 → build/libs/mdt-cli-all.jar
MDT_BUILD_VERSION=1.4.1 gradle shadowJar  # 버전 지정 빌드 (env 미지정 시 "unknown")
gradle test                               # JUnit 5 전체 테스트
gradle test --tests 'mdt.cli.PeriodicRefreshingConsoleTest'  # 단일 테스트 클래스
```

- Java 21 toolchain이 강제된다. Gradle wrapper는 없으므로 시스템/SDKMAN의 `gradle`을 사용한다.
- 실행은 [`sbin/mdt`](sbin/mdt)(`java -jar $MDT_CLIENT_HOME/mdt-cli-all.jar "$@"`)를 통한다.
  `MDT_CLIENT_HOME`이 배포 디렉토리를 가리켜야 한다.

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

접속 설정 해석 우선순위: `--client_conf <path>` 옵션 → `$MDT_CLIENT_HOME/mdt_client_config.yaml`
→ 환경 변수 `MDT_URL`.

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

### 워크플로우 task 명령

[`mdt.task.builtin`](src/main/java/mdt/task/builtin/) 패키지는 워크플로우에서 실행되는 빌트인 task
(HTTP / AASOperation / Set / Program)를 CLI 명령으로 노출한다. `AbstractTaskCommand`가 커맨드라인
인자로부터 입출력 argument와 `TaskDescriptor`를 구성한다.

## 패키징 규칙

`shadowJar`/`jar`는 의존 프로젝트의 `logback-test.xml`, `logback-spring.xml`을 제외하고 이 프로젝트의
`logback.xml`만 포함한다(`build.gradle` 참조). 런타임 로그 설정 변경 시 이 점을 유의한다.
