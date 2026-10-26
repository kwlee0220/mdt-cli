package mdt.cli;

import picocli.CommandLine.IVersionProvider;


/**
 * {@code mdt} 명령의 {@code -V/--version} 출력에 사용되는 버전 정보 제공자.
 * <p>
 * 버전은 실행 jar의 manifest에 기록된 {@code Implementation-Version} 속성 값을 사용한다.
 * 이 값은 빌드 시 환경 변수 {@code MDT_BUILD_VERSION}으로 지정된 버전이며
 * ({@code build.gradle}의 {@code shadowJar} 설정 참조), jar가 아닌 클래스 디렉토리에서 실행하는 등
 * manifest 정보를 얻을 수 없는 경우에는 {@code "unknown"}을 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTVersionProvider implements IVersionProvider {
	private static final String UNKNOWN_VERSION = "unknown";

	@Override
	public String[] getVersion() {
		String version = MDTVersionProvider.class.getPackage().getImplementationVersion();
		return new String[] { (version != null) ? version : UNKNOWN_VERSION };
	}
}
