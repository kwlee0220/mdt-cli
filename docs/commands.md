# mdt-cli 명령어 사용 설명서

Manufacturing DigitalTwin(MDT) 시스템 제어용 커맨드라인 인터페이스(`mdt`)의 전체 명령어
사용법을 정리한 문서이다. 진입점은
[`mdt.cli.MDTCommandsMain`](../src/main/java/mdt/cli/MDTCommandsMain.java)이며,
`picocli` 기반의 계층형 하위 명령 구조를 가진다.

> 이 문서는 `MDTCommandsMain`의 `subcommands` 등록 목록을 기준으로, 실제 `mdt` 명령에서
> 호출 가능한 명령만 다룬다.

---

## 실행 방법

```bash
export MDT_CLIENT_HOME=/path/to/deploy      # 배포 디렉토리 (mdt-cli-all.jar 위치)
sbin/mdt <명령> [하위명령] [인자...] [옵션...]
```

- 리눅스/맥: [`sbin/mdt`](../sbin/mdt) — `java -Dfile.encoding=UTF-8 -jar $MDT_CLIENT_HOME/mdt-cli-all.jar "$@"`
- 윈도우: [`sbin/mdt.bat`](../sbin/mdt.bat)
- `gradle shadowJar` 산출물은 `build/libs/mdt-cli-<버전>-all.jar`(버전은 `MDT_BUILD_VERSION`, 미지정 시
  `unknown`)이므로, 배포 디렉토리에는 `mdt-cli-all.jar` 이름으로 복사한다.
- 이 문서에서는 `sbin/mdt`를 편의상 `mdt`로 표기한다.

### picocli 편의 기능

루트 명령은 다음 동작이 활성화되어 있다.

- **enum 값 대소문자 무시** — `--output json` == `--output JSON`
- **옵션 약어 허용** — `--time 30s` 가 `--timeout 30s`로 해석(모호하지 않은 경우)
- **하위 명령 약어 허용** — `mdt li inst` 가 `mdt list instances`로 해석
- `Duration` 타입 옵션은 `"1s"`, `"500ms"`, `"5m"` 형식 문자열을 사용

---

## MDTManager 접속 설정

모든 명령은 실행 중인 MDTManager에 HTTP로 접속한다. 접속 정보는 다음 우선순위로 결정된다.

1. `--client-conf <path>` 옵션으로 지정한 설정 파일
2. `$MDT_CLIENT_HOME/mdt_client_config.yaml` (`MDT_CLIENT_HOME`이 없으면 현재 디렉토리의 같은 파일)
3. 환경 변수 `MDT_URL` (endpoint URL)

설정 파일 예:

```yaml
mdtUrl: "${env:MDT_URL:-http://localhost:12985}"
connectTimeout: "10s"
readTimeout: "30s"
workflowManagerUrl: "${env:MDT_WORKFLOW_URL:-http://localhost:12989}"
# mqttEndpoint: "tcp://localhost:1883"     # 선택
```

| 키 | 설명 |
|----|------|
| `mdtUrl` | MDTManager endpoint URL |
| `connectTimeout` / `readTimeout` | HTTP 접속/읽기 타임아웃 (`"10s"` 형식) |
| `workflowManagerUrl` | 워크플로우 매니저 URL (선택) |
| `mqttEndpoint` | MQTT endpoint (선택) |

- 값에는 `${env:VAR}` / `${env:VAR:-기본값}` 형식으로 환경 변수를 넣을 수 있다.
- 위 5개 외의 키가 있으면 설정 파일을 읽지 못하고 오류가 난다. 예전 키 이름인 `endpoint`,
  `workflowEndpoint`는 더 이상 인식되지 않으므로 각각 `mdtUrl`, `workflowManagerUrl`로 바꾼다.

---

## 공통 옵션

