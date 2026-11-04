package mdt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import utils.http.RESTfulIOException;

import mdt.client.MDTClientConfig;
import mdt.model.MDTManager;
import mdt.model.ResourceNotFoundException;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceStatus;
import mdt.workflow.Workflow;
import mdt.workflow.WorkflowManager;
import mdt.workflow.WorkflowModel;
import mdt.workflow.WorkflowStatus;

import picocli.CommandLine;


/**
 * CLI 명령의 대상 선택, 종료 코드, 설정 전달에 대한 회귀 테스트.
 * <p>
 * {@link AbstractMDTCommand#s_connector}를 가짜 {@link MDTManager}를 돌려주는 함수로 바꿔, MDTManager 없이
 * {@code mdt} 진입점과 같은 설정의 {@link CommandLine}으로 명령을 실행한다.
 */
public class CommandRegressionTest {
	private Function<MDTClientConfig, MDTManager> m_originalConnector;
	private Path m_confFile;

	/** 가짜 매니저가 받은 호출 기록. */
	private final List<String> m_calls = new CopyOnWriteArrayList<>();
	/** 가짜 매니저에 접속할 때 받은 설정의 MDT URL. */
	private final List<String> m_connectedUrls = new CopyOnWriteArrayList<>();

	private List<Workflow> m_workflows = List.of();
	private List<WorkflowModel> m_wfModels = List.of();
	private Map<String, MDTInstance> m_instances = Map.of();
	private boolean m_failWorkflowList = false;

	@BeforeEach
	public void setUp(@TempDir Path tempDir) throws IOException {
		m_originalConnector = AbstractMDTCommand.s_connector;
		AbstractMDTCommand.s_connector = config -> {
			m_connectedUrls.add(config.getMdtUrl());
			return newManager();
		};
		m_confFile = writeConfig(tempDir, "conf.yaml", "http://localhost:1");
	}

	@AfterEach
	public void tearDown() {
		AbstractMDTCommand.s_connector = m_originalConnector;
	}

	// ---------------------------------------------------------------------------------------------
	// R1: 워크플로우·워크플로우 모델 삭제 대상 선택
	// ---------------------------------------------------------------------------------------------

	@Test
	public void removeWorkflowWithoutTargetIsUsageError() {
		Result result = run("remove", "workflow", "--client-conf", m_confFile.toString());
		assertEquals(2, result.exitCode());
		assertTrue(m_connectedUrls.isEmpty(), "validation must run before connecting");
	}

	@Test
	public void removeWorkflowWithNamesAndAllIsUsageError() {
		Result result = run("remove", "workflow", "--client-conf", m_confFile.toString(), "--all", "wf-1");
		assertEquals(2, result.exitCode());
		assertTrue(m_calls.isEmpty());
	}

	@Test
	public void removeWorkflowAppliesGlobAndModelFilterTogether() {
		m_workflows = List.of(workflow("a-1", "m1"), workflow("a-2", "m2"), workflow("b-1", "m1"));

		Result result = run("remove", "workflow", "--client-conf", m_confFile.toString(),
							"--glob", "a-*", "--model-filter", "m1");
		assertEquals(0, result.exitCode(), result.err());
		assertEquals(List.of("removeWorkflow(a-1)"), callsStartingWith("removeWorkflow"));
	}

	@Test
	public void removeWorkflowModelWithIdsAndGlobIsUsageError() {
		Result result = run("remove", "wfmodel", "--client-conf", m_confFile.toString(), "--glob", "m*", "m1");
		assertEquals(2, result.exitCode());
		assertTrue(m_calls.isEmpty());
	}

	// ---------------------------------------------------------------------------------------------
	// R2: 'remove instance --all --force'에서 정지 실패
	// ---------------------------------------------------------------------------------------------

	@Test
	public void removeAllForceAbortsWhenStopFails() {
		m_instances = Map.of("a", instance("a", MDTInstanceStatus.RUNNING, true));

		Result result = run("remove", "instance", "--client-conf", m_confFile.toString(), "--all", "--force");
		assertEquals(1, result.exitCode());
		assertTrue(callsStartingWith("removeInstance").isEmpty(), "nothing must be removed: " + m_calls);
		assertTrue(result.err().contains("a"), result.err());
	}

