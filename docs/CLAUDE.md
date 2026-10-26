# mdt CLI 사용 가이드 (LLM/에이전트용)

이 문서는 **Claude 같은 LLM 에이전트가 `mdt` CLI를 직접 호출할 때** 올바른 명령을 조립하기 위한
참조 문서다. 사람이 읽는 설명서는 [commands.md](commands.md)를 참고한다. 여기서는 "어떤 작업을
어떤 명령으로, 어떤 인자 문법으로 표현하는가"에 집중한다.

전체 명령 목록/옵션은 [commands.md](commands.md)에 있으므로, **이 문서는 판단·조립 규칙과
자주 틀리는 지점만** 다룬다.

---

## 0. 실행 전 체크리스트 (매번 확인)

1. **호출 형태**: `sbin/mdt <group> <subcommand> [args...] [options...]`
   (배포본에서는 `MDT_CLIENT_HOME`이 설정돼 있어야 하며 스크립트가 `mdt-cli-all.jar`를 구동한다.)
2. **접속 대상**: 실행 중인 MDTManager. 접속 정보 우선순위 = `--client_conf <path>` →
   `$MDT_CLIENT_HOME/mdt_client_config.yaml` → 환경변수 `MDT_URL`.
   - endpoint를 모르면 먼저 `mdt endpoints`로 확인한다.
3. **결과를 파싱해야 하면 반드시 `-o json`(또는 `value`/`csv`)을 붙인다.** 기본 출력은 사람이 보는
   `table`/`tree`라 파싱에 부적합하다. (§3 참조)
4. **대상 식별자를 모르면 먼저 조회 명령으로 탐색한다.** 인스턴스 id를 모르면
   `mdt list instances -o json`, 파라미터/오퍼레이션 이름을 모르면
   `mdt get instance <id> parameters -o json` / `... operations -o json`.

---

## 1. 작업 → 명령 매핑 (의도 기반 선택)

| 하고 싶은 것 | 명령 |
|--------------|------|
| 인스턴스 목록/상태 확인 | `mdt list instances -o json` |
| 특정 인스턴스 요약 | `mdt get instance <id> -o json` |
| 인스턴스의 파라미터 목록 | `mdt get instance <id> parameters -o json` |
| 인스턴스의 오퍼레이션 목록 | `mdt get instance <id> operations -o json` |
| 인스턴스의 서브모델 목록 | `mdt get instance <id> submodels -o json` |
| 파라미터 **값** 읽기 | `mdt get parameter <id> <param> -o value` |
| 임의 SubmodelElement 값 읽기 | `mdt get element --ref <element-ref> -o value` |
| 오퍼레이션 인자 값 읽기 | `mdt get argument <id> <op> <arg> -o value` |
| 시계열 데이터 읽기(최근) | `mdt get timeseries <id> <ts-submodel> --last <n|dur> -o csv --header` |
| 시계열 데이터 읽기(시각 기준) | `mdt get timeseries <id> <ts-submodel> --start <datetime> --count <n> -o csv --header` |
| 파일(File 요소) 내려받기 | `mdt get file <element-ref> -f <path>` |
| 파라미터 **값** 쓰기 | `mdt set parameter <id> <param> --json '<json>'` |
| SubmodelElement 값 쓰기 | `mdt set element <id> <submodel> <idShortPath> --json '<json>'` |
| 오퍼레이션 인자 값 쓰기 | `mdt set argument <id> <op> <arg> --json '<json>'` |
| File 요소에 파일 올리기 | `mdt set file <element-ref> <path> --mimeType <type>` |
| AAS Operation 실행 | `mdt run operation <id> <submodel> <path> --in.<n> <expr> --out.<n> <ref> --showResult` |
| AI/Simulation Submodel 실행 | `mdt run submodel <id> <submodel> --in.<n> <expr> --out.<n> <ref> --showResult` |
| 인스턴스 기동/중지 | `mdt start instance <id...>` / `mdt stop instance <id...>` |
| 인스턴스 등록/삭제 | `mdt add instance <id> <path>` / `mdt remove instance <id...>` |
| 워크플로우 기동/중지 | `mdt start workflow <model-id>` / `mdt stop workflow <wf-id>` |
| 참조식을 URL로 해석 | `mdt resolve reference <element-ref>` |
| 시뮬레이션 실행 | `mdt simulation run <sim-id> -t <timeout>` |

값을 기계적으로 한 개만 뽑아야 하면 `get ... -o value`가 가장 파싱하기 쉽다(장식 없는 raw 값).

---

## 2. 참조식 (element-ref) 문법 — **가장 자주 틀리는 부분**

`--ref`, `get file`/`remove file`/`set file`의 `element-ref`, `run`의 `--in/--out/--inout`
값(참조 대상일 때) 등은 모두 아래 문법을 따른다. 문법은 ANTLR 정의
([`MdtExpr.g4`](../../mdt-client/src/main/antlr/MdtExpr.g4))에 근거한다.

### 2.1 서브모델 지정(submodelSpec) — 다른 참조의 기반 요소