모든 실행 명령([`AbstractMDTCommand`](../src/main/java/mdt/cli/AbstractMDTCommand.java) 상속)이
공통으로 지원하는 옵션이다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--client-conf` | `<path>` | MDTManager 클라이언트 설정 파일 경로 |
| `--loglevel` | `<level>` | 로그 레벨: `debug`, `info`, `warn`, `error` |
| `-h`, `--help` | | 사용법 출력 |
| `-V`, `--version` | | 버전 출력 |

> 아래 각 명령 설명의 옵션 표에는 위 공통 옵션을 생략한다.

---

옵션 이름은 kebab-case(`--client-conf`, `--json-file`, `--mime-type`, `--show-result`, `--base-url`,
`--use-endpoint`)를 쓴다. 이전 이름(`--client_conf`, `--jsonFile`, `--mimeType`, `--showResult`, `--baseUrl`,
`--use_endpoint`, `--pollInterval`)도 별칭으로 계속 받는다.

## 명령어 전체 구조

```
mdt
├── endpoints                     클라이언트 endpoint 정보 출력
├── build <instance-id> <dir>     MDTInstance zip 파일 생성
├── list                          MDT 엔티티 목록 조회
│   ├── instances                 MDT 인스턴스 목록
│   ├── shells                    AAS(Shell) 목록
│   ├── submodels                 Submodel 목록
│   ├── operations                Operation(AI/Simulation) submodel 목록
│   ├── wfmodels                  워크플로우 모델 목록
│   └── workflows                 워크플로우 인스턴스 목록
├── get                           MDT 엔티티 상세 조회
│   ├── instance <instance-id>    인스턴스 정보 ( + 하위: submodels/parameters/operations/compositions/log )
│   ├── mdt-info <instance-id>    MDT 모델 정보
│   ├── element <instance-id> <submodel-idShort> <idShortPath>  SubmodelElement 값 조회 (또는 --ref <element-ref>)
│   ├── parameter <instance-id> [parameter-id]  파라미터 값 조회
│   ├── argument <instance-id> <operation-id> <argument-id>  오퍼레이션 인자 값 조회
│   ├── shell <aas-id|instance-id>  AAS(Shell) 정보
│   ├── submodel <instance-id|submodel-id> [submodel-idShort]  Submodel 정보
│   ├── file <element-ref>        File SubmodelElement 다운로드
│   ├── timeseries (ts) <instance-id> <submodel-idShort>  시계열 데이터 조회
│   ├── wfmodel <model-id>        워크플로우 모델 ( + 하위: script )
│   └── workflow <workflow-name>  워크플로우 인스턴스 ( + 하위: log )
├── add                           MDT 엔티티 등록
│   ├── instance <instance-id> <path>  MDT 인스턴스 등록
│   └── wfmodel <path>            워크플로우 모델 등록
├── set                           MDT 엔티티 값 설정
│   ├── element <instance-id> <submodel-idShort> <idShortPath>  SubmodelElement 값 설정
│   ├── parameter <instance-id> [parameter-id]  파라미터 값 설정
│   ├── argument <instance-id> <operation-id> <argument-id>  오퍼레이션 인자 값 설정
│   └── file <element-ref> <path>   File SubmodelElement 설정
├── remove                        MDT 엔티티 삭제
│   ├── instance [instance-id...]  MDT 인스턴스 삭제
│   ├── file <element-ref>        File 첨부 삭제
│   ├── wfmodel [model-id...]     워크플로우 모델 삭제
│   └── workflow [workflow-name...]  워크플로우 인스턴스 삭제
├── start                         MDT 엔티티 기동
│   ├── instance [instance-id...]  인스턴스 기동
│   └── workflow <model-id>       워크플로우 기동
├── stop                          MDT 엔티티 중지
│   ├── instance [instance-id...]  인스턴스 중지
│   └── workflow <workflow-name>  워크플로우 중지
├── suspend                       MDT 엔티티 일시 정지
│   └── workflow <workflow-name>  워크플로우 일시 정지
├── resume                        일시 정지된 MDT 엔티티 재개
│   └── workflow <workflow-name>  워크플로우 재개
├── run                           MDT Task 실행
│   ├── operation <instance-id> <submodel-idShort> <idShortPath>  AAS Operation 실행
│   ├── submodel <instance-id> <submodel-idShort>  AI/Simulation Submodel 실행
│   ├── restful                     RESTful 기반 async RPC 실행
│   └── set                         set task 실행
├── resolve                       MDT 대상 해석
│   └── reference <element-ref>   ElementReference를 URL로 해석
└── simulation                    시뮬레이션 제어
    └── run <instance-id|submodel-id>  시뮬레이션 실행 (SKKU)
```

---

## 최상위 명령

### `mdt endpoints`

클라이언트 설정에 기록된 endpoint 정보(MDTManager URL과 접속/읽기 타임아웃, 워크플로우 매니저 URL,
MQTT endpoint)를 출력한다. 설정은 위 [접속 설정](#mdtmanager-접속-설정)과 같은 우선순위로 찾는다.
(이 명령은 MDTManager에 접속하지 않고 로컬 설정만 읽는다.)

```bash
mdt endpoints
```

### `mdt build <instance-id> <dir>`

MDTInstance 구현 디렉토리로부터 배포용 zip 파일을 생성한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance 식별자 |
| `dir` | O | MDTInstance 디렉토리 경로 |

```bash
mdt build my-twin ./twins/my-twin
```

---

## `mdt list` — 목록 조회

MDT 인스턴스/셸/서브모델/오퍼레이션/워크플로우 목록을 조회한다.

### `mdt list instances`

전체 MDTInstance 목록을 출력한다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-f`, `--filter` | `<expr>` | | 인스턴스 필터식 |
| `-o`, `--output` | `<type>` | `CSV` | 출력 형식: `CSV`, `TABLE`, `TREE`, `JSON` |
| `-t`, `--table` | | false | `TABLE` 형식 강제(순서와 무관하게 `--output`보다 우선) |
| `-r`, `--repeat` | `<interval>` | | 반복 갱신 주기 (예: `"1s"`, `"500ms"`) |
| `-l`, `--long` | | false | 상세 정보 표시(CSV/TABLE 전용) |
| `-d`, `--delimiter` | `<delim>` | `\|` | 구분자 (CSV 전용) |
| `--pretty` | | false | 정렬 출력 (JSON 전용) |
| `--show-endpoint` | | false | 실행 중 인스턴스의 endpoint 표시(TREE 전용) |
| `-v` | | false | verbose |