	// ---------------------------------------------------------------------------------------------
	// R3: 여러 인스턴스 중 일부 실패와 목록 조회 실패의 종료 코드
	// ---------------------------------------------------------------------------------------------

	@Test
	public void stopInstanceReportsPartialFailure() {
		m_instances = Map.of("a", instance("a", MDTInstanceStatus.RUNNING, false),
							"b", instance("b", MDTInstanceStatus.RUNNING, true));

		Result result = run("stop", "instance", "--client-conf", m_confFile.toString(), "--nowait", "a", "b", "x");
		assertEquals(1, result.exitCode());
		assertTrue(m_calls.contains("stop(a)"), m_calls.toString());
		assertTrue(result.err().contains("id=b"), result.err());
		assertTrue(result.err().contains("id=x"), result.err());
		assertTrue(!result.err().contains("id=a"), result.err());
	}

	@Test
	public void stopInstanceSucceedsWhenAllStopped() {
		m_instances = Map.of("a", instance("a", MDTInstanceStatus.RUNNING, false),
							"c", instance("c", MDTInstanceStatus.STOPPED, false));

		Result result = run("stop", "instance", "--client-conf", m_confFile.toString(), "--nowait", "a", "c");
		assertEquals(0, result.exitCode(), result.err());
		assertEquals(List.of("stop(a)"), callsStartingWith("stop"));
	}

	@Test
	public void listWorkflowsFailsOnIOError() {
		m_failWorkflowList = true;

		Result result = run("list", "workflows", "--client-conf", m_confFile.toString());
		assertEquals(1, result.exitCode());
		assertTrue(result.err().startsWith("error:"), result.err());
	}

	// ---------------------------------------------------------------------------------------------
	// R4: 하위 명령으로의 '--client-conf' 전달
	// ---------------------------------------------------------------------------------------------

	@Test
	public void subcommandUsesParentClientConf(@TempDir Path tempDir) throws IOException {
		Path conf2 = writeConfig(tempDir, "conf2.yaml", "http://localhost:2");

		run("get", "instance", "demo", "--client-conf", conf2.toString(), "parameters");
		assertEquals(List.of("http://localhost:2"), m_connectedUrls);
	}

	@Test
	public void subcommandClientConfOverridesParent(@TempDir Path tempDir) throws IOException {
		Path conf2 = writeConfig(tempDir, "conf2.yaml", "http://localhost:2");

		run("get", "instance", "demo", "--client-conf", conf2.toString(),
			"parameters", "--client-conf", m_confFile.toString());
		assertEquals(List.of("http://localhost:1"), m_connectedUrls);
	}

	// ---------------------------------------------------------------------------------------------
	// R5: 'get workflow <workflow-name> log'
	// ---------------------------------------------------------------------------------------------

	@Test
	public void workflowLogUsesWorkflowName() {
		Result result = run("get", "workflow", "wf-7", "--client-conf", m_confFile.toString(),
							"log", "--pod", "pod-1");
		assertEquals(0, result.exitCode(), result.err());
		assertEquals(List.of("getWorkflowLog(wf-7,pod-1)"), callsStartingWith("getWorkflowLog"));
		assertEquals("LOG", result.out());
	}

	// ---------------------------------------------------------------------------------------------
	// 도우미
	// ---------------------------------------------------------------------------------------------

	private record Result(int exitCode, String out, String err) { }

	private Result run(String... args) {
		StringWriter out = new StringWriter();
		StringWriter err = new StringWriter();
		CommandLine cl = MDTCommandsMain.newCommandLine(new MDTCommandsMain());
		cl.setOut(new PrintWriter(out, true));
		cl.setErr(new PrintWriter(err, true));
		int exitCode = cl.execute(args);
		return new Result(exitCode, out.toString(), err.toString());
	}

	private List<String> callsStartingWith(String prefix) {
		List<String> calls = new ArrayList<>();
		for ( String call: m_calls ) {
			if ( call.startsWith(prefix + "(") ) {
				calls.add(call);
			}
		}
		return calls;
	}

