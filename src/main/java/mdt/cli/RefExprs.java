package mdt.cli;

import mdt.model.expr.MDTExpressionParser;


/**
 * 명령 인자(인스턴스 id, Submodel idShort, idShort 경로, 파라미터 id)로 MdtExpr 참조 표현식을 만드는 유틸리티.
 * <p>
 * 각 구성요소는 {@link MDTExpressionParser#toNameExpr(String)}/{@link MDTExpressionParser#toIdShortPathExpr(String)}로
 * 감싸므로, 예약어나 공백·{@code :}가 들어 있는 이름도 그대로 가리킨다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class RefExprs {
	private RefExprs() {
		throw new AssertionError("Should not be called: class=" + RefExprs.class.getName());
	}

	/**
	 * Submodel 참조 표현식({@code <instance>:<submodel>})을 만든다.
	 *
	 * @param instanceId	MDTInstance 식별자.
	 * @param smIdShort		Submodel idShort.
	 * @return	Submodel 참조 표현식.
	 */
	public static String submodelRef(String instanceId, String smIdShort) {
		return MDTExpressionParser.toNameExpr(instanceId) + ":" + MDTExpressionParser.toNameExpr(smIdShort);
	}

	/**
	 * 요소 참조 표현식({@code <instance>:<submodel>:<idShortPath>})을 만든다.
	 *
	 * @param instanceId	MDTInstance 식별자.
	 * @param smIdShort		Submodel idShort.
	 * @param idShortPath	SubmodelElement의 idShort 경로.
	 * @return	요소 참조 표현식.
	 */
	public static String elementRef(String instanceId, String smIdShort, String idShortPath) {
		return submodelRef(instanceId, smIdShort) + ":" + MDTExpressionParser.toIdShortPathExpr(idShortPath);
	}

	/**
	 * 파라미터 참조 표현식({@code param:<instance>:<parameter>})을 만든다.
	 * <p>
	 * 파라미터 자리의 {@code *}(파라미터 전체)와 숫자(파라미터 인덱스)는 표현식 문법의 일부이므로 그대로 쓴다.
	 *
	 * @param instanceId	MDTInstance 식별자.
	 * @param parameterId	파라미터 식별자, 인덱스 또는 {@code *}.
	 * @return	파라미터 참조 표현식.
	 */
	public static String parameterRef(String instanceId, String parameterId) {
		String paramExpr = (parameterId.equals("*") || parameterId.matches("\\d+"))
							? parameterId : MDTExpressionParser.toNameExpr(parameterId);
		return "param:" + MDTExpressionParser.toNameExpr(instanceId) + ":" + paramExpr;
	}
}