**인스턴스 필터식(`-f`)** — MDTManager가 JPQL `WHERE` 절로 그대로 사용하므로, 인스턴스 속성은
`instance.<속성>` 형식으로 쓰고 문자열 리터럴은 작은따옴표로 감싼다(`=`, `!=`, `like` 등).

| 접두어 | 사용 가능한 속성 |
|--------|------------------|
| `instance.` | `instanceId`(MDTInstance id), `status`(`STOPPED`, `RUNNING` 등), `baseEndpoint`, `aasId`, `aasIdShort`, `globalAssetId`, `assetType` |
| `submodel.` | `id`, `idShort`, `semanticId` (쓰면 인스턴스의 submodel과 조인한다) |
| `parameter.` | `id`, `name`, `valueType` (쓰면 인스턴스의 파라미터와 조인한다) |

```bash
mdt list instances
mdt list instances -o TABLE -l
mdt list instances -o TREE --show-endpoint
mdt list instances -f "instance.instanceId like 'line%'" -o JSON --pretty
mdt list instances -f "instance.status = 'RUNNING'"
mdt list instances -r 2s          # 2초마다 갱신
```

### `mdt list shells` / `submodels` / `operations`

각각 전체 AssetAdministrationShell / Submodel / Operation(AI·Simulation) submodel 목록을
출력한다. 세 명령은 동일한 옵션 집합을 가진다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-f`, `--filter` | `<expr>` | | 인스턴스 필터식 (`list instances`와 같은 문법). 조건에 맞는 인스턴스의 항목만 출력 |
| `-t`, `--table` | | false | table 형식 출력 |
| `-d`, `--delimiter` | `<delim>` | `\|` | 구분자(simple 출력 전용) |
| `-r`, `--repeat` | `<interval>` | | 반복 갱신 주기 |
| `-v` | | false | verbose |

```bash
mdt list shells -t
mdt list submodels -f "instance.status = 'RUNNING'"
mdt list operations -t
```

### `mdt list wfmodels` / `workflows`

각각 전체 워크플로우 모델 / 워크플로우 인스턴스 목록을 출력한다. 두 명령은 동일한 옵션 집합을
가진다.

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-t`, `--table` | | false | table 형식 출력 |
| `-g`, `--glob` | `<expr>` | | 워크플로우 필터용 glob 패턴 |
| `-l`, `--long` | | false | 상세 정보 표시 |
| `-d`, `--delimiter` | `<delim>` | `\|` | 구분자(csv 출력 전용) |
| `-r`, `--repeat` | `<interval>` | | 반복 갱신 주기 |
| `-v` | | false | verbose |

```bash
mdt list wfmodels -t
mdt list workflows -g "line-*" -l
```

---

## `mdt get` — 상세 조회

### `mdt get instance <instance-id>`

MDTInstance 정보를 조회한다. 자체적으로 하위 명령을 가진다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | 조회할 MDTInstance id |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | 출력 형식: `table`, `json`, `env` |

```bash
mdt get instance line-01
mdt get instance line-01 -o json
```

#### `mdt get instance <instance-id>` 하위 명령

아래 하위 명령들은 상위 `instance` 명령의 위치 인자 `instance-id`를 대상으로 동작한다. (`log`을 제외한
나머지는 `-o/--output`(`tree`|`json`, 기본 `tree`)과 `-v` 옵션을 공통 지원한다.)

| 하위 명령 | 설명 |
|-----------|------|
| `submodels` | 인스턴스의 submodel 정보 |
| `parameters` | 인스턴스의 파라미터 정보 |
| `operations` | 인스턴스의 오퍼레이션(AI/Simulation) 정보 |
| `compositions` | 인스턴스의 Twin-Composition 정보 |
| `log` | 인스턴스 로그 데이터 (`-o`/`-v` 미지원) |

```bash
mdt get instance line-01 parameters
mdt get instance line-01 submodels -o json
mdt get instance line-01 operations
mdt get instance line-01 compositions
mdt get instance line-01 log
```

### `mdt get mdt-info <instance-id>`