	private static Path writeConfig(Path dir, String name, String mdtUrl) throws IOException {
		return Files.writeString(dir.resolve(name), "mdtUrl: " + mdtUrl + "\n");
	}

	private static Workflow workflow(String name, String modelId) {
		return new Workflow(name, modelId, WorkflowStatus.RUNNING, null, null, null, Collections.emptyList());
	}

	private MDTManager newManager() {
		MDTInstanceManager instMgr = fake(MDTInstanceManager.class, (method, args) -> switch ( method ) {
			case "getInstance" -> {
				MDTInstance inst = m_instances.get((String)args[0]);
				if ( inst == null ) {
					throw new ResourceNotFoundException("MDTInstance", "id=" + args[0]);
				}
				yield inst;
			}
			case "getInstanceAllByFilter" -> m_instances.values().stream().filter(inst -> inst.isRunning()).toList();
			case "removeInstance" -> record("removeInstance(" + args[0] + ")");
			case "removeInstanceAll" -> record("removeInstanceAll()");
			default -> UNHANDLED;
		});
		WorkflowManager wfMgr = fake(WorkflowManager.class, (method, args) -> switch ( method ) {
			case "getWorkflowAll" -> {
				if ( m_failWorkflowList ) {
					throw new RESTfulIOException("connection refused");
				}
				yield m_workflows;
			}
			case "getWorkflowModelAll" -> m_wfModels;
			case "removeWorkflow" -> record("removeWorkflow(" + args[0] + ")");
			case "removeWorkflowAll" -> record("removeWorkflowAll()");
			case "removeWorkflowModel" -> record("removeWorkflowModel(" + args[0] + ")");
			case "removeWorkflowModelAll" -> record("removeWorkflowModelAll()");
			case "getWorkflowLog" -> {
				record("getWorkflowLog(" + args[0] + "," + args[1] + ")");
				yield "LOG";
			}
			default -> UNHANDLED;
		});
		return fake(MDTManager.class, (method, args) -> switch ( method ) {
			case "getInstanceManager" -> instMgr;
			case "getWorkflowManager" -> wfMgr;
			default -> UNHANDLED;
		});
	}

	private MDTInstance instance(String id, MDTInstanceStatus status, boolean failToStop) {
		return fake(MDTInstance.class, (method, args) -> switch ( method ) {
			case "getId" -> id;
			case "getStatus" -> status;
			case "stop" -> {
				if ( failToStop ) {
					throw new IllegalStateException("cannot stop " + id);
				}
				record("stop(" + id + ")");
				yield MDTInstanceStatus.STOPPED;
			}
			default -> UNHANDLED;
		});
	}

	private Object record(String call) {
		m_calls.add(call);
		return null;
	}

	/** {@link FakeHandler}가 처리하지 않는 메소드임을 나타내는 값. */
	private static final Object UNHANDLED = new Object();

	@FunctionalInterface
	private interface FakeHandler {
		Object handle(String method, Object[] args) throws Throwable;
	}

	/**
	 * 인터페이스의 가짜 구현을 만든다. {@code handler}가 {@link #UNHANDLED}를 돌려준 메소드는 default 구현이
	 * 있으면 그것을 쓰고, 없으면 {@link UnsupportedOperationException}을 던진다.
	 */
	private static <T> T fake(Class<T> intfc, FakeHandler handler) {
		InvocationHandler ih = (proxy, method, args) -> {
			if ( method.getDeclaringClass() == Object.class ) {
				return switch ( method.getName() ) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> intfc.getSimpleName() + "@fake";
				};
			}
			Object result = handler.handle(method.getName(), (args != null) ? args : new Object[0]);
			if ( result != UNHANDLED ) {
				return result;
			}
			if ( method.isDefault() ) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			throw new UnsupportedOperationException(intfc.getSimpleName() + "." + method.getName());
		};
		return intfc.cast(Proxy.newProxyInstance(intfc.getClassLoader(), new Class<?>[] { intfc }, ih));
	}
}
