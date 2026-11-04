package mdt.cli;

import java.util.List;

import utils.func.Funcs;
import utils.stream.FStream;

import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTOperationDescriptor;
import mdt.model.instance.MDTOperationDescriptor.ArgumentDescriptor;
import mdt.model.sm.ref.MDTArgumentKind;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;


/**
 * MDTInstance 연산(AI·Simulation)의 인자 하나를 지정하는 picocli mixin.
 * <p>
 * 위치 인자 {@code <id> <operation> <argument>}와 선택 옵션 {@code --kind in|out}을 정의하고,
 * {@link #toReferenceExpr(MDTInstanceManager)}로 그 인자를 가리키는 참조 표현식을 찾는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class OperationArgumentSpec {
	@Spec(Spec.Target.MIXEE) private CommandSpec m_mixee;
	
	@Parameters(index="0", paramLabel="instance-id", description="Target MDTInstance id.")
	private String m_instanceId;

	@Parameters(index="1", paramLabel="operation-id", description="Target operation (AI or Simulation) id")
	private String m_opId;

	@Parameters(index="2", paramLabel="argument-id", description="Target argument id")
	private String m_argumentId;

	@Option(names={"--kind"}, paramLabel="in|out",
			description="Argument kind. Needed only when an input and an output argument share the id.")
	private String m_kind;

	/**
	 * 지정된 연산 인자를 가리키는 참조 표현식을 연산 descriptor에서 찾는다.
	 * <p>
	 * {@code --kind}가 없으면 입력·출력 인자에서 모두 찾으며, 양쪽에 같은 id가 있으면 어느 쪽인지 정할 수 없으므로
	 * 오류로 처리한다.
	 *
	 * @param manager	MDTInstance를 찾을 매니저.
	 * @return	인자의 참조 표현식.
	 * @throws IllegalArgumentException	연산이나 인자가 없는 경우.
	 * @throws CommandLine.ParameterException	{@code --kind} 값이 잘못되었거나, 인자 id가 입력·출력에 모두 있는데
	 * 											{@code --kind}가 없는 경우.
	 */
	public String toReferenceExpr(MDTInstanceManager manager) {
		MDTOperationDescriptor op = Funcs.findFirst(manager.getInstance(m_instanceId).getMDTOperationDescriptorAll(),
													o -> o.getId().equals(m_opId));
		if ( op == null ) {
			throw new IllegalArgumentException("No such operation id=" + m_opId + " in the instance " + m_instanceId);
		}

		MDTArgumentKind kind = null;
		if ( m_kind != null ) {
			try {
				kind = MDTArgumentKind.fromString(m_kind);
			}
			catch ( IllegalArgumentException e ) {
				throw usageError("invalid '--kind' value (candidates: in, out): " + m_kind);
			}
		}
		String inRef = (kind != MDTArgumentKind.OUTPUT) ? findReference(op.getInputArguments()) : null;
		String outRef = (kind != MDTArgumentKind.INPUT) ? findReference(op.getOutputArguments()) : null;
		if ( inRef != null && outRef != null ) {
			throw usageError(String.format("Both input and output arguments have id=%s in the operation %s; "
											+ "specify '--kind in' or '--kind out'", m_argumentId, m_opId));
		}
		if ( inRef == null && outRef == null ) {
			String kindStr = (kind != null) ? kind.name().toLowerCase() + " " : "";
			throw new IllegalArgumentException(String.format("No such %sargument id=%s in the operation %s",
															kindStr, m_argumentId, m_opId));
		}
		return (inRef != null) ? inRef : outRef;
	}

	private CommandLine.ParameterException usageError(String message) {
		return new CommandLine.ParameterException(m_mixee.commandLine(), message);
	}
	
	private String findReference(List<ArgumentDescriptor> args) {
		return FStream.from(args)
						.findFirst(arg -> arg.getId().equals(m_argumentId))
						.map(ArgumentDescriptor::getReference)
						.getOrNull();
	}
}