| 형태 | 의미 | 예 |
|------|------|----|
| `<inst>:<smIdShort>` | 인스턴스의 submodel(idShort 기준) | `line01:Data` |
| `<inst>:idShort=<smIdShort>` | 위와 동일(명시형) | `line01:idShort=Data` |
| `submodel:id=<submodelId>` | 전역 submodel id 기준 | `submodel:id=https://ex/sm/Data` |

### 2.2 참조식 종류(fullElementSpec)

| 접두어 | 문법 | 의미 | 예 |
|--------|------|------|----|
| (없음) | `<submodelSpec>:<idShortPath>` | 일반 SubmodelElement | `line01:Data:DataInfo.temperature` |
| `param:` | `param:<inst>:<idShortPath \| INT \| *>` | MDT 파라미터 | `param:line01:Temperature` · `param:line01:*` |
| `oparg:` | `oparg:<submodelSpec>:<in\|out>:<name \| INT \| *>` | 오퍼레이션 입출력 인자 | `oparg:line01:Simulation:in:0` |
| `opvar:` | `opvar:<defaultElementSpec>:<in\|out\|inout>:<INT>` | Operation 요소의 변수 | `opvar:line01:AddAndSleep:Operation:out:1` |
| `timeseries:` | `timeseries:<submodelSpec>(#range)?(\|cols)?` | 시계열 | `timeseries:line01:Data#last=5\|current,power` |

- `idShortPath`: `name` 을 `.` 로 잇고, 리스트 원소는 `[정수]`로 인덱싱한다.
  예: `records[0].value`, `DataInfo.temperature`.
- `param:...:*` 는 인스턴스의 **파라미터 컬렉션 전체**를 가리킨다.
- `mdt:` 접두어(`mdt:<inst>...`)도 인스턴스 지정에 허용된다.

### 2.3 timeseries 범위/투영 (`#`=범위, `|`=컬럼)

| 조각 | 의미 | 예 |
|------|------|----|
| `#last=<INT>` | 마지막 N개 레코드 | `#last=10` |
| `#last=<dur>` | 마지막 기간 구간 | `#last=30s`, `#last=1h`, `#last=P1DT2H` |
| `#last=<dur>@<anchor>` | 기준점 지정 (`latest`=최신데이터시각(기본), `now`=현재시각) | `#last=1h@now` |
| `#<from>~<to>` | 절대 시간 범위(한쪽 생략 가능) | `#2024-01-01~2024-02-01`, `#2024-01-01T09:00:00Z~`, `#~2024-02-01` |
| `#<from>+<INT>` | `from`(포함)부터 시간 오름차순 N개 | `#2024-01-01T09:00:00Z+100` |
| `\|colA,colB` | 컬럼 투영(생략 시 전체 컬럼) | `\|current,power` |

- duration 접미어: `ms`,`s`,`m`,`h`,`d` 또는 ISO8601(`PT2H30M`, `P1D` 등, 년/월/주 제외).
- timestamp: `YYYY-MM-DD` 또는 `YYYY-MM-DDThh:mm[:ss[.fff]][Z|±hh:mm]`.
- 레코드 조회 결과는 범위 종류와 무관하게 항상 시각 오름차순이다.

---

## 3. 값 리터럴 문법 (`set`/`run --in` 의 리터럴 값)

`set ... --json '<...>'` 에는 JSON을 넘긴다(가장 안전). `run --in.<n> <expr>` 의 `<expr>`은
**참조식이거나 값 리터럴**일 수 있고, 값 리터럴은 아래 문법을 따른다.

| 종류 | 문법/예 |
|------|---------|
| Property 값 | `25.3`, `-7`, `true`, `abc`, `"공백 포함 문자열"` |
| 다국어 텍스트(MLP) | `"온도"@ko` |
| File 값 | `file:"/path/to/f.pdf"("application/pdf")` |
| Range 값 | `[0,100]` |

- 음수는 값 리터럴에서만 허용된다(인덱스·length·duration 등은 부호 없는 정수만).
- 셸에서 공백/특수문자가 있으면 작은따옴표로 전체를 감싼다: `--in.msg '"hi there"@en'`.

---

## 4. `run` 동적 인자 규칙 (중요)

`run operation`/`run submodel`/`run restful`/`run set` 은 태스크 인자를 동적으로 받는다.

| 인자 형태 | 의미 | 값에 쓸 수 있는 것 |
|-----------|------|--------------------|
| `--in.<name> <expr>` | 입력 인자 (`--<name>`으로도 지정 가능) | 참조식 **또는** 값 리터럴 |
| `--out.<name> <ref>` | 출력 인자 | **참조식만** (결과를 쓸 위치) |
| `--inout.<name> <ref>` | 입출력 인자 | **참조식만** |

- `run submodel` 과 `run restful` 은 **`--inout.*` 미지원**(주면 오류).
- `run set` 은 `--in.source <expr>` 와 `--out.target <ref>` 두 개가 사실상 필수.
- 실행 결과값을 콘솔에서 보려면 `--showResult` 를 붙인다.
- 완료까지 대기가 길면 `--timeout <dur>`(예: `--timeout 5m`), 폴링 주기 `--poll <dur>`.

