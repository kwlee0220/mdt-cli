package mdt.cli;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import utils.func.FOption;

import mdt.client.MDTClientConfig;

import picocli.CommandLine.Option;


/**
 * MDTManager 클라이언트 설정 파일 옵션({@code --client-conf})과 설정 탐색을 담은 picocli mixin.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ClientConfigOptions {
	/**
	 * 사용자가 명시적으로 지정한 MDTManager 클라이언트 설정 파일 경로.
	 * <p>
	 * 지정되지 않은 경우 기본 위치({@link MDTClientConfig#loadDefault()})에서 설정을 찾는다.
	 */
	@Option(names={"--client-conf"}, paramLabel="path", required=false,
			description={"MDTManager configuration file path"})
	private File m_clientConfigFile;

	/** 이전 옵션 이름 {@code --client_conf}(도움말에는 표시하지 않는다). */
	@Option(names={"--client_conf"}, hidden=true)
	private void setClientConfigFileAlias(File file) {
		m_clientConfigFile = file;
	}

	/**
	 * {@code --client-conf}로 지정된 설정 파일을 반환한다.
	 *
	 * @return	설정 파일. 지정되지 않았으면 {@code null}.
	 */
	public @Nullable File getConfigFile() {
		return m_clientConfigFile;
	}
	
	/**
	 * 클라이언트 설정을 찾는다.
	 * <p>
	 * {@code --client-conf}가 지정되었으면 그 파일을 읽고, 그렇지 않으면 {@link MDTClientConfig#loadDefault()}로
	 * 클라이언트 홈 디렉토리의 설정 파일 또는 환경 변수 {@code MDT_URL}에서 찾는다.
	 *
	 * @return	찾은 설정. 어디에도 없으면 빈 값.
	 * @throws IOException	설정 파일을 읽거나 해석하지 못한 경우.
	 */
	public FOption<MDTClientConfig> getClientConfig() throws IOException {
		return loadClientConfig(m_clientConfigFile);
	}
	
	/**
	 * 주어진 설정 파일, 없으면 기본 위치({@link MDTClientConfig#loadDefault()})에서 클라이언트 설정을 찾는다.
	 *
	 * @param configFile	설정 파일. {@code null}이면 기본 위치에서 찾는다.
	 * @return	찾은 설정. 어디에도 없으면 빈 값.
	 * @throws IOException	설정 파일을 읽거나 해석하지 못한 경우.
	 */
	public static FOption<MDTClientConfig> loadClientConfig(@Nullable File configFile) throws IOException {
		return (configFile != null) ? FOption.of(MDTClientConfig.load(configFile)) : MDTClientConfig.loadDefault();
	}
}
