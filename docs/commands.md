# mdt 명령어 사용 설명서

Manufacturing DigitalTwin(MDT) 시스템을 제어하는 커맨드라인 도구 `mdt`의 사용 설명서이다. 각 명령의 형식, 인자, 옵션, 동작, 출력, 오류 조건을 설명한다. 명령의 내장 도움말은 `mdt <명령> ... -h`로 볼 수 있으며, 이 문서의 형식(Usage) 줄은 그 도움말과 같다.

## 목차

1. [설치와 실행](#1-설치와-실행)
2. [MDTManager 접속 설정](#2-mdtmanager-접속-설정)
3. [공통 규칙](#3-공통-규칙) — 공통 옵션, 값 표기, 출력과 종료 코드, 반복 출력
4. [명령 전체 구조](#4-명령-전체-구조)
5. [명령별 설명](#5-명령별-설명) — `endpoints`, `build`, `list`, `get`, `add`, `set`, `remove`, `start`, `stop`,
   `suspend`/`resume`, `run`, `resolve`, `simulation`
6. [참조식과 값 리터럴 문법](#6-참조식과-값-리터럴-문법)
7. [자주 겪는 오류](#7-자주-겪는-오류)
8. [부록: 등록되지 않은 명령](#8-부록-등록되지-않은-명령)

---

## 1. 설치와 실행

```bash
export MDT_CLIENT_HOME=/path/to/deploy      # 배포 디렉토리 (mdt-cli-all.jar 위치)
$MDT_CLIENT_HOME/sbin/mdt <명령> [하위명령] [인자...] [옵션...]
```

- 리눅스/맥: [`sbin/mdt`](../sbin/mdt) — `java -Dfile.encoding=UTF-8 -jar $MDT_CLIENT_HOME/mdt-cli-all.jar "$@"`
- 윈도우: [`sbin/mdt.bat`](../sbin/mdt.bat)
- Java 21 이상이 필요하다.
- 빌드: `gradle shadowJar`의 산출물 `build/libs/mdt-cli-<버전>-all.jar`를 배포 디렉토리에 `mdt-cli-all.jar` 이름으로 복사한다. 버전은 빌드 시 환경 변수 `MDT_BUILD_VERSION`으로 정하며(미지정 시 `unknown`), `mdt -V`로 확인한다.
- 명령 자동 완성(bash): [`sbin/mdt_completion`](../sbin/mdt_completion)을 `.bash_profile`에서 `source` 한다.
  명령·옵션 이름과 함께, `MDT_URL`(필요하면 `MDT_WORKFLOW_ENDPOINT`)이 설정되어 있으면 인스턴스 id, 파라미터, 오퍼레이션, 워크플로우 이름 같은 값도 완성한다.

이 문서에서는 `sbin/mdt`를 `mdt`로 줄여 쓴다.

---

## 2. MDTManager 접속 설정

`endpoints`와 `build`를 제외한 모든 명령은 실행 중인 MDTManager에 HTTP로 접속한다. 접속 설정은 다음 순서로 찾으며, 먼저 찾은 것 하나만 쓴다.

1. `--client-conf <path>` 옵션으로 지정한 설정 파일.
   하위 명령에 지정하지 않았으면 가장 가까운 상위 명령에 지정한 값을 쓴다
   (예: `mdt get instance line-01 --client-conf conf.yaml parameters`).
2. `$MDT_CLIENT_HOME/mdt_client_config.yaml` (`MDT_CLIENT_HOME`이 없으면 현재 디렉토리의 같은 파일).
3. 환경 변수 `MDT_URL` (MDTManager URL). 이때 읽기 타임아웃은 30초다.

어디에도 없으면 `error: IllegalArgumentException: MDTManager endpoint is not configured: ...`를 출력하고 종료 코드 1로 끝난다. 2번 파일이 있는데 형식이 잘못되었으면 3번으로 넘어가지 않고 오류로 끝난다.

설정 파일 예:

```yaml
mdtUrl: "${env:MDT_URL:-http://localhost:12985}"
connectTimeout: "10s"
readTimeout: "30s"
workflowManagerUrl: "${env:MDT_WORKFLOW_ENDPOINT:-http://localhost:12989}"
# mqttEndpoint: "tcp://localhost:1883"
```

| 키 | 필수 | 설명 |
|----|------|------|
| `mdtUrl` | O | MDTManager URL |
| `connectTimeout` | | HTTP 접속 타임아웃 (`"10s"` 형식) |
| `readTimeout` | | HTTP 응답 읽기 타임아웃 |
| `workflowManagerUrl` | | 워크플로우 매니저 URL. 워크플로우 명령에 필요 |
| `mqttEndpoint` | | MQTT endpoint |

- 값에 `${env:VAR}`, `${env:VAR:-기본값}`, `${sys:prop}` 형식으로 환경 변수·시스템 속성을 넣을 수 있다.
- 위 다섯 키 외의 키가 있으면 설정 파일을 읽지 못하고 오류가 난다. 예전 키 `endpoint`, `workflowEndpoint`는 각각 `mdtUrl`, `workflowManagerUrl`로 바꾼다.
- 현재 적용되는 설정은 [`mdt endpoints`](#mdt-endpoints)로 확인한다.

---

## 3. 공통 규칙

### 3.1 공통 옵션

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--client-conf` | `<path>` | MDTManager 접속 설정 파일 (§2). `build`에는 없다 |
| `--loglevel` | `<level>` | `mdt` 로거의 레벨: `debug`, `info`, `warn`, `error`. 하위 명령에 없으면 상위 명령의 값을 쓴다 |
| `-h`, `--help` | | 사용법 출력 |
| `-V`, `--version` | | 버전 출력 |

명령별 옵션 표에서는 위 공통 옵션을 생략한다. 그룹 명령(`list`, `get` 등)을 하위 명령 없이 실행하면 사용법을 출력한다.

### 3.2 입력 편의 기능

- **enum 값 대소문자 무시** — `-o json`과 `-o JSON`은 같다.
- **옵션 약어** — 모호하지 않으면 `--time 30s`가 `--timeout 30s`로 해석된다.
- **하위 명령 약어** — `mdt li inst`가 `mdt list instances`로 해석된다.
  스크립트에서는 나중에 명령이 추가되어 모호해질 수 있으므로 전체 이름을 쓴다.
- **옵션 값 표기** — `--timeout 30s`와 `--timeout=30s` 모두 된다.
- **예전 옵션 이름** — 다음 이름도 계속 받는다(도움말에는 나오지 않는다):
  `--client_conf`, `--jsonFile`, `--mimeType`, `--showResult`/`--showResults`, `--baseUrl`, `--use_endpoint`.

### 3.3 값 표기

| 종류 | 형식 | 예 |
|------|------|----|
| 기간(duration) | 숫자 + `ms`/`s`/`m`/`h`/`d`, 또는 ISO-8601 | `500ms`, `30s`, `5m`, `PT1H30M` |
| 시각(datetime) | `YYYY-MM-DD[Thh:mm[:ss[.fff]]]` + 선택적 `Z` 또는 `±hh:mm`. 타임존이 없으면 로컬 시각 | `2026-07-01T09:00:00Z` |
| 와일드카드·공백 | 셸이 해석하지 않도록 작은따옴표로 감싼다 | `'param:line-01:*'`, `-g 'line-*'` |

### 3.4 출력과 종료 코드

- 명령 결과는 **표준 출력**, 오류·진행 메시지·소요 시간(`-v`)·로그는 **표준 에러**로 나온다. 따라서
  `mdt list instances -o json > out.json`처럼 결과만 파일로 받을 수 있다.
- 종료 코드:

| 코드 | 의미 |
|------|------|
| `0` | 성공 |
| `1` | 실행 실패. 서버 오류·접속 실패 같은 예외가 났거나, 여러 대상 중 일부라도 실패한 경우(예: `stop instance a b`에서 `b` 정지 실패) |
| `2` | 사용법 오류. 필수 인자 누락, 함께 쓸 수 없는 옵션 조합, 잘못된 옵션 값 등. 메시지와 사용법을 출력한다 |

- 실패 메시지는 한 줄로 출력한다.
  - 자원 오류는 예외 이름과 대상 자원을 출력한다.
    - `ResourceNotFoundException: id=MDTInstance(id=CNC-04)`: 해당 자원이 없다.
    - `ResourceAlreadyExistsException: id=MDTInstance(id=CNC-04)`: 같은 식별자의 자원이 이미 있다.
    - `InvalidResourceStatusException: id=MDTInstance(id=CNC-04), status=STOPPED`: 자원이 요청을 처리할 수 없는 상태다.
  - 그 밖의 오류는 `error: <예외 이름>: <메시지>`로 출력한다.
- 원인을 자세히 보려면 `--loglevel debug`를 붙이면 스택 트레이스까지 출력한다. 사용자 입력 오류가 아닌 예외(내부 오류)는 항상 스택 트레이스를 출력한다.

### 3.5 출력 형식

| 형식 | 용도 | 비고 |
|------|------|------|
| `table` | 사람이 보는 표 | |
| `tree` | 사람이 보는 계층 구조 | |
| `csv` | 스크립트 처리 | 헤더 행 없음(`get timeseries`는 `--header`로 추가). 값에 구분자·`"`·줄바꿈이 있으면 큰따옴표로 감싸고 `"`는 `""`로 적는다(RFC 4180) |
| `json` | 스크립트 처리 | |
| `value` | 값 하나만 필요할 때 | 장식 없는 값. 값이 없으면 `(none)` |

### 3.6 반복 출력 (`-r`, `--repeat`)

`list` 명령, `get mdt-info`, `get element/parameter/argument/timeseries`는 `-r <interval>`을 주면 화면을 지우고
주기적으로 다시 출력한다.

- `q`, `Q` 또는 `ESC` 키를 누르면 끝난다(종료 코드 0).
- 반복 중 조회가 실패하면 그 회차 화면에 `failed to ...: cause=...`를 표시하고 계속 반복한다. 반복하지 않는 실행에서 조회가 실패하면 종료 코드 1로 끝난다.
- `-v`를 함께 주면 소요 시간도 표시한다.

### 3.7 로그

로그는 표준 에러로 출력한다. 로그 설정은 `$MDT_CLIENT_HOME/logback.xml`(없으면 현재 디렉토리의 `logback.xml`)을 먼저 쓰고, 없으면 내장 기본값(INFO 이상)을 쓴다. 명령 한 번만 자세히 보려면 `--loglevel debug`를 쓴다.

---

## 4. 명령 전체 구조

```
mdt
├── endpoints                                 접속 설정 출력
├── build <instance-id> <dir>                 MDTInstance 배포용 zip 생성
├── list                                      목록 조회
│   ├── instances                             MDTInstance
│   ├── shells                                AssetAdministrationShell
│   ├── submodels                             Submodel
│   ├── operations                            Operation(AI/Simulation) submodel
│   ├── wfmodels                              워크플로우 모델
│   └── workflows                             워크플로우 인스턴스
├── get                                       상세 조회
│   ├── instance <instance-id>                MDTInstance (하위: submodels, parameters, operations, compositions, log)
│   ├── mdt-info <instance-id>                MDT 정보 모델
│   ├── element (--ref <element-ref> | <instance-id> <submodel-idShort> <idShortPath>)
│   ├── parameter <instance-id> [parameter-id]
│   ├── argument <instance-id> <operation-id> <argument-id>
│   ├── shell <aas-id|instance-id>
│   ├── submodel <instance-id|submodel-id> [submodel-idShort]
│   ├── file <element-ref>                    File 요소의 첨부 파일 내려받기
│   ├── timeseries (ts) <instance-id> <submodel-idShort>
│   ├── wfmodel <model-id>                    워크플로우 모델 (하위: script)
│   └── workflow <workflow-name>              워크플로우 인스턴스 (하위: log)
├── add
│   ├── instance <instance-id> <path>         MDTInstance 등록
│   └── wfmodel <path>                        워크플로우 모델 등록
├── set
│   ├── element <instance-id> <submodel-idShort> <idShortPath>
│   ├── parameter <instance-id> [parameter-id]
│   ├── argument <instance-id> <operation-id> <argument-id>
│   └── file <element-ref> <path>             File 요소에 파일 올리기
├── remove
│   ├── instance [instance-id...]             MDTInstance 삭제
│   ├── file <element-ref>                    File 요소의 첨부 파일 삭제
│   ├── wfmodel [model-id...]                 워크플로우 모델 삭제
│   └── workflow [workflow-name...]           워크플로우 인스턴스 삭제
├── start
│   ├── instance [instance-id...]             MDTInstance 기동
│   └── workflow <model-id>                   워크플로우 실행
├── stop
│   ├── instance [instance-id...]             MDTInstance 중지
│   └── workflow <workflow-name>              워크플로우 중지
├── suspend workflow <workflow-name>          워크플로우 일시 정지
├── resume workflow <workflow-name>           워크플로우 재개
├── run                                       태스크 실행
│   ├── operation <instance-id> <submodel-idShort> <idShortPath>
│   ├── submodel <instance-id> <submodel-idShort>
│   ├── restful                               RESTful 비동기 RPC
│   └── set                                   값 복사(set task)
├── resolve reference <element-ref>           참조식을 URL로 변환
└── simulation run <instance-id|submodel-id>  시뮬레이션 실행
```

위치 인자 이름의 뜻:

| 이름 | 뜻 |
|------|----|
| `instance-id` | MDTInstance 식별자 |
| `submodel-id` | Submodel의 전역 id (예: `https://example.com/sm/Data`) |
| `submodel-idShort` | 인스턴스 안에서 Submodel을 가리키는 idShort (예: `Data`) |
| `idShortPath` | Submodel 안 SubmodelElement 경로 (예: `DataInfo.temperature`, `records[0].value`) |
| `aas-id` | AssetAdministrationShell의 id |
| `parameter-id`, `operation-id`, `argument-id` | MDT 파라미터, 오퍼레이션(AI/Simulation), 오퍼레이션 인자의 id |
| `model-id` | 워크플로우 모델 id |
| `workflow-name` | 워크플로우 인스턴스 이름 (`mdt list workflows`의 NAME) |
| `element-ref` | 참조식 (§6) |

---

## 5. 명령별 설명

### `mdt endpoints`

```
mdt endpoints [-hV] [--client-conf=path] [--loglevel=logger-level]
```

현재 적용되는 접속 설정(§2)을 출력한다. MDTManager에 접속하지 않는다.

```bash
$ mdt endpoints
- mdt-url: http://localhost:12985
  . connect-timeout: PT10S
  . read-timeout: PT30S
- workflow-endpoint: http://localhost:12989
- mqtt-endpoint: null
```

설정을 찾지 못하면 `Cannot get MDTManager's endpoint`를 표준 에러로 출력한다.

### `mdt build`

```
mdt build [-hV] [--loglevel=logger-level] instance-id dir
```

MDTInstance 구현 디렉토리를 묶어 현재 디렉토리에 `<instance-id>.zip`을 만든다. MDTManager에 접속하지 않는다.
만든 zip은 `mdt add instance`로 등록한다.

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id` | 인스턴스 id. zip 파일 이름이 된다 |
| `dir` | MDTInstance 구현 디렉토리. 디렉토리가 아니면 오류 |

```bash
$ mdt build line-01 ./twins/line-01
MDTInstance zip file created: /home/user/line-01.zip
```

---

### `mdt list` — 목록 조회

목록 명령의 CSV 출력은 표(`table`)와 같은 열을 같은 순서로 출력하며 헤더 행은 없다. 첫 열 `#`은 1부터 시작하는 순번이다. 기본 구분자는 `|`이며 `-d`로 바꾼다.

**인스턴스 필터식(`-f`)** — `list instances/shells/submodels/operations`가 받는다. MDTManager가 JPQL `WHERE` 절로
사용하므로 속성은 `instance.<속성>` 형식으로 쓰고 문자열은 작은따옴표로 감싼다(`=`, `!=`, `like`, `and`, `or` 등).

| 접두어 | 속성 |
|--------|------|
| `instance.` | `instanceId`, `status`(`STOPPED`, `RUNNING` 등), `baseEndpoint`, `aasId`, `aasIdShort`, `globalAssetId`, `assetType` |
| `submodel.` | `id`, `idShort`, `semanticId` (쓰면 인스턴스의 submodel과 조인한다) |
| `parameter.` | `id`, `name`, `valueType` (쓰면 인스턴스의 파라미터와 조인한다) |

#### `mdt list instances`

```
mdt list instances [-hltvV] [--pretty] [--show-endpoint] [--client-conf=path] [-d=delimiter]
                   [-f=filter-expr] [--loglevel=logger-level] [-o=type] [-r=interval]
```

MDTInstance 목록을 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `csv` | `csv`, `table`, `tree`, `json` |
| `-t`, `--table` | | | `-o table`과 같다. `-o`보다 우선한다 |
| `-l`, `--long` | | | 자세한 열을 출력한다 (`csv`, `table`에만 적용) |
| `-f`, `--filter` | `<expr>` | | 인스턴스 필터식 |
| `-d`, `--delimiter` | `<delim>` | `\|` | CSV 구분자 |
| `--pretty` | | | JSON을 들여쓰기 해서 출력한다 |
| `--show-endpoint` | | | 실행 중인 인스턴스의 endpoint를 함께 표시한다 (`tree`에만 적용) |
| `-r`, `--repeat` | `<interval>` | | 반복 출력 (§3.6) |
| `-v` | | | 소요 시간을 표준 에러로 출력한다 |

출력 열:

| 형식 | 열 |
|------|----|
| 기본 | `#`, `INSTANCE`, `STATUS` |
| `-l` | `#`, `INSTANCE`, `ASSET_TYPE`, `ASSET_ID`, `SUB_MODELS`, `STATUS`, `ENDPOINT` |
| `tree` | 인스턴스와 하위 구성(composition) 인스턴스의 계층 |

```bash
mdt list instances
mdt list instances -t -l
mdt list instances -o tree --show-endpoint
mdt list instances -f "instance.instanceId like 'line%'" -o json --pretty
mdt list instances -f "instance.status = 'RUNNING'"
mdt list instances -r 2s
```

#### `mdt list shells` / `submodels` / `operations`

```
mdt list shells     [-htvV] [--client-conf=path] [-d=delimiter] [-f=filter-expr] [--loglevel=logger-level]
                    [-o=type] [-r=interval]
mdt list submodels  (옵션 같음)
mdt list operations (옵션 같음)
```

각각 AssetAdministrationShell, Submodel, Operation(AI/Simulation) submodel 목록을 출력한다. `-f`를 주면 조건에
맞는 인스턴스의 항목만 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `csv` | `csv`, `table` |
| `-t`, `--table` | | | `-o table`과 같다 |
| `-f`, `--filter` | `<expr>` | | 인스턴스 필터식 |
| `-d`, `--delimiter` | `<delim>` | `\|` | CSV 구분자 |
| `-r`, `--repeat` | `<interval>` | | 반복 출력 |
| `-v` | | | 소요 시간 출력 |

| 명령 | 열 |
|------|----|
| `shells` | `#`, `ID`, `ID_SHORT`, `GLOBAL_ASSET_ID`, `ASSET_TYPE`, `SUBMODELS` |
| `submodels` | `#`, `MDT_ID`, `ID`, `ID_SHORT`, `SEMANTIC_ID`, `ENDPOINT` |
| `operations` | `#`, `MDT`, `OPERATION`, `TYPE`, `INPUTS`, `OUTPUTS` |

`submodels`의 `SEMANTIC_ID`는 MDT가 아는 semantic ID(Data, InformationModel, AI, Simulation, TimeSeries 등)는
짧은 이름으로, 그 밖의 것은 원래 값 그대로 표시한다.

```bash
mdt list shells -t
mdt list submodels -f "instance.status = 'RUNNING'" -d ,
mdt list operations -t
```

#### `mdt list wfmodels` / `workflows`

```
mdt list wfmodels  [-hltvV] [--client-conf=path] [-d=delimiter] [-g=expr] [--loglevel=logger-level]
                   [-o=type] [-r=interval]
mdt list workflows (옵션 같음)
```

각각 워크플로우 모델, 워크플로우 인스턴스 목록을 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `csv` | `csv`, `table` |
| `-t`, `--table` | | | `-o table`과 같다 |
| `-g`, `--glob` | `<expr>` | | 모델 id(`wfmodels`) 또는 워크플로우 이름(`workflows`)에 대한 glob 패턴 |
| `-l`, `--long` | | | 자세한 열 |
| `-d`, `--delimiter` | `<delim>` | `\|` | CSV 구분자 |
| `-r`, `--repeat` | `<interval>` | | 반복 출력 |
| `-v` | | | 소요 시간 출력 |

| 명령 | 기본 열 | `-l` 열 |
|------|---------|---------|
| `wfmodels` | `#`, `ID`, `COUNT`(태스크 수) | `#`, `ID`, `NAME`, `COUNT` |
| `workflows` | `#`, `NAME`, `MODEL`, `COUNT`, `STATUS` | `#`, `ID`, `MODEL`, `STATUS`, `COUNT`, `TASKS`(태스크별 상태) |

```bash
mdt list wfmodels -t
mdt list workflows -g 'line-*' -l -t
```

---

### `mdt get` — 상세 조회

#### `mdt get instance`

```
mdt get instance [-hV] [--client-conf=path] [--loglevel=logger-level] [-o=type] instance-id [COMMAND]
```

MDTInstance 정보를 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`: 주요 필드 표 (INSTANCE, AAS_ID, ID_SHORT, GLOBAL_ASSET_ID, ASSET_TYPE, ASSET_KIND, 서브모델, STATUS, ENDPOINT)<br>`json`: 인스턴스 descriptor JSON<br>`env`: AAS Environment JSON (Shell과 모든 Submodel 전체) |

하위 명령 — 상위 명령의 `instance-id`를 대상으로 한다. `log`를 제외하고 `-o tree|json`(기본 `tree`)과 `-v`
(소요 시간을 표준 에러로 출력)를 지원한다.

| 하위 명령 | 출력 |
|-----------|------|
| `submodels` | 인스턴스의 Submodel 목록 |
| `parameters` | 파라미터 목록과 현재 값. 값을 읽지 못한 파라미터는 `error` 항목에 원인을 표시한다 |
| `operations` | 오퍼레이션(AI/Simulation)과 입출력 인자 |
| `compositions` | Twin-Composition(구성 인스턴스 관계) |
| `log` | 인스턴스 프로세스의 출력 로그. 옵션 없음 |

```bash
mdt get instance line-01
mdt get instance line-01 -o env > line-01-env.json
mdt get instance line-01 parameters
mdt get instance line-01 operations -o json
mdt get instance line-01 log
```

#### `mdt get mdt-info`

```
mdt get mdt-info [-chiopvV] [--client-conf=path] [--loglevel=logger-level] [-r=interval] instance-id
```

인스턴스의 MDT 정보 모델(InformationModel)을 트리로 출력한다. 표시할 항목을 플래그로 고르며, 하나도 주지 않으면
파라미터만 표시한다.

| 옵션 | 설명 |
|------|------|
| `-i`, `--info` | MDTInfo |
| `-p`, `--parameters` | 파라미터 (Data submodel이 없으면 오류) |
| `-o`, `--operations` | 오퍼레이션(AI/Simulation). **출력 형식 옵션이 아니다** |
| `-c`, `--compositions` | 구성(composition) |
| `-r`, `--repeat` `<interval>` | 반복 출력 |
| `-v`, `--verbose` | 소요 시간 출력 |

```bash
mdt get mdt-info line-01 -i -p
mdt get mdt-info line-01 -c -r 3s
```

#### `mdt get element`

```
mdt get element [-hvV] [--client-conf=path] [--loglevel=logger-level] [-o=type] [-r=interval]
                (--ref=ref_expr | [instance-id submodel-idShort idShortPath])
```

SubmodelElement 하나를 출력한다. 대상은 `--ref` 참조식 또는 위치 인자 세 개 중 **하나**로 지정한다. 위치 인자는
`<instance-id>:<submodel-idShort>:<idShortPath>` 참조식으로 합쳐 해석하므로 `*` 같은 와일드카드는 쓸 수 없다.
`--ref`에는 `param:`, `oparg:`, `timeseries:` 등 모든 참조식(§6.1)을 쓸 수 있다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--ref` | `<ref_expr>` | | 대상 참조식 |
| `-o`, `--output` | `<type>` | `tree` | `tree`, `json`(SubmodelElement JSON), `value`(값만) |
| `-r`, `--repeat` | `<interval>` | | 반복 출력 |
| `-v` | | | 소요 시간 출력 |

```bash
mdt get element line-01 Data DataInfo.temperature
mdt get element line-01 Data DataInfo -o json
mdt get element --ref param:line-01:Temperature -o value
mdt get element --ref 'param:line-01:*' -o json          # 파라미터 전체
mdt get element --ref 'timeseries:line-01:Log#last=5'    # 시계열 최근 5건
```

#### `mdt get parameter`

```
mdt get parameter [-hvV] [--client-conf=path] [--loglevel=logger-level] [-o=type] [-r=interval]
                  instance-id [parameter-id]
```

MDT 파라미터를 출력한다. `--ref param:<instance-id>:<parameter-id>`와 같다.

| 위치 인자 | 기본값 | 설명 |
|-----------|--------|------|
| `instance-id` | | 인스턴스 id |
| `parameter-id` | `*` | 파라미터 id. 생략하거나 `*`이면 전체 파라미터 |

옵션은 `get element`와 같다(`-o tree|json|value`, `-r`, `-v`).

```bash
mdt get parameter line-01
mdt get parameter line-01 Temperature -o value
mdt get parameter line-01 Temperature -r 1s
```

#### `mdt get argument`

```
mdt get argument [-hvV] [--client-conf=path] [--kind=in|out] [--loglevel=logger-level] [-o=type]
                 [-r=interval] instance-id operation-id argument-id
```

오퍼레이션(AI/Simulation)의 입력 또는 출력 인자를 출력한다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--kind` | `in`\|`out` | 입력 인자와 출력 인자가 같은 id를 가질 때만 필요하다. 없이 실행하면 그런 경우 사용법 오류로 알려 준다 |
| `-o`, `-r`, `-v` | | `get element`와 같다 |

없는 오퍼레이션 id나 인자 id를 주면 오류(종료 코드 1)로 끝난다.

```bash
mdt get argument line-01 Predict input1 -o value
mdt get argument line-01 Predict result --kind out -o json
```

#### `mdt get shell`

```
mdt get shell [-hV] [--client-conf=path] [--loglevel=logger-level] [-o=type] aas-id|instance-id
```

AssetAdministrationShell을 출력한다. 인자는 AAS id, AAS idShort, 인스턴스 id 순서로 찾아본다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`: 주요 필드(ID, ID_SHORT, GLOBAL_ASSET_ID, INSTANCE, DISPLAY_NAME, ENDPOINT 등)<br>`json`: AAS JSON<br>`desc`: AAS descriptor JSON |

```bash
mdt get shell line-01
mdt get shell line-01 -o desc
```

#### `mdt get submodel`

```
mdt get submodel [-hV] [--client-conf=path] [--loglevel=logger-level] [-o=type]
                 instance-id|submodel-id [submodel-idShort]
```

Submodel을 출력한다. 대상 지정 방법은 둘이다.

- `<instance-id> <submodel-idShort>` — 인스턴스 안의 Submodel.
- `<submodel-id>` — Submodel 전역 id 하나.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`: ID, ID_SHORT, SEMANTIC_ID, ENDPOINT<br>`json`: Submodel 전체 JSON<br>`desc`: Submodel descriptor JSON |

```bash
mdt get submodel line-01 Data
mdt get submodel https://example.com/sm/Data -o json
```

#### `mdt get file`

```
mdt get file [-hvV] [--client-conf=path] [-f=path] [--loglevel=logger-level] element-ref
```

File SubmodelElement의 첨부 파일을 내려받아 로컬 파일로 저장한다. 내용은 표준 출력으로 내보내지 않는다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `-f`, `--file` | `<path>` | 저장할 경로. 생략하면 File 요소의 `value`(서버 경로)의 파일 이름으로 현재 디렉토리에 저장한다. `value`가 비어 있으면 오류 |
| `-v` | | 저장한 파일의 절대 경로를 출력한다 |

```bash
mdt get file param:line-01:Manual -f ./manual.pdf
mdt get file line-01:Data:Documents.Manual -v
```

#### `mdt get timeseries` (별칭 `ts`)

```
mdt get timeseries [-hvV] [--header] [--client-conf=path] [--columns=columns] [--count=count]
                   [-d=delimiter] [--end=datetime] [--last=period(@anchor)?] [--loglevel=logger-level]
                   [-o=type] [-r=interval] [--start=datetime] instance-id submodel-idShort
```

TimeSeries submodel의 레코드를 출력한다. 결과는 범위 지정 방식과 상관없이 **항상 시각 오름차순**이다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--last` | `<N>` 또는 `<dur>[@anchor]` | | 마지막 N건, 또는 마지막 기간. `anchor`는 `latest`(기본, 최신 데이터 시각) 또는 `now`(현재 시각) |
| `--start` | `<datetime>` | | 시작 시각(포함) |
| `--end` | `<datetime>` | | 종료 시각(포함) |
| `--count` | `<N>` | | `--start`부터 시각 오름차순으로 N건 |
| `--columns` | `<col,...>` | 전체 | 출력할 열 |
| `-o`, `--output` | `<type>` | `tree` | `csv`, `table`, `tree`, `json`, `value` |
| `--header` | | | CSV 첫 줄에 열 이름을 출력한다 |
| `-d`, `--delim` | `<delim>` | `,` | CSV 구분자 (다른 명령의 `--delimiter`와 이름이 다르다) |
| `-r`, `--repeat` | `<interval>` | | 반복 출력 |
| `-v` | | | 소요 시간 출력 |

범위 옵션 조합:

| 조합 | 의미 |
|------|------|
| (없음) | 전체 레코드 |
| `--last 10` | 마지막 10건 |
| `--last 1h`, `--last 1h@now` | 최신 데이터(또는 현재) 시각 기준 마지막 1시간 |
| `--start T`, `--end T`, 둘 다 | 절대 시간 범위 (한쪽만 주면 그쪽만 제한) |
| `--start T --count N` | T부터 N건 |

다음 조합은 사용법 오류다: `--last`와 `--start`/`--end`/`--count`, `--start` 없는 `--count`, `--count`와
`--end`, 음수 `--count`, 건수 `--last`에 `@anchor`.

```bash
mdt ts line-01 Log --last 1h -o csv --header
mdt get timeseries line-01 Log --start 2026-07-01T00:00:00 --end 2026-07-02T00:00:00 -o table
mdt get timeseries line-01 Log --start 2026-07-01T09:00:00Z --count 100 -o csv --header --columns time,power
mdt get timeseries line-01 Log --start 1970-01-01T00:00:00Z --count 1 -o csv   # 가장 오래된 1건
```

#### `mdt get wfmodel`

```
mdt get wfmodel [-hV] [--client-conf=path] [--loglevel=logger-level] [-o=type] model-id [COMMAND]
```

워크플로우 모델을 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`: ID, NAME, DESCRIPTION, 태스크 시그니처 목록<br>`json`: 모델 JSON (`add wfmodel`에 그대로 쓸 수 있다) |

하위 명령 `script` — 서버가 모델로부터 생성한 워크플로우 스크립트를 출력한다.

```
mdt get wfmodel <model-id> script [--mdt-url=url] [--mdt-client-docker=image-id]
```

`--mdt-url`, `--mdt-client-docker`는 받기는 하지만 현재 사용되지 않는다.

```bash
mdt get wfmodel line-flow -o json > line-flow.json
mdt get wfmodel line-flow script
```

#### `mdt get workflow`

```
mdt get workflow [-hV] [--client-conf=path] [--loglevel=logger-level] [-o=type] workflow-name [COMMAND]
```

워크플로우 인스턴스를 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`: NAME, MODEL, STATUS, CREATION_TIME, START_TIME, FINISH_TIME, 태스크별 상태<br>`json`: 워크플로우 JSON |

하위 명령 `log` — 워크플로우를 실행한 Pod의 로그를 출력한다.

```
mdt get workflow <workflow-name> log --pod=<name>
```

| 옵션 | 필수 | 설명 |
|------|------|------|
| `--pod` | O | 로그를 읽을 Pod 이름 |

```bash
mdt get workflow line-flow-x7k2p -o json
mdt get workflow line-flow-x7k2p log --pod line-flow-x7k2p-1234567890
```

---

### `mdt add` — 등록

#### `mdt add instance`

```
mdt add instance [-hvV] [--client-conf=path] [--loglevel=logger-level] [-p=number] instance-id path
```

MDTInstance를 MDTManager에 등록한다. 등록만 하며 기동하지 않는다(`mdt start instance`로 기동).

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id` | 등록할 인스턴스 id |
| `path` | MDTInstance 구현 디렉토리 또는 zip 파일 (`mdt build`로 생성) |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-p`, `--port` | `<number>` | `-1` | 인스턴스 포트. `-1`이면 자동 할당 |
| `-v` | | | 성공하면 `Added MDTInstance: id=...`를 출력한다 |

같은 id가 이미 있으면 `MDTInstance already exists: id=...`를, 그 밖의 실패는 `Failed to add MDTInstance: ...`를
표준 에러로 출력하고 종료 코드 1로 끝난다.

```bash
mdt add instance line-01 ./line-01.zip
mdt add instance line-01 ./twins/line-01 -p 13000 -v
```

#### `mdt add wfmodel`

```
mdt add wfmodel [-fhV] [--client-conf=path] [--loglevel=logger-level] path
```

워크플로우 모델 JSON 파일을 등록한다.

| 옵션 | 설명 |
|------|------|
| `-f`, `--force` | 같은 id의 모델이 있으면 교체한다. 교체가 실패하면 서버가 기존 모델을 유지하거나 되돌린다 |

다음은 사용법 오류(종료 코드 2)다: 파일이 없거나 읽을 수 없음, JSON 해석 실패, `-f` 없이 같은 id의 모델이 이미
있음.

```bash
mdt add wfmodel ./workflows/line-flow.json
mdt add wfmodel ./workflows/line-flow.json -f
```

---

### `mdt set` — 값 쓰기

`element`, `parameter`, `argument`는 값 소스를 다음 중 **정확히 하나** 지정한다.

| 값 소스 | 설명 |
|---------|------|
| `--json <json>` | 값만 담은 JSON 문자열. Property는 `25.3`, `true`, `"\"abc\""`처럼, SubmodelElementCollection은 `{"Field": 1}`처럼 쓴다 |
| `--json-file <path>` | 값 JSON을 담은 파일. `-`이면 표준 입력에서 읽는다 |
| `--file <path> [--path <value>] [--mime-type <type>]` | 대상이 File 요소일 때 파일을 올린다. `--path`는 File 요소에 기록할 경로(기본: 파일 이름), `--mime-type`은 콘텐츠 타입(기본: 파일 내용으로 추정) |

성공하면 아무것도 출력하지 않는다.

#### `mdt set element`

```
mdt set element [-hV] [--client-conf=path] [--loglevel=logger-level] (값 소스)
                instance-id submodel-idShort idShortPath
```

SubmodelElement 값을 쓴다. 세 위치 인자 모두 필수다.

```bash
mdt set element line-01 Data DataInfo.temperature --json '25.3'
mdt set element line-01 Data DataInfo.status --json '"RUNNING"'
mdt set element line-01 Data DataInfo --json-file ./data-info.json
cat value.json | mdt set element line-01 Data DataInfo --json-file -
```

#### `mdt set parameter`

```
mdt set parameter [-hV] [--client-conf=path] [--loglevel=logger-level] (값 소스) instance-id [parameter-id]
```

MDT 파라미터 값을 쓴다. `parameter-id`를 생략하거나 `*`이면 파라미터 전체(`param:<instance-id>:*`)가 대상이다.

```bash
mdt set parameter line-01 Temperature --json '25.3'
mdt set parameter line-01 Manual --file ./manual.pdf --mime-type application/pdf
```

#### `mdt set argument`

```
mdt set argument [-hV] [--client-conf=path] [--kind=in|out] [--loglevel=logger-level] (값 소스)
                 instance-id operation-id argument-id
```

오퍼레이션(AI/Simulation) 인자 값을 쓴다. `--kind`는 [`get argument`](#mdt-get-argument)와 같다.

```bash
mdt set argument line-01 Predict input1 --json '3.5'
mdt set argument line-01 Predict input1 --json-file ./in1.json
```

#### `mdt set file`

```
mdt set file [-hV] [--client-conf=path] [--loglevel=logger-level] [--mime-type=type] [--path=value]
             element-ref path
```

참조식이 가리키는 File 요소에 파일을 올린다. 다른 `set` 명령과 달리 값 소스 옵션 대신 위치 인자로 파일을 받는다.
대상이 File 요소가 아니면 오류다.

| 옵션 | 설명 |
|------|------|
| `--path` | File 요소에 기록할 경로 (기본: 파일 이름) |
| `--mime-type` | 콘텐츠 타입 (기본: 파일 내용으로 추정) |

```bash
mdt set file param:line-01:Manual ./manual.pdf --mime-type application/pdf
mdt set file line-01:Data:Documents.Manual ./manual.pdf
```

---

### `mdt remove` — 삭제

#### `mdt remove instance`

```
mdt remove instance [-afhvV] [--client-conf=path] [--loglevel=logger-level] [-n=count] [instance-id...]
```

MDTInstance 등록을 해제한다. 대상은 id 목록과 `--all` 중 하나로 지정한다(둘 다 주거나 둘 다 없으면 사용법 오류).

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-a`, `--all` | | | 모든 인스턴스 |
| `-f`, `--force` | | | 실행 중인 인스턴스를 먼저 중지하고 삭제한다. 없으면 중지하지 않고 바로 삭제를 요청한다 |
| `-n`, `--nthreads` | `<count>` | `8` | 동시에 처리할 인스턴스 수 (1 이상) |
| `-v` | | | 받기는 하지만 현재 사용되지 않는다 |

- 일부 인스턴스의 중지·삭제가 실패하면 원인을 표준 에러로 출력하고 나머지는 계속 처리한 뒤 종료 코드 1로 끝난다.
- `--all --force`에서 실행 중인 인스턴스 하나라도 중지에 실패하면 **아무것도 삭제하지 않고** 종료 코드 1로 끝난다.

```bash
mdt remove instance line-01 line-02
mdt remove instance line-01 -f
mdt remove instance --all --force
```

#### `mdt remove file`

```
mdt remove file [-hvV] [--client-conf=path] [--loglevel=logger-level] element-ref
```

참조식이 가리키는 File 요소의 첨부 파일을 삭제한다. `-v`는 받기는 하지만 현재 사용되지 않는다.

```bash
mdt remove file param:line-01:Manual
```

#### `mdt remove wfmodel`

```
mdt remove wfmodel [-ahV] [--client-conf=path] [-g=expr] [--loglevel=logger-level] [model-id...]
```

워크플로우 모델을 삭제한다. 대상은 id 목록, `-g`, `-a` 중 **정확히 하나**로 지정한다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `-g`, `--glob` | `<expr>` | 모델 id glob 패턴 |
| `-a`, `--all` | | 모든 모델 |

```bash
mdt remove wfmodel line-flow
mdt remove wfmodel -g 'test-*'
```

#### `mdt remove workflow`

```
mdt remove workflow [-ahV] [--client-conf=path] [-g=expr] [--loglevel=logger-level] [--model-filter=model-id]
                    [workflow-name...]
```

워크플로우 인스턴스를 삭제한다. 대상은 이름 목록, `-a`, 필터(`-g`, `--model-filter`) 중 **정확히 하나**로
지정한다. `-g`와 `--model-filter`를 함께 주면 둘 다 만족하는 워크플로우만 삭제한다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `-g`, `--glob` | `<expr>` | 워크플로우 이름 glob 패턴 |
| `--model-filter` | `<model-id>` | 이 모델로 실행된 워크플로우 |
| `-a`, `--all` | | 모든 워크플로우 |

```bash
mdt remove workflow line-flow-x7k2p
mdt remove workflow --model-filter line-flow
mdt remove workflow -g 'line-flow-*' --model-filter line-flow
```

---

### `mdt start` — 기동

#### `mdt start instance`

```
mdt start instance [-ahrV] [--nowait] [--client-conf=path] [--loglevel=logger-level] [-n=count]
                   [--poll=duration] [--timeout=duration] [instance-id...]
```

MDTInstance를 기동한다. 대상은 id 목록과 `--all` 중 하나로 지정한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-a`, `--all` | | | 실행 중이 아닌 모든 인스턴스 |
| `-r`, `--recursive` | | | 지정한 인스턴스가 RUNNING이 되면, 그 인스턴스의 구성(composition) 인스턴스도 차례로 기동한다. id 목록이 필요하며 `--all`과 함께 쓸 수 없다 |
| `--nowait` | | | 기동 요청만 보내고 RUNNING이 될 때까지 기다리지 않는다 |
| `--poll` | `<duration>` | `1s` | 상태 확인 주기 |
| `--timeout` | `<duration>` | 없음 | 기동 대기 제한 시간 |
| `-n`, `--nthreads` | `<count>` | `1` | 동시에 기동할 인스턴스 수 |

진행 상황을 `MDTInstance starting: <id>`, `MDTInstance started: <id>`로 출력한다. 없는 id, 기동 실패,
시간 초과는 `No such MDTInstance: ...`, `MDTInstance failed to start: ...`를 표준 에러로 출력하고 나머지는
계속 처리한 뒤 종료 코드 1로 끝난다.

```bash
mdt start instance line-01
mdt start instance line-01 -r --timeout 2m
mdt start instance --all -n 4
```

#### `mdt start workflow`

```
mdt start workflow [-hV] [--client-conf=path] [--loglevel=logger-level] model-id
```

워크플로우 모델로 워크플로우를 실행하고, 만들어진 워크플로우의 이름·모델·상태를 출력한다. 모델이 없으면 사용법
오류다.

```bash
$ mdt start workflow line-flow
Started workflow:
  name:   line-flow-x7k2p
  model:  line-flow
  status: RUNNING
```

---

### `mdt stop` — 중지

#### `mdt stop instance`

```
mdt stop instance [-ahrV] [--nowait] [--client-conf=path] [--loglevel=logger-level] [-n=count]
                  [--poll=duration] [--timeout=duration] [instance-id...]
```

실행 중인 MDTInstance를 중지한다. 대상은 id 목록과 `--all` 중 하나로 지정한다. 실행 중이 아닌 인스턴스는
건너뛴다(성공으로 본다).

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-a`, `--all` | | | 실행 중인 모든 인스턴스 |
| `-r`, `--recursive` | | | 지정한 인스턴스를 중지하기 전에, 그 인스턴스의 구성 인스턴스부터 의존 순서대로 중지한다. id 목록이 필요하며 `--all`과 함께 쓸 수 없다 |
| `--nowait` | | | 중지 요청만 보내고 STOPPED가 될 때까지 기다리지 않는다 |
| `--poll` | `<duration>` | `1s` | 상태 확인 주기 |
| `--timeout` | `<duration>` | 없음 | 중지 대기 제한 시간 |
| `-n`, `--nthreads` | `<count>` | `1` | 동시에 중지할 인스턴스 수 |

성공 판정: 기다리는 경우 최종 상태가 `STOPPED`여야 하고, `--nowait`이면 `STOPPING` 또는 `STOPPED`면 된다.
조회·중지에 실패한 인스턴스는 `Failed to stop MDTInstance: id=..., status=..., cause=...`를 표준 에러로 출력하고,
나머지를 처리한 뒤 종료 코드 1로 끝난다.

```bash
mdt stop instance line-01
mdt stop instance line-01 -r
mdt stop instance --all --nowait
```

#### `mdt stop workflow`

```
mdt stop workflow [-hV] [--client-conf=path] [--loglevel=logger-level] workflow-name
```

실행 중인 워크플로우를 중지한다. 성공하면 아무것도 출력하지 않는다.

```bash
mdt stop workflow line-flow-x7k2p
```

---

### `mdt suspend` / `mdt resume` — 일시 정지와 재개

```
mdt suspend workflow [-hV] [--client-conf=path] [--loglevel=logger-level] workflow-name
mdt resume workflow  [-hV] [--client-conf=path] [--loglevel=logger-level] workflow-name
```

워크플로우를 일시 정지하거나 재개하고, 갱신된 워크플로우 정보를 출력한다.

```bash
mdt suspend workflow line-flow-x7k2p
mdt resume workflow line-flow-x7k2p
```

---

### `mdt run` — 태스크 실행

`run` 명령은 태스크 인자를 **동적 인자**로 받는다.

| 동적 인자 | 의미 | 값 |
|-----------|------|----|
| `--in.<name> <expr>` | 입력 인자. `--<name> <expr>`도 입력으로 본다 | 참조식 또는 값 리터럴 (§6) |
| `--out.<name> <ref>` | 출력 인자 — 실행 결과를 기록할 위치 | 참조식만 |
| `--inout.<name> <ref>` | 입출력 인자 | 참조식만 |

- 값은 `--in.x 3` 또는 `--in.x=3`으로 준다.
- **동적 인자는 위치 인자 뒤에 쓴다.** 위치 인자 앞에 `--in.x 3`을 쓰면 `3`이 위치 인자로 해석되어 오류가 난다.
  순서를 바꿀 수 없으면 `--in.x=3`을 쓴다.
- `-`로 시작하는 값(음수 등)은 `--in.x=-3`처럼 `=` 형식으로 준다.
- 값이 없는 동적 인자, 같은 인자를 두 번 지정하는 것은 사용법 오류다.
- `operation`, `submodel`, `set`은 대상이 선언하지 않은 인자 이름을 주면 오류를 내며, 선언된 이름 목록을 함께
  보여 준다. `restful`은 이름을 검사하지 않는다.

`operation`, `submodel`, `restful` 공통 옵션 (`set`에는 없다):

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--poll` | `<duration>` | `1s` | 상태 확인 주기 |
| `--timeout` | `<duration>` | 없음 | 실행 제한 시간. 넘으면 실패 |
| `--show-result` | | | 실행 후 출력·입출력 인자 값을 `<name>: <value>` 형식으로 출력한다 |

입력 값 해석 순서:

| 명령 | 해석 순서 |
|------|-----------|
| `operation`, `submodel` | ① 참조식이면 참조 → ② JSON이면 대상 인자의 타입에 맞춘 값 → ③ 대상 인자가 Property면 따옴표 없는 값을 그 데이터 타입으로 해석(맞지 않으면 오류) |
| `restful` | ① 참조식이면 참조 대상의 값(File이면 참조 자체) → ② JSON 리터럴 → ③ 그 외는 문자열 |
| `set` | ① 참조식이면 참조 → ② 값 리터럴(§6.2) → ③ 둘 다 아니면 오류 |

#### `mdt run operation`

```
mdt run operation [-hV] [--show-result] [--client-conf=path] [--loglevel=logger-level]
                  [--poll=duration] [--timeout=duration] instance-id submodel-idShort idShortPath
                  [--in.<name> <expr>]... [--out.<name> <ref>]... [--inout.<name> <ref>]...
```

AAS Operation 요소를 실행한다. 세 위치 인자 모두 필수다. 인자 이름은 Operation 요소의 변수 idShort다.

```bash
mdt run operation line-01 Ops Predict --in.x 3 --in.y 4 --out.z param:line-01:Result --show-result
```

#### `mdt run submodel`

```
mdt run submodel [-hV] [--show-result] [--client-conf=path] [--loglevel=logger-level]
                 [--poll=duration] [--timeout=duration] instance-id submodel-idShort
                 [--in.<name> <expr>]... [--out.<name> <ref>]...
```

AI/Simulation Submodel을 실행한다. 인자 이름은 Submodel에 선언된 입출력 인자 id다. `--inout.*`는 지원하지 않는다.

```bash
mdt run submodel line-01 Predict --in.input1 param:line-01:Temperature --out.out1 param:line-01:Pred --show-result
mdt run submodel line-01 Simulation --in.Duration=PT10M --timeout 30m
```

#### `mdt run restful`

```
mdt run restful [-hV] [--show-result] --base-url=baseURL --endpoint=endpoint [--client-conf=path]
                [--loglevel=logger-level] [--poll=duration] [--timeout=duration]
                [--in.<name> <expr>]... [--out.<name> <ref>]...
```

외부 서버의 연산을 RESTful 비동기 RPC로 실행한다. 첫 요청으로 연산을 시작한 뒤, 끝날 때까지 상태를 확인한다.
끝나면 응답의 출력 값을 `--out.*`이 가리키는 요소에 기록한다.

| 옵션 | 필수 | 설명 |
|------|------|------|
| `--base-url` | O | 서버 base URL |
| `--endpoint` | O | 연산 endpoint (base URL 뒤에 붙는 경로) |

- `--inout.*`는 지원하지 않는다.
- `--out.<name>`이 가리키는 요소의 현재 값을 요청에 함께 보낸다(File이면 참조 자체). 응답 값에 `@type`이 없으면
  이 값의 타입으로 해석해 기록한다.
- 요청한 출력이 응답에 없거나 출력 값을 기록하지 못하면 원인을 표준 에러로 출력하고 종료 코드 1로 끝난다.
  요청하지 않은 출력이 응답에 있으면 경고 로그만 남긴다.
- 서버가 연산 실패나 취소를 알리면 종료 코드 1로 끝난다.

```bash
mdt run restful --base-url http://localhost:9000 --endpoint /operations/predict \
    --in.x param:line-01:Temperature --out.y param:line-01:Pred --show-result
```

#### `mdt run set`

```
mdt run set [-hV] [--client-conf=path] [--loglevel=logger-level] --in.source <expr> --out.target <ref>
```

`source` 값을 `target` 위치에 기록한다. 두 동적 인자가 모두 필요하며, 다른 이름이나 `--inout.*`는 오류다.

- `source`는 요소 참조식이나 값 리터럴이어야 한다. 따옴표 없는 단어·숫자·불리언(`hello`, `25.3`, `true`)은 값으로
  읽는다.
- Submodel 참조(`line-01:Data`), 빈 문자열, 따옴표 없는 날짜·기간(`PT5S`)처럼 둘 다 아닌 입력은 오류다. 문자열로
  넘기려면 따옴표로 감싼다: `--in.source "'PT5S'"`.

```bash
mdt run set --in.source 25.3 --out.target param:line-01:Temperature
mdt run set --in.source param:line-01:Pred --out.target line-01:Data:DataInfo.predicted
```

---

### `mdt resolve reference`

```
mdt resolve reference [-hV] [--client-conf=path] [--loglevel=logger-level] element-ref
```

참조식이 가리키는 요소를 직접 읽고 쓸 수 있는 REST URL로 바꿔 출력한다.

```bash
mdt resolve reference param:line-01:Temperature
```

---

### `mdt simulation run`

```
mdt simulation run [-ehvV] [--nowait] [--client-conf=path] [--loglevel=logger-level] [--poll=duration]
                   [-t=duration] instance-id|submodel-id
```

Simulation submodel이 가리키는 시뮬레이터로 시뮬레이션을 실행한다.

- 인자가 Simulation submodel의 id면 그 Submodel을 쓴다. Simulation이 아닌 Submodel이면 오류다.
- 인자가 인스턴스 id면 그 인스턴스의 Simulation submodel을 쓴다. 여러 개면 첫 번째 것을 쓴다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-e`, `--use-endpoint` | | | Submodel id 대신 Submodel 서비스 endpoint로 시뮬레이터를 호출한다 |
| `--nowait` | | | 시작 요청만 보내고 끝날 때까지 기다리지 않는다 |
| `--poll` | `<duration>` | `3s` | 상태 확인 주기 |
| `-t`, `--timeout` | `<duration>` | 없음 | 제한 시간. 넘으면 시뮬레이션을 취소하고 실패로 끝난다 |
| `-v` | | | 기다리는 동안 상태 확인마다 `.`을 출력한다 |

끝나면 `Simulation completes: id=...`를 출력한다. 실패·취소·시간 초과는 종료 코드 1이다. `--nowait`이면 시작 응답
(`Starting a simulation: id=..., status=...`)만 출력하고 끝난다.

```bash
mdt simulation run line-01 -t 5m
mdt simulation run https://example.com/sm/Simulation -e --poll 5s
```

---

## 6. 참조식과 값 리터럴 문법

### 6.1 참조식 (`element-ref`)

`--ref`, `get/set/remove file`의 `element-ref`, `run`의 `--in`/`--out`/`--inout` 값(참조일 때),
`resolve reference`가 이 문법을 쓴다. 문법 정의는
[`MdtExpr.g4`](../../mdt-client/src/main/antlr/MdtExpr.g4)에 있다.

Submodel 지정:

| 형식 | 의미 | 예 |
|------|------|----|
| `<inst>:<smIdShort>` | 인스턴스의 Submodel (idShort) | `line-01:Data` |
| `<inst>:idShort=<smIdShort>` | 위와 같음 (명시형) | `line-01:idShort=Data` |
| `submodel:id=<submodelId>` | 전역 Submodel id | `submodel:id=https://example.com/sm/Data` |

참조식 종류:

| 접두어 | 형식 | 의미 | 예 |
|--------|------|------|----|
| (없음) | `<submodel지정>:<idShortPath>` | SubmodelElement | `line-01:Data:DataInfo.temperature` |
| `param:` | `param:<inst>:<id \| 번호 \| *>` | MDT 파라미터 (`*`는 전체) | `param:line-01:Temperature` |
| `oparg:` | `oparg:<submodel지정>:<in\|out>:<이름 \| 번호 \| *>` | AI/Simulation 오퍼레이션 인자 | `oparg:line-01:Simulation:in:0` |
| `opvar:` | `opvar:<Operation 요소 참조>:<in\|out\|inout>:<번호>` | AAS Operation 요소의 변수 | `opvar:line-01:Ops:Predict:out:1` |
| `timeseries:` | `timeseries:<submodel지정>[#범위][\|열,...]` | 시계열 레코드 | `timeseries:line-01:Log#last=5\|time,power` |

- `idShortPath`는 이름을 `.`으로 잇고 리스트 원소는 `[번호]`로 가리킨다: `records[0].value`.
- 인스턴스 지정 앞에 `mdt:`를 붙여도 된다: `mdt:line-01:Data:DataInfo.temperature`.

시계열 범위(`#`)와 열(`|`):

| 조각 | 의미 | 예 |
|------|------|----|
| `#last=<N>` | 마지막 N건 | `#last=10` |
| `#last=<dur>[@latest\|@now]` | 마지막 기간 | `#last=1h`, `#last=30m@now` |
| `#<from>~<to>` | 절대 범위 (한쪽 생략 가능) | `#2026-07-01~2026-07-02`, `#~2026-07-02` |
| `#<from>+<N>` | from부터 N건 | `#2026-07-01T09:00:00Z+100` |
| `\|a,b` | 열 선택 | `\|time,power` |

### 6.2 값 리터럴

`run --in.<name>`의 값과 `run set`의 `source`에 쓸 수 있다. `set ... --json`에는 리터럴이 아닌 JSON을 쓴다.

| 종류 | 형식 | 예 |
|------|------|----|
| Property | 숫자, 불리언, 따옴표 없는 단어, 따옴표 문자열 | `25.3`, `-7`, `true`, `abc`, `"공백 포함"` |
| 다국어 텍스트 | `"<text>"@<lang>` | `"온도"@ko` |
| File | `file:"<path>"("<mime-type>")` | `file:"/data/a.pdf"("application/pdf")` |
| Range | `[<min>,<max>]` | `[0,100]` |

셸에서는 리터럴 전체를 작은따옴표로 감싼다: `--in.msg '"hi there"@en'`.

---

## 7. 자주 겪는 오류

| 증상 | 원인과 해결 |
|------|-------------|
| `error: IllegalArgumentException: MDTManager endpoint is not configured` | 접속 설정이 없다. §2의 설정 파일을 두거나 `MDT_URL`을 설정한다 |
| `error: RESTfulIOException: Failed to connect to the server: endpoint=...` | MDTManager가 꺼져 있거나 URL이 틀렸다. `mdt endpoints`로 확인한다 |
| `ResourceNotFoundException: id=MDTInstance(id=...)` | 해당 MDTInstance가 등록되어 있지 않다. `mdt list instances`로 확인한다 |
| `InvalidResourceStatusException: id=MDTInstance(id=...), status=...` | MDTInstance가 실행 중이 아니다. `mdt start instance <id>`로 시작한다 |
| `Missing required parameter: ...` (종료 코드 2) | 필수 위치 인자가 빠졌다. `-h`로 형식을 확인한다 |
| `Specify either MDTInstance ids or --all, not both.` | id 목록과 `--all`을 함께 줬다 |
| `run` 명령에서 위치 인자 오류 | 동적 인자를 위치 인자 앞에 썼다. 뒤로 옮기거나 `--in.x=값` 형식을 쓴다 |
| 결과를 파싱하지 못함 | 기본 출력이 표·트리다. `-o json`, `-o csv`, `-o value`를 붙인다 |
| 와일드카드가 다른 값으로 바뀜 | 셸이 `*`를 확장했다. `'param:line-01:*'`처럼 작은따옴표로 감싼다 |
| 원인을 더 보고 싶음 | `--loglevel debug`를 붙이면 스택 트레이스와 접속 정보를 출력한다 |

---

## 8. 부록: 등록되지 않은 명령

소스에는 있지만 어떤 명령 그룹에도 등록되어 있지 않아 `mdt`로 실행할 수 없는 명령이다.

- `mdt.cli.StopSimulationCommand`
- `mdt.task.skku.SKKUSimulationTaskCommand`
