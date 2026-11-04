# CLAUDE.md

Guidance for LLM agents working in this repository. Two parts:

- **Part 1 — Development:** build, structure, and rules for reading/changing the code.
- **Part 2 — Using the `mdt` CLI:** how to assemble and run `mdt` commands correctly, and common mistakes.

The full human-oriented command reference is [docs/commands.md](docs/commands.md) (Korean).

---

# Part 1. Development

## Project

`mdt-cli` is the command-line client of the Manufacturing DigitalTwin (MDT) system. A single picocli root command
`mdt` (`mdt.cli.MDTCommandsMain`) exposes command groups that talk to a running MDTManager over HTTP to control
instances, submodels, AAS elements, workflows, and simulations.

**Language rule:** source comments, Javadoc, and `docs/commands.md` are written in Korean. Keep that when editing
code. This file is in English for agents.

## Build / run / test

```bash
gradle shadowJar                                     # fat jar → build/libs/mdt-cli-<version>-all.jar
MDT_BUILD_VERSION=1.7.0 gradle shadowJar             # versioned build (default "unknown")
gradle test                                          # all JUnit 5 tests
gradle test --tests 'mdt.cli.CommandRegressionTest'  # one test class
```

- Java 21 toolchain. No Gradle wrapper — use the system `gradle`.
- `MDT_BUILD_VERSION` goes into the jar manifest `Implementation-Version`; `mdt -V` prints it via
  [`MDTVersionProvider`](src/main/java/mdt/cli/MDTVersionProvider.java) (`unknown` when run from class dirs).
- Runtime entry is [`sbin/mdt`](sbin/mdt): `java -Dfile.encoding=UTF-8 -jar $MDT_CLIENT_HOME/mdt-cli-all.jar "$@"`.
  Deploy the shadow jar as `$MDT_CLIENT_HOME/mdt-cli-all.jar`.
- Tests:
  - [`CommandRegressionTest`](src/test/java/mdt/cli/CommandRegressionTest.java) — runs commands against a fake
    MDTManager; checks target selection, exit codes, config propagation.
  - [`PeriodicRefreshingConsoleTest`](src/test/java/mdt/cli/PeriodicRefreshingConsoleTest.java) — repeat-mode output.
- The CLI runtime classpath uses the `:mdt-client` **jar**. After changing mdt-client, run `gradle jar` in
  `../mdt-client` before running `mdt` commands manually.

## Dependencies (composite build)

This is its own git repository, but [`settings.gradle`](settings.gradle) includes sibling projects by relative path:

- `:utils` → `../../common/utils` — `FOption`, `FStream`, `Picoclies`, `LogbackConfigLoader`, `utils.Preconditions`.
- `:mdt-client` → `../mdt-client` — `HttpMDTManager`, `MDTClientConfig`, `MDTInstance`, `StopMDTInstances`,
  `StartInstances`, task classes, reference-expression parser.

Most domain and HTTP logic lives in `:mdt-client`; read it to understand what a command actually does. If you
change a public mdt-client API used here, the two repositories must be committed together.

## Architecture

### Command hierarchy

| Type | Role |
|---|---|
| `MDTCommandsMain` | Root `mdt` command; registers top-level groups. `newCommandLine(Object)` builds the shared picocli config (case-insensitive enums, abbreviated options/subcommands, `Duration`/`Level` converters, usage width 110, `CommandExceptionHandler`). Every entry point must use it. |
| [`CommandCollection`](src/main/java/mdt/cli/CommandCollection.java) | Base of group commands (`get`, `list`, `add`, `set`, `remove`, `start`, `stop`, `suspend`, `resume`, `run`, `resolve`, …). Prints usage when called without a subcommand. A new subcommand must be added to its group's `@Command(subcommands={...})`. |
| [`AbstractCommand`](src/main/java/mdt/cli/AbstractCommand.java) | Base of all commands. Commands that do not connect to MDTManager (`build`, `endpoints`) extend it and implement `execute()`. |
| [`AbstractMDTCommand`](src/main/java/mdt/cli/AbstractMDTCommand.java) | Base of commands that connect. Subclasses implement only `run(MDTManager)`. |

### Execution order (`AbstractCommand.run()`)

1. Apply `--loglevel`. If the command does not set it, use the nearest parent command's value (`findNearest`).
2. `validate()` — check option/argument combinations. Runs **before** connecting, so bad input is reported as a usage
   error rather than masked by connection errors.