인스턴스의 MDT 모델 정보를 조회한다. 표시할 항목을 플래그로 선택한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | 조회할 MDTInstance id |

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `-i`, `--info` | false | MDTInfo 표시 |
| `-p`, `--parameters` | false | 파라미터 표시 |
| `-o`, `--operations` | false | 오퍼레이션(AI/Simulation) 표시 |
| `-c`, `--compositions` | false | composition 표시 |
| `-r`, `--repeat <interval>` | | 반복 갱신 주기 |
| `-v`, `--verbose` | false | verbose |

```bash
mdt get mdt-info line-01 -i -p
mdt get mdt-info line-01 -c -r 3s
```

### `mdt get element ...`

SubmodelElement 정보를 조회한다. 대상 지정은 **위치 인자 방식**과 **`--ref` 참조식 방식** 중
하나를 택한다(둘 중 정확히 하나 필수).

- 위치 인자 방식(세 인자 모두 필수):

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id` | MDTInstance id |
| `submodel-idShort` | 대상 submodel idShort |
| `idShortPath` | 대상 SubmodelElement idShortPath |

  세 인자는 `<instance-id>:<submodel-idShort>:<idShortPath>` 참조식으로 합쳐 해석된다. `*` 와일드카드는 지원하지 않으며
  지정하면 참조식 파싱 오류가 난다. submodel 전체를 보려면 `mdt get submodel`을 쓴다.

- 참조식 방식:

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--ref` | `<ref_expr>` | ElementReference 표현식 (예: `param:inst:param1`, `timeseries:inst:Log:Tail#last=5`) |

- 공통 출력 옵션:

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `tree` | 출력 형식: `tree`, `json`, `value` |
| `-r`, `--repeat` | `<interval>` | | 반복 갱신 주기 |
| `-v` | | false | verbose |

```bash
mdt get element line-01 Data DataInfo.temperature
mdt get element line-01 Data DataInfo -o json
mdt get element --ref param:line-01:param1 -o value
mdt get element --ref 'param:line-01:*' -o json     # 파라미터 전체
```

### `mdt get parameter <instance-id> [parameter-id]`

MDTInstance의 파라미터 정보를 조회한다.

| 위치 인자 | 기본값 | 설명 |
|-----------|--------|------|
| `instance-id` (필수) | | MDTInstance id |
| `parameter-id` | `*` | 대상 파라미터 id (`*` = 전체) |

공통 출력 옵션: `-o/--output`(`tree` 기본), `-r/--repeat`, `-v`.

```bash
mdt get parameter line-01
mdt get parameter line-01 temperature -o value
```

### `mdt get argument <instance-id> <operation-id> <argument-id>`

오퍼레이션(AI/Simulation) 인자 값을 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `operation-id` | O | 대상 오퍼레이션(AI/Simulation) id |
| `argument-id` | O | 대상 인자 id |

공통 출력 옵션: `-o/--output`(`tree` 기본), `-r/--repeat`, `-v`.

```bash
mdt get argument line-01 predict input1 -o value
```

### `mdt get shell <aas-id|instance-id>`

AssetAdministrationShell 정보를 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `aas-id\|instance-id` | O | AAS id(또는 AAS idShort) 또는 MDTInstance id |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`, `json`, `desc` |

```bash
mdt get shell line-01 -o desc
```

### `mdt get submodel <instance-id|submodel-id> [submodel-idShort]`

Submodel 정보를 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id\|submodel-id` | O | MDTInstance id 또는 Submodel id |
| `submodel-idShort` | | 대상 Submodel idShort (instance-id와 함께 사용) |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`, `json`, `desc` |

```bash
mdt get submodel line-01 Data
mdt get submodel https://example.com/sm/Data -o json
```

### `mdt get file <element-ref>`

File SubmodelElement의 첨부 파일을 내려받아 로컬 파일로 저장한다. (표준 출력으로는 내보내지 않는다.)

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `element-ref` | O | 대상 File SubmodelElement 참조 |

| 옵션 | 인자 | 설명 |
|------|------|------|
| `-f`, `--file` | `<path>` | 저장할 파일 경로. 생략하면 File 요소의 `value`(파일 경로)를 그대로 저장 경로로 쓴다 |
| `-v` | | 저장한 파일의 절대 경로 출력 |

```bash
mdt get file param:line-01:manual -f ./manual.pdf
```

### `mdt get timeseries <instance-id> <submodel-idShort>`  (별칭: `ts`)

시계열(TimeSeries) submodel의 레코드를 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `submodel-idShort` | O | 대상 TimeSeries submodel idShort |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--header` | | false | CSV 출력 시 헤더 행 표시 |
| `-d`, `--delim` | `<delim>` | `,` | CSV 출력 구분자 |
| `--last` | `<period(@anchor)?>` | | 마지막 개수/기간 레코드 (예: `5`, `1d`, `2h`, `30m`, `1h@now`) |
| `--start` | `<datetime>` | | 조회 시작 시각(포함) |
| `--end` | `<datetime>` | | 조회 종료 시각(포함) |
| `--count` | `<count>` | | `--start` 시각부터 시간 오름차순으로 읽을 레코드 개수 |
| `--columns` | `<columns>` | | 선택 컬럼 |
| `-o`, `--output` | `<type>` | `tree` | `tree`, `json`, `value`, `csv`, `table` |
| `-r`, `--repeat` | `<interval>` | | 반복 갱신 주기 |
| `-v` | | false | verbose |