예:
```bash
# param:line01:temp 값을 입력으로 넣고, 결과를 param:line01:pred 에 기록
mdt run submodel line01 Predict --in.input1 param:line01:temp --out.out1 param:line01:pred --showResult

# 리터럴 입력 + 결과 콘솔 출력
mdt run operation line01 Ops predict --in.x 3 --in.y 4 --showResult
```

---

## 5. set 계열 값 지정 방법

`set element`/`set parameter`/`set argument` 는 값 소스로 **정확히 하나**를 지정해야 한다.

| 옵션 | 용도 |
|------|------|
| `--json '<json string>'` | 인라인 JSON (짧은 값에 권장) |
| `--jsonFile <path>` | 파일에 담긴 JSON |
| `--file <path> [--path <v>] [--mimeType <t>]` | File 요소 콘텐츠 |

`set file <element-ref> <path>` 는 예외적으로 소스 그룹을 쓰지 않고 위치 인자로 파일을 받는다.

```bash
mdt set parameter line01 Temperature --json '25.3'
mdt set element line01 Data DataInfo.status --json '"RUNNING"'
mdt set file param:line01:manual ./manual.pdf --mimeType application/pdf
```

---

## 6. 출력 형식 요약 (파싱 관점)

| 명령류 | 파싱용 권장 옵션 | 비고 |
|--------|------------------|------|
| `list *` | `-o JSON`(instances) / 대부분 `-t` 또는 CSV | instances만 JSON 지원, 나머지는 CSV(구분자 `-d`) |
| `get instance`, `get shell`, `get submodel` | `-o json` | 기본 `table` |
| `get instance <id> <sub>` (parameters/submodels/…) | `-o json` | 기본 `tree` |
| `get element/parameter/argument` | `-o value`(단일 값) 또는 `-o json` | 기본 `tree` |
| `get timeseries` | `-o csv --header`(+`-d`) | `csv`,`table`,`json`,`value` 지원 |
| `get wfmodel/workflow` | `-o json` | 기본 `table` |

**단일 스칼라 값**만 필요하면 `-o value`가 최선(따옴표·표 장식 없음).

---

## 7. 자주 틀리는 지점 (gotchas)

- **파싱 실패**: `-o json/value/csv` 를 빠뜨리면 사람이 보는 표/트리가 나온다. 항상 명시.
- **`get instance <id> log` 는 `-o`/`-v` 옵션이 없다** (형제 하위명령과 달리 다른 베이스 클래스).
- **`run submodel`/`run restful` 에 `--inout.*` 금지.**
- **`param:...:*`, `list ... -f/--glob`** 등 와일드카드는 셸 확장을 피하려 작은따옴표로 감싼다:
  `'param:line01:*'`, `-g 'line-*'`.
- **약어 허용**이라 편하지만, 스크립트/자동화에서는 **전체 이름**을 써서 모호성을 피한다.
- **삭제/중지/기동의 대량 대상**: `--all`, `--glob/-g`, `--model-filter`로 범위를 지정하되,
  `remove instance --all` 등은 파괴적이므로 신중히. 실행 중 강제 삭제는 `remove instance -f`.
- **비동기 완료 대기**: `start/stop/run/simulation` 은 상태가 목표에 도달할 때까지 대기한다.
  대기 없이 즉시 반환하려면 `--nowait`, 타임아웃은 `--timeout`.
- **id vs idShort**: submodel 지정 시 인스턴스 하위 idShort(`line01:Data`)와 전역 id
  (`submodel:id=<...>`)를 혼동하지 말 것.
- **엔티티 탐색 우선**: id/이름을 확신할 수 없으면 조작 전에 `list`/`get ... -o json`으로 확인.

---

## 8. 전형적인 작업 흐름 (discover → act)

```bash
# 1) 무엇이 있는지 파악
mdt list instances -o json

# 2) 대상 인스턴스의 파라미터/오퍼레이션 파악
mdt get instance line01 parameters -o json
mdt get instance line01 operations -o json

# 3) 값 읽기
mdt get parameter line01 Temperature -o value

# 4) 값 쓰기
mdt set parameter line01 Temperature --json '26.0'

# 5) 연산 실행 후 결과 확인
mdt run submodel line01 Predict --in.in1 param:line01:Temperature --out.out1 param:line01:Pred --showResult
mdt get parameter line01 Pred -o value
```

---

## 참고 소스

- 명령 트리 진입점: [`MDTCommandsMain`](../src/main/java/mdt/cli/MDTCommandsMain.java)
- 공통 옵션/접속: [`AbstractMDTCommand`](../src/main/java/mdt/cli/AbstractMDTCommand.java)
- 참조식 문법: [`MdtExpr.g4`](../../mdt-client/src/main/antlr/MdtExpr.g4),
  파서 진입 [`ElementReferences.parseExpr`](../../mdt-client/src/main/api/mdt/model/sm/ref/ElementReferences.java)
- 사람용 전체 설명서: [commands.md](commands.md)