3. `execute()` — `AbstractMDTCommand` resolves the client config, connects, then calls `run(MDTManager)`.
4. `finally`: flush `out()` and `err()`.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | An exception escaped (`CommandExceptionHandler` prints `error: <message>`), or the command called `markFailed()`. |
| 2 | Usage error: `usageError(msg)` (a picocli `ParameterException`) or a picocli parse error. |

[`CommandExceptionHandler`](src/main/java/mdt/cli/CommandExceptionHandler.java) prints a one-line message for user
errors (`MDTException`, `IllegalArgumentException`, `RESTfulException`, `IOException`, `TimeoutException`), and a
stack trace for anything else or when the log level is debug.

### Client config resolution

Order: `--client-conf <path>` (nearest command that sets it, walking up parents) →
`$MDT_CLIENT_HOME/mdt_client_config.yaml` (current dir if `MDT_CLIENT_HOME` is unset) → env `MDT_URL`
(`MDTClientConfig.loadDefault()`). If the config file exists but fails to parse, it errors instead of falling back to
`MDT_URL`. YAML keys must match `MDTClientConfig` fields exactly: `mdtUrl`, `connectTimeout`, `readTimeout`,
`mqttEndpoint`, `workflowManagerUrl`. No config at all → `IllegalArgumentException` (exit 1).

The connect function is an instance field of `AbstractMDTCommand` (default `HttpMDTManager::connect`). Tests create
subcommands through `MDTCommandsMain.newCommandLine(command, IFactory)` and inject a fake via `setConnector(...)`.
See `CommandRegressionTest` for the pattern (fake interfaces via `java.lang.reflect.Proxy`).

### Shared mixins

| Mixin | Options | Used by |
|---|---|---|
| [`ClientConfigOptions`](src/main/java/mdt/cli/ClientConfigOptions.java) | `--client-conf` | `AbstractMDTCommand` |
| [`RepeatOptions`](src/main/java/mdt/cli/RepeatOptions.java) | `--repeat <interval>` | `list *`, `get mdt-info`, `get element/parameter/argument/timeseries` |
| [`ListOutputOptions`](src/main/java/mdt/cli/list/ListOutputOptions.java) | `-o csv\|table`, `-t` | `list shells/submodels/operations/workflows/wfmodels` |
| [`OperationArgumentSpec`](src/main/java/mdt/cli/OperationArgumentSpec.java) | `<instance-id> <operation-id> <argument-id>`, `--kind` | `get argument`, `set argument` |

### Output rendering

- TABLE: `text-table-formatter` (`org.nocrala.tools.texttablefmt.Table`).
- TREE: `text-tree` (`org.barfuin.texttree`). Nodes live in [`mdt.tree`](src/main/java/mdt/tree/) (`*Node`,
  `NodeFactory`). A node's `getChildren()` must not make remote calls — the command reads values first and passes
  them in (see `GetInstanceParametersCommand`).
- CSV: build each line with [`CsvFormat`](src/main/java/mdt/cli/CsvFormat.java) (RFC 4180 quoting).
- JSON: AAS4J `JsonSerializer` / `MDTModelSerDe`.
- `--repeat`: [`PeriodicRefreshingConsole`](src/main/java/mdt/cli/PeriodicRefreshingConsole.java) (JLine). Its test
  calls `performPeriodicAction` directly so it works without a TTY.

### Task commands (`mdt run`)

[`mdt.cli.run`](src/main/java/mdt/cli/run/) provides `run operation|submodel|restful|set`. Task implementations
(`AASOperationTask`, `OperationSubmodelTask`, `SetTask`) are in `:mdt-client` `mdt.task`; `RESTfulAsyncRpcClient`
is in `:utils` `utils.rpc.restful`.

- [`DynamicArgumentsCommand`](src/main/java/mdt/cli/run/DynamicArgumentsCommand.java) — parses picocli `@Unmatched`
  `--in.<name>` / `--out.<name>` / `--inout.<name>` (no kind → input) into task arguments. The split form
  `--in.x 3` fails if the value token is consumed as a positional argument.
- [`AbstractRunTaskCommand`](src/main/java/mdt/cli/run/AbstractRunTaskCommand.java) — base of `operation`/`submodel`;
  adds `--poll`/`--timeout`/`--show-result` ([`OperationRunOptions`](src/main/java/mdt/cli/run/OperationRunOptions.java));
  runs the task from `newTask()`.
- `restful` ([`RESTfulAsyncRpcCommand`](src/main/java/mdt/cli/run/RESTfulAsyncRpcCommand.java)) and `set`
  ([`SetTaskCommand`](src/main/java/mdt/cli/run/SetTaskCommand.java)) extend `DynamicArgumentsCommand` directly.
  `restful` exits 1 if a requested output is missing from the RPC response.