조회 결과는 범위 지정 방식과 무관하게 **항상 시각 오름차순**으로 반환된다.

**범위 지정 옵션 조합**

| 조합 | 의미 |
|------|------|
| (없음) | 전체 레코드 |
| `--last <N>` | 마지막 N개 |
| `--last <dur>[@anchor]` | 마지막 기간 구간. `anchor`는 `latest`(기본, 최신 데이터 시각) 또는 `now`(현재 시각) |
| `--start <T>` / `--end <T>` / 둘 다 | 절대 시간 범위(한쪽 개방 가능) |
| `--start <T> --count <N>` | `T`(포함)부터 시간 오름차순으로 N개 |

- `--last`는 `--start`/`--end`/`--count`와 함께 쓸 수 없다.
- `--count`는 `--start`를 필요로 하며 `--end`와 함께 쓸 수 없다.
- `datetime` 표기: `YYYY-MM-DDThh:mm[:ss[.fff]]`(타임존 생략 시 로컬 시각), `...Z`, `...±hh:mm`.

```bash
mdt ts line-01 Log --last 1h -o csv --header
mdt get timeseries line-01 Log --start 2026-07-01T00:00:00 --end 2026-07-02T00:00:00

# 특정 시각부터 100개
mdt get timeseries line-01 Log --start 2026-07-01T09:00:00Z --count 100 -o csv --header

# 가장 오래된 레코드 1건 (데이터보다 이른 시각을 시작점으로 지정)
mdt get timeseries line-01 Log --start 1970-01-01T00:00:00Z --count 1 -o csv
```

### `mdt get wfmodel <model-id>`

워크플로우 모델 정보를 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `model-id` | O | 조회할 워크플로우 모델 id |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`, `json` |

하위 명령 `mdt get wfmodel <model-id> script` — 워크플로우 스크립트를 조회한다.

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--mdt-url` | `<url>` | MDTPlatform URL |
| `--mdt-client-docker` | `<image-id>` | MDTClient 도커 이미지 |

```bash
mdt get wfmodel line-flow -o json
mdt get wfmodel line-flow script
```

### `mdt get workflow <workflow-name>`

워크플로우 인스턴스(또는 모델) 정보를 조회한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `workflow-name` | O | 조회할 워크플로우 인스턴스 이름 |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-o`, `--output` | `<type>` | `table` | `table`, `json` |

하위 명령 `mdt get workflow <workflow-name> log` — 워크플로우 로그를 조회하는 명령이다.

| 옵션 | 인자 | 필수 | 설명 |
|------|------|------|------|
| `--pod` | `<name>` | O | 로그를 읽을 Pod 이름 |

> **주의**: 현재 이 하위 명령은 본문이 비활성화(주석 처리)되어 있어, 실행해도 아무것도 출력하지 않는다.

```bash
mdt get workflow line-flow-001 -o json
```

---

## `mdt add` — 등록

### `mdt add instance <instance-id> <path>`

MDT 인스턴스를 등록한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | 등록할 MDTInstance 식별자 |
| `path` | O | MDTInstance 구현 디렉토리 또는 zip 파일 경로 |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-p`, `--port` | `<number>` | `-1` | 인스턴스 포트 번호 (`-1` = 자동 할당) |
| `-v` | | false | 성공 시 상세 출력 |

```bash
mdt add instance line-01 ./twins/line-01.zip
mdt add instance line-01 ./twins/line-01 -p 13000
```

### `mdt add wfmodel <path>`

워크플로우 모델을 등록한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `path` | O | 등록할 워크플로우 모델 JSON 파일 경로 |

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `-f`, `--force` | false | 동일 모델 존재 시 덮어쓰기 |

```bash
mdt add wfmodel ./workflows/line-flow.json -f
```

---

## `mdt set` — 값 설정

`element`/`parameter`/`argument` 세 명령은 값 소스를 지정하는 **상호 배타 그룹(하나 필수)** 을
공유한다.

| 소스 옵션 | 인자 | 설명 |
|-----------|------|------|
| `--json-file` | `<path>` | JSON 파일 경로 |
| `--json` | `<json>` | SubmodelElement/ElementValue의 JSON 문자열 |
| `--file` | `<path>` | (File 요소용) 파일 경로. 아래 `--path`/`--mime-type`와 함께 사용 |
| `--path` | `<value>` | File 값 경로(선택) |
| `--mime-type` | `<contentType>` | File contentType(선택) |

### `mdt set element <instance-id> [submodel-idShort] [idShortPath]`

SubmodelElement 값을 설정한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `submodel-idShort` | O* | 대상 submodel idShort |
| `idShortPath` | O* | 대상 SubmodelElement idShortPath |

\* 파싱 단계에서는 생략할 수 있지만(기본값 `*`), `*`는 참조식으로 해석되지 않아 실행 시 오류가 나므로
반드시 지정한다.

```bash
mdt set element line-01 Data DataInfo.temperature --json '25.3'
mdt set element line-01 Data DataInfo --json-file ./data.json
```

### `mdt set parameter <instance-id> [parameter-id]`

파라미터 값을 설정한다.

| 위치 인자 | 기본값 | 설명 |
|-----------|--------|------|
| `instance-id` (필수) | | MDTInstance id |
| `parameter-id` | `*` | 대상 파라미터 id |

```bash
mdt set parameter line-01 temperature --json '25.3'
```

### `mdt set argument <instance-id> <operation-id> <argument-id>`

오퍼레이션(AI/Simulation) 인자 값을 설정한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `operation-id` | O | 대상 오퍼레이션 id |
| `argument-id` | O | 대상 인자 id |

```bash
mdt set argument line-01 predict input1 --json-file ./in1.json
```

### `mdt set file <element-ref> <path>`

File SubmodelElement를 설정한다. (다른 `set` 명령과 달리 소스 그룹을 사용하지 않고 위치 인자로
파일을 지정한다.)

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `element-ref` | O | 대상 SubmodelElementReference |
| `path` | O | 설정할 파일 경로 |

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--path` | `<value>` | File 값 경로 |
| `--mime-type` | `<contentType>` | File contentType |

```bash
mdt set file param:line-01:manual ./manual.pdf --mime-type application/pdf
```

---

## `mdt remove` — 삭제

### `mdt remove instance [instance-id...]`

MDT 인스턴스를 삭제한다.

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id...` | 삭제할 MDTInstance id 목록 (`--all` 지정 시 생략 가능) |

| 옵션 | 설명 |
|------|------|
| `-a`, `--all` | 모든 MDTInstance 삭제 |
| `-f`, `--force` | 실행 중이어도 강제 삭제 |
| `-v` | verbose |

```bash
mdt remove instance line-01 line-02
mdt remove instance --all --force
```

### `mdt remove file <element-ref>`

AAS File 첨부를 삭제한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `element-ref` | O | 대상 File SubmodelElement 참조 |

| 옵션 | 설명 |
|------|------|
| `-v` | verbose |

```bash
mdt remove file param:line-01:manual
```

### `mdt remove wfmodel [model-id...]`

워크플로우 모델을 삭제한다. (id, `--glob`, `--all` 중 하나로 대상을 지정)

| 위치 인자 | 설명 |
|-----------|------|
| `model-id...` | 삭제할 워크플로우 모델 id 목록 |

| 옵션 | 인자 | 설명 |
|------|------|------|
| `-g`, `--glob` | `<expr>` | glob 패턴으로 필터 |
| `-a`, `--all` | | 모든 워크플로우 모델 삭제 |

```bash
mdt remove wfmodel line-flow
mdt remove wfmodel -g "test-*"
```

### `mdt remove workflow [workflow-name...]`

워크플로우 인스턴스를 삭제한다. (id, `--glob`, `--all`, `--model-filter` 중 하나로 대상 지정)

| 위치 인자 | 설명 |
|-----------|------|
| `workflow-name...` | 삭제할 워크플로우 인스턴스 이름 목록 |

| 옵션 | 인자 | 설명 |
|------|------|------|
| `--model-filter` | `<model-id>` | 특정 모델 id의 인스턴스 삭제 |
| `-g`, `--glob` | `<expr>` | glob 패턴으로 필터 |
| `-a`, `--all` | | 모든 워크플로우 인스턴스 삭제 |

```bash
mdt remove workflow line-flow-001
mdt remove workflow --model-filter line-flow
```

---

## `mdt start` — 기동

### `mdt start instance [instance-id...]`

MDTInstance를 기동한다.

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id...` | 기동할 MDTInstance id 목록 (`--all` 지정 시 생략 가능) |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--poll` | `<duration>` | `1s` | 상태 폴링 주기 |
| `--timeout` | `<duration>` | | 상태 샘플링 타임아웃 |
| `--nowait` | | false | RUNNING 도달까지 대기하지 않음 |
| `-a`, `--all` | | false | 미실행 인스턴스 전체 기동(id와 함께 사용 불가) |
| `-n`, `--nthreads` | `<int>` | `1` | 스레드 풀 크기 |
| `-r`, `--recursive` | | false | 의존 인스턴스 재귀 기동(id 필요) |