`StopSimulationCommand` and `mdt.task.skku.SKKUSimulationTaskCommand` are not registered in any group (intentional;
do not register them without being asked).

## Coding rules

- **Validate in `validate()`**, not by overriding `run()`. Report bad input with `throw usageError("...")` (exit 2).
- **Write output through `out()` / `err()`** (picocli writers), never `System.out` / `System.err` — tests capture
  these writers. Results → `out()`; errors, progress, elapsed time → `err()`. Use `newOutWriter()` when a closeable
  writer is needed (closing it only flushes).
- **Partial failure:** when some targets fail, print the cause to `err()`, call `markFailed()`, and continue. Never
  swallow an exception and exit 0. For multi-instance operations, decide from mdt-client results
  (`StopMDTInstances.getResults()` → `InstanceOperationResult`; `StartInstances` failure listener).
- **Positional label vocabulary:** `instance-id`, `submodel-id`, `submodel-idShort`, `idShortPath`, `aas-id`,
  `parameter-id`, `operation-id`, `argument-id`, `workflow-name` (workflow instance), `model-id` (workflow model),
  `element-ref`. When you change labels or options, update [docs/commands.md](docs/commands.md) and Part 2 below.
- Argument checks use `utils.Preconditions` (not Guava).
- Javadoc/comment lines ≤ 110 columns (tab = 4).

## Packaging

`shadowJar`/`jar` exclude dependencies' `logback-test.xml` and `logback-spring.xml` and include only this
project's `logback.xml` (see `build.gradle`). At runtime `MDTCommandsMain.configureLogging()` uses
`$MDT_CLIENT_HOME/logback.xml` (or current dir) if present, otherwise the bundled
`src/main/resources/logback.xml` (INFO and above to stderr).

---

# Part 2. Using the `mdt` CLI

Covers how to map a task to a command, argument syntax, and common mistakes. For every option of every command, see
[docs/commands.md](docs/commands.md).

## 0. Checklist (every call)

1. **Form:** `sbin/mdt <group> <subcommand> [args...] [options...]`. A deployment needs `MDT_CLIENT_HOME`.
2. **Target:** a running MDTManager. Config order: `--client-conf <path>` → `$MDT_CLIENT_HOME/mdt_client_config.yaml`
   → env `MDT_URL`. Show the current config with `mdt endpoints`.
3. **If you will parse the result, always pass `-o json` (or `value` / `csv`).** Defaults are human-oriented
   `table`/`tree`. See §6.
4. **Discover before acting.** Unknown instance id → `mdt list instances -o json`. Unknown parameter/operation →
   `mdt get instance <instance-id> parameters -o json` / `... operations -o json`.
5. **Check the exit code.** 0 = success, 1 = failure (including partial failure across targets), 2 = usage error.
   Results go to stdout, errors to stderr.

## 1. Task → command

| Task | Command |
|---|---|
| List instances / status | `mdt list instances -o json` |
| Instance summary | `mdt get instance <instance-id> -o json` |
| Instance parameters | `mdt get instance <instance-id> parameters -o json` |
| Instance operations | `mdt get instance <instance-id> operations -o json` |
| Instance submodels | `mdt get instance <instance-id> submodels -o json` |
| Read a parameter **value** | `mdt get parameter <instance-id> <parameter-id> -o value` |
| Read any SubmodelElement value | `mdt get element --ref <element-ref> -o value` |
| Read an operation argument | `mdt get argument <instance-id> <operation-id> <argument-id> -o value` |
| Time series (recent) | `mdt get timeseries <instance-id> <submodel-idShort> --last <n\|dur> -o csv --header` |
| Time series (from a time) | `mdt get timeseries <instance-id> <submodel-idShort> --start <datetime> --count <n> -o csv --header` |
| Download a File element | `mdt get file <element-ref> -f <path>` |
| Write a parameter value | `mdt set parameter <instance-id> <parameter-id> --json '<json>'` |
| Write a SubmodelElement value | `mdt set element <instance-id> <submodel-idShort> <idShortPath> --json '<json>'` |
| Write an operation argument | `mdt set argument <instance-id> <operation-id> <argument-id> --json '<json>'` |
| Upload a file to a File element | `mdt set file <element-ref> <path> --mime-type <type>` |
| Run an AAS Operation | `mdt run operation <instance-id> <submodel-idShort> <idShortPath> --in.<n> <expr> --out.<n> <ref> --show-result` |
| Run an AI/Simulation submodel | `mdt run submodel <instance-id> <submodel-idShort> --in.<n> <expr> --out.<n> <ref> --show-result` |
| Start / stop instances | `mdt start instance <instance-id...>` / `mdt stop instance <instance-id...>` |
| Register / remove instances | `mdt add instance <instance-id> <path>` / `mdt remove instance <instance-id...>` |
| Start / stop a workflow | `mdt start workflow <model-id>` / `mdt stop workflow <workflow-name>` |
| Suspend / resume a workflow | `mdt suspend workflow <workflow-name>` / `mdt resume workflow <workflow-name>` |
| Resolve a reference to a URL | `mdt resolve reference <element-ref>` |
| Run a simulation | `mdt simulation run <instance-id\|submodel-id> -t <timeout>` |

For a single machine-readable value, `get ... -o value` is the easiest to parse (raw value, no decoration).

## 2. Reference expression (`element-ref`) syntax — most common source of errors

Used by `--ref`, the `element-ref` of `get/remove/set file`, and `run --in/--out/--inout` values that point at
elements. Grammar: [`MdtExpr.g4`](../mdt-client/src/main/antlr/MdtExpr.g4).

### 2.1 Submodel spec (base of other references)

| Form | Meaning | Example |
|---|---|---|
| `<inst>:<smIdShort>` | Instance submodel by idShort | `line01:Data` |
| `<inst>:idShort=<smIdShort>` | Same, explicit | `line01:idShort=Data` |
| `submodel:id=<submodelId>` | Submodel by global id | `submodel:id=https://ex/sm/Data` |

### 2.2 Reference kinds

| Prefix | Syntax | Meaning | Example |
|---|---|---|---|
| (none) | `<submodelSpec>:<idShortPath>` | SubmodelElement | `line01:Data:DataInfo.temperature` |
| `param:` | `param:<inst>:<idShortPath \| INT \| *>` | MDT parameter | `param:line01:Temperature`, `param:line01:*` |
| `oparg:` | `oparg:<submodelSpec>:<in\|out>:<name \| INT \| *>` | Operation-submodel argument | `oparg:line01:Simulation:in:0` |
| `opvar:` | `opvar:<defaultElementSpec>:<in\|out\|inout>:<INT>` | Operation element variable | `opvar:line01:AddAndSleep:Operation:out:1` |
| `timeseries:` | `timeseries:<submodelSpec>(#range)?(\|cols)?` | Time series | `timeseries:line01:Data#last=5\|current,power` |

- `idShortPath`: names joined by `.`; list items indexed with `[int]`. E.g. `records[0].value`.
- `param:<inst>:*` = the instance's whole parameter collection.
- An `mdt:` prefix (`mdt:<inst>...`) is also accepted for instance specs.

### 2.3 Time-series range / projection (`#` = range, `|` = columns)

| Fragment | Meaning | Example |
|---|---|---|
| `#last=<INT>` | Last N records | `#last=10` |
| `#last=<dur>` | Last duration | `#last=30s`, `#last=1h`, `#last=P1DT2H` |
| `#last=<dur>@<anchor>` | Anchor: `latest` (latest data time, default) or `now` | `#last=1h@now` |
| `#<from>~<to>` | Absolute range; either side optional | `#2024-01-01~2024-02-01`, `#~2024-02-01` |
| `#<from>+<INT>` | N records ascending from `from` (inclusive) | `#2024-01-01T09:00:00Z+100` |
| `\|colA,colB` | Column projection (default: all) | `\|current,power` |

- Duration: `ms`, `s`, `m`, `h`, `d`, or ISO-8601 (`PT2H30M`, `P1D`; no years/months/weeks).
- Timestamp: `YYYY-MM-DD` or `YYYY-MM-DDThh:mm[:ss[.fff]][Z|±hh:mm]`.
- Records are always returned in ascending time order.

## 3. Value literals (`set` / `run --in`)

Prefer JSON for `set ... --json '<...>'`. For `run --in.<n> <expr>`, `<expr>` is a reference **or** a literal:

| Kind | Syntax / example |
|---|---|
| Property | `25.3`, `-7`, `true`, `abc`, `"string with spaces"` |
| Multi-language text | `"온도"@ko` |
| File | `file:"/path/to/f.pdf"("application/pdf")` |
| Range | `[0,100]` |

- Negative numbers only in value literals (indexes, lengths, durations are unsigned).
- Quote the whole value in the shell when it has spaces/special chars: `--in.msg '"hi there"@en'`.

## 4. `run` dynamic arguments