```bash
mdt start instance line-01
mdt start instance --all -n 4
mdt start instance line-01 -r --timeout 1m
```

### `mdt start workflow <model-id>`

워크플로우를 기동한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `model-id` | O | 기동할 워크플로우 모델 id |

```bash
mdt start workflow line-flow
```

---

## `mdt stop` — 중지

### `mdt stop instance [instance-id...]`

실행 중인 MDTInstance를 중지한다.

| 위치 인자 | 설명 |
|-----------|------|
| `instance-id...` | 중지할 MDTInstance id 목록 (`--all` 지정 시 생략 가능) |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--poll` | `<duration>` | `1s` | 상태 폴링 주기 |
| `--timeout` | `<duration>` | | 상태 샘플링 타임아웃 |
| `-a`, `--all` | | | 실행 중인 인스턴스 전체 중지 |
| `-n`, `--nthreads` | `<int>` | `1` | 스레드 풀 크기 |
| `-r`, `--recursive` | | | 의존 인스턴스 재귀 중지(`--all` 지정 시 무시) |
| `--nowait` | | false | 중지 완료까지 대기하지 않음 |

```bash
mdt stop instance line-01
mdt stop instance --all
```

### `mdt stop workflow <workflow-name>`

워크플로우를 중지한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `workflow-name` | O | 중지할 워크플로우 인스턴스 이름 |

```bash
mdt stop workflow line-flow-001
```

---

## `mdt suspend` / `mdt resume` — 일시 정지와 재개

### `mdt suspend workflow <workflow-name>`

실행 중인 워크플로우를 일시 정지하고, 갱신된 워크플로우 정보를 출력한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `workflow-name` | O | 일시 정지할 워크플로우 인스턴스 이름 |

### `mdt resume workflow <workflow-name>`

일시 정지된 워크플로우를 재개하고, 갱신된 워크플로우 정보를 출력한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `workflow-name` | O | 재개할 워크플로우 인스턴스 이름 |

```bash
mdt suspend workflow line-flow-001
mdt resume workflow line-flow-001
```

---

## `mdt run` — Task 실행

`run` 명령들은 태스크 인자를 **동적 인자(dynamic arguments)** 로 받는다. 고정 옵션 외에
다음 형식의 인자를 자유롭게 지정할 수 있다.

| 동적 인자 | 설명 |
|-----------|------|
| `--in.<name> <expr>` | 입력 인자 (참조식 또는 리터럴). `--<name> <expr>` 로도 지정 가능(입력으로 처리) |
| `--out.<name> <ref-expr>` | 출력 인자 (참조식만) |
| `--inout.<name> <ref-expr>` | 입출력 인자 (참조식만) |

- 값은 `--in.x 3`처럼 다음 토큰으로 주거나, `--in.x=3`처럼 `=`로 붙여 줄 수 있다.
- **동적 인자는 모든 위치 인자 뒤에 쓴다.** 위치 인자보다 앞에 `--in.x 3` 형식으로 쓰면 값 토큰이
  위치 인자로 소비되어 오류가 난다. 순서를 바꿀 수 없으면 `--in.x=3` 형식을 쓴다.
- 음수처럼 `-`로 시작하는 값은 `--in.x=-3`처럼 `=` 형식으로 주는 것이 안전하다.
- 값이 없는 동적 인자는 오류다.
- `operation`/`submodel`/`set`은 대상이 선언하지 않은 인자 이름을 주면 오류를 낸다(선언된 이름 목록을
  함께 출력한다). `restful`은 이름을 검사하지 않는다.

`operation`/`submodel`/`restful` 명령은 다음 공통 옵션을 지원한다. (`set`은 지원하지 않는다.)

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `--poll` | `<duration>` | `1s` | 상태 폴링 주기 |
| `--timeout` | `<duration>` | (없음) | 실행 타임아웃 |
| `--show-result` | | false | 실행 후 output/inoutput 인자 값을 콘솔에 표시 |

입력 인자(`--in.<name>`)의 값은 명령마다 다음 순서로 해석한다.

| 명령 | 해석 순서 |
|------|-----------|
| `operation`, `submodel` | ① 참조식이면 참조 → ② JSON이면 대상 인자의 타입에 맞춰 값으로 해석 → ③ 대상 인자가 Property면 따옴표 없는 값을 그 데이터 타입으로 해석(맞지 않으면 오류) |
| `restful` | ① 참조식이면 참조 대상의 값(File이면 참조 자체) → ② JSON 리터럴 → ③ 그 외는 문자열 |
| `set` | ① 참조식이면 참조 → ② 값 리터럴([CLAUDE.md](CLAUDE.md) §3 문법) → ③ 그 외는 문자열 |

### `mdt run operation <instance-id> <submodel-idShort> [idShortPath]`

AAS Operation 요소를 실행한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `submodel-idShort` | O | 대상 submodel idShort |
| `idShortPath` | O* | 대상 Operation 요소의 idShortPath |

\* 파싱 단계에서는 생략할 수 있지만(기본값 `*`), `*`는 참조식으로 해석되지 않아 실행 시 오류가 나므로
반드시 지정한다.

```bash
mdt run operation line-01 Ops predict --in.x 3 --out.y param:line-01:result --show-result
```

### `mdt run submodel <instance-id> <submodel-idShort>`

AI/Simulation Submodel을 실행한다. (`--inout.*` 를 지정하면 오류)

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id` | O | MDTInstance id |
| `submodel-idShort` | O | 대상 AI/Simulation submodel idShort |