| Form | Meaning | Allowed value |
|---|---|---|
| `--in.<name> <expr>` | Input (also `--<name>`) | reference **or** literal |
| `--out.<name> <ref>` | Output (where the result is written) | reference only |
| `--inout.<name> <ref>` | In/out | reference only |

- `run submodel` and `run restful` reject `--inout.*`.
- `run set` effectively requires `--in.source <expr>` and `--out.target <ref>`.
- `--show-result` prints results; `--timeout <dur>` (e.g. `5m`) and `--poll <dur>` control waiting.

```bash
# use param:line01:temp as input, write the result to param:line01:pred
mdt run submodel line01 Predict --in.input1 param:line01:temp --out.out1 param:line01:pred --show-result

# literal inputs, print the result
mdt run operation line01 Ops predict --in.x 3 --in.y 4 --show-result
```

## 5. Value source for `set element|parameter|argument`

Exactly one of:

| Option | Use |
|---|---|
| `--json '<json>'` | Inline JSON (recommended for short values) |
| `--json-file <path>` | JSON from a file |
| `--file <path> [--path <v>] [--mime-type <t>]` | File element content |

`set file <element-ref> <path>` instead takes the file as a positional argument.

```bash
mdt set parameter line01 Temperature --json '25.3'
mdt set element line01 Data DataInfo.status --json '"RUNNING"'
mdt set file param:line01:manual ./manual.pdf --mime-type application/pdf
```

## 6. Output formats (for parsing)

| Commands | Use for parsing | Notes |
|---|---|---|
| `list instances` | `-o json` | `csv` (default), `table`, `tree`, `json` |
| `list shells/submodels/operations/workflows/wfmodels` | `-o csv` (default; delimiter `-d`) | only `csv`, `table` (`-t`) |
| `get instance`, `get shell`, `get submodel` | `-o json` | default `table` |
| `get instance <instance-id> <sub>` (parameters, submodels, …) | `-o json` | default `tree` |
| `get element/parameter/argument` | `-o value` (single value) or `-o json` | default `tree` |
| `get timeseries` | `-o csv --header` (+ `-d`) | `csv`, `table`, `tree`, `json`, `value` |
| `get wfmodel`, `get workflow` | `-o json` | default `table` |

CSV fields containing the delimiter, `"`, or a newline are quoted per RFC 4180.

## 7. Common mistakes

- Forgetting `-o json|value|csv` → you get a human table/tree.
- `get instance <instance-id> log` has no `-o` / `-v` options.
- `--inout.*` is not allowed for `run submodel` / `run restful`.
- Single-quote wildcards to avoid shell expansion: `'param:line01:*'`, `-g 'line-*'`.
- Abbreviated options/subcommands are accepted, but use full names in scripts.
- Bulk targets: give **exactly one** of id list, `--all`, or filters (`--glob/-g`, `--model-filter`); mixing them is
  a usage error. In `remove workflow`, `--glob` and `--model-filter` together select the intersection.
  `remove instance --all` is destructive; `remove instance -f` stops running instances first.
- `start/stop/run/simulation` wait until the target state is reached. Use `--nowait` to return immediately and
  `--timeout` to bound the wait. If any target fails, the exit code is 1.
- Do not confuse a submodel idShort under an instance (`line01:Data`) with a global submodel id
  (`submodel:id=<...>`).
- If unsure of an id or name, look it up with `list` / `get ... -o json` before modifying anything.

## 8. Typical flow (discover → act)

```bash
mdt list instances -o json                                   # 1. what exists
mdt get instance line01 parameters -o json                   # 2. inspect the target
mdt get instance line01 operations -o json
mdt get parameter line01 Temperature -o value                # 3. read
mdt set parameter line01 Temperature --json '26.0'           # 4. write
mdt run submodel line01 Predict --in.in1 param:line01:Temperature \
    --out.out1 param:line01:Pred --show-result               # 5. run
mdt get parameter line01 Pred -o value                       #    and check
```

## Key sources

- Command tree: [`MDTCommandsMain`](src/main/java/mdt/cli/MDTCommandsMain.java)
- Execution order and exit codes: [`AbstractCommand`](src/main/java/mdt/cli/AbstractCommand.java),
  [`AbstractMDTCommand`](src/main/java/mdt/cli/AbstractMDTCommand.java)
- Reference grammar: [`MdtExpr.g4`](../mdt-client/src/main/antlr/MdtExpr.g4); parser entry
  [`ElementReferences.parseExpr`](../mdt-client/src/main/api/mdt/model/sm/ref/ElementReferences.java)
- Full command reference (Korean): [docs/commands.md](docs/commands.md)