```bash
mdt run submodel line-01 Predict --in.input1 param:line-01:temp --out.out1 param:line-01:pred
```

### `mdt run restful`

원격 연산을 RESTful 기반 비동기 RPC로 실행한다. 최초 요청으로 연산을 시작한 뒤 상태를 폴링하여
완료를 기다린다. (위치 인자 없음. `--in.*`/`--out.*` 동적 인자 지원, `--inout.*` 를 지정하면 오류)

| 옵션 | 인자 | 필수 | 설명 |
|------|------|------|------|
| `--base-url` | `<baseURL>` | O | 서버 base URL |
| `--endpoint` | `<endpoint>` | O | 오퍼레이션 endpoint |

`--poll`/`--timeout`/`--show-result`는 위 공통 옵션과 같다.

- `--out.<name>`이 가리키는 요소의 값은 요청 시점에 읽어 요청 메시지에 함께 보낸다(File이면 참조 자체).
  응답 값에 `@type`이 없으면 이 값의 타입으로 해석하여 기록한다.
- 요청한 출력이 응답에 없거나, 요청하지 않은 출력이 응답에 있으면 무시하고 경고 로그만 남긴다.

```bash
mdt run restful --base-url http://localhost:9000 --endpoint /op/predict \
    --in.x 3 --out.y param:line-01:result --show-result
```

### `mdt run set`

set task를 실행한다. `source` 값을 `target` 위치에 기록한다. (위치 인자 없음. 동적 인자만 사용)

| 동적 인자 | 필수 | 설명 |
|-----------|------|------|
| `--in.source <expr>` | O | 소스 값 (참조식 또는 값 리터럴) |
| `--out.target <ref-expr>` | O | 기록할 대상 참조 |

- 둘 중 하나라도 없으면 오류다. `source`/`target` 이외의 이름이나 `--inout.*` 를 주어도 오류다.
- `source` 는 요소 참조식이나 값 리터럴이어야 한다. 따옴표 없는 단어·숫자·불리언(`hello`, `25.3`, `true`)은 그대로 값으로 읽는다. Submodel 참조(`line-01:Data`), 빈 문자열, 따옴표 없는 날짜·기간(`PT5S`)처럼 둘 다 아닌 입력은 오류다. 문자열로 넘기려면 따옴표로 감싼다(예: `--in.source "'PT5S'"`). 2026-11-01 이전에는 이런 입력을 오류 없이 문자열로 기록했다.

```bash
mdt run set --in.source 25.3 --out.target param:line-01:temperature
```

---

## `mdt resolve` — 대상 해석

### `mdt resolve reference <element-ref>`

ElementReference를 URL로 해석한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `element-ref` | O | 대상 element 참조 |

```bash
mdt resolve reference param:line-01:temperature
```

---

## `mdt simulation` — 시뮬레이션 제어

### `mdt simulation run <instance-id|submodel-id>`

시뮬레이션(SKKU)을 실행한다.

| 위치 인자 | 필수 | 설명 |
|-----------|------|------|
| `instance-id\|submodel-id` | O | MDTInstance id 또는 대상 Simulation Submodel id |

| 옵션 | 인자 | 기본값 | 설명 |
|------|------|--------|------|
| `-e`, `--use-endpoint` | | false | Simulation submodel 서비스 endpoint로 호출 |
| `--nowait` | | false | 실행 완료까지 대기하지 않음 |
| `--poll` | `<duration>` | `3s` | 상태 폴링 주기 |
| `-t`, `--timeout` | `<duration>` | | 시뮬레이션 타임아웃 |
| `-v` | | false | verbose |

```bash
mdt simulation run sim-line-01 -t 5m
mdt simulation run sim-line-01 -e --poll 5s
```

---

## 부록: 등록되지 않은 명령

소스에는 존재하지만 `MDTCommandsMain`의 `subcommands`에 등록되어 있지 않아 현재 `mdt` 명령으로
호출할 수 없는 항목이 있다. 참고용으로만 기록한다.

- `mdt/cli/`의 `StopSimulationCommand`, `mdt/task/skku/`의 `SKKUSimulationTaskCommand`는 어떤 그룹에도
  등록되어 있지 않다.
