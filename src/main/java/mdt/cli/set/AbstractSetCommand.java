package mdt.cli.set;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.tika.Tika;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.jetbrains.annotations.Nullable;

import utils.io.IOUtils;
import utils.func.Nullables;

import mdt.cli.AbstractMDTCommand;
import mdt.model.SubmodelService;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.sm.ref.ElementReference;
import mdt.model.sm.ref.MDTElementReference;
import mdt.model.sm.value.FileValue;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractSetCommand extends AbstractMDTCommand {
	@ArgGroup(exclusive=true, multiplicity="1")
	private SourceSpec m_source;
	
	static class SourceSpec {
		@Option(names={"--json-file"}, paramLabel="path",
				description="Path to a value-only JSON file ('-' for standard input)")
		private String m_jsonFilePath;
		
		/** 이전 옵션 이름 {@code --jsonFile}(도움말에는 표시하지 않는다). */
		@Option(names={"--jsonFile"}, paramLabel="path", hidden=true)
		private void setJsonFilePathAlias(String path) {
			m_jsonFilePath = path;
		}
		
		@Option(names={"--json"}, paramLabel="json",
				description="Value-only JSON string (e.g. 25.3, \"\\\"abc\\\"\", {\"Field\": 1})")
		private String m_json;

		@ArgGroup(exclusive = false)
		FileElementSpec m_fileElementSpec;
	}
	
	static class FileElementSpec {
		@Option(names = {"--file"}, paramLabel="file path", required=true)
		private File m_file;
		
		@Option(names = {"--path"}, paramLabel="value for File", required=false)
		private String m_path;
		
		@Option(names = {"--mime-type"}, paramLabel="contentType for File", required=false)
		private String m_mimeType;
		
		/** 이전 옵션 이름 {@code --mimeType}(도움말에는 표시하지 않는다). */
		@Option(names = {"--mimeType"}, hidden=true)
		private void setMimeTypeAlias(String mimeType) {
			m_mimeType = mimeType;
		}
	}

	protected void run(MDTInstanceManager manager, MDTElementReference targetRef) throws Exception {
		// 대상 참조는 여기서 한 번만 활성화한다.
		targetRef.activate(manager);
		
		if ( m_source.m_json != null ) {
			targetRef.updateFromValueJson(m_source.m_json);
		}
		else if ( m_source.m_jsonFilePath != null ) {
			setWithJsonFile(targetRef, m_source.m_jsonFilePath);
		}
		else if ( m_source.m_fileElementSpec != null ) {
			FileElementSpec spec = m_source.m_fileElementSpec;
			putAttachment(targetRef, spec.m_file, spec.m_path, spec.m_mimeType);
		}
		else {
			throw new IllegalArgumentException("Value is not specified");
		}
	}
	
	/**
	 * JSON 파일(또는 {@code -}이면 표준 입력)에 담긴 value-only JSON으로 대상 요소의 값을 갱신한다.
	 *
	 * @param target		대상 요소의 참조.
	 * @param jsonFilePath	JSON 파일 경로. {@code -}이면 표준 입력에서 읽는다.
	 * @throws IOException	파일을 읽지 못했거나 값을 갱신하지 못한 경우.
	 */
	private void setWithJsonFile(ElementReference target, String jsonFilePath) throws IOException {
		String jsonStr = jsonFilePath.equals("-")
						? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
						: IOUtils.toString(new File(jsonFilePath));
		target.updateFromValueJson(jsonStr);
	}
	
	/**
	 * 로컬 파일을 참조가 가리키는 File 요소의 내용으로 올린다.
	 * <p>
	 * {@code set element --file}과 {@code set file}이 함께 쓴다.
	 *
	 * @param target	활성화된 대상 참조. File 요소를 가리켜야 한다.
	 * @param file		올릴 로컬 파일.
	 * @param path		File 요소에 기록할 경로 값. {@code null}이면 {@code file}의 이름.
	 * @param mimeType	content type. {@code null}이면 파일 내용으로 추정한다.
	 * @throws IllegalArgumentException	대상이 File 요소가 아닌 경우.
	 * @throws IOException	파일을 읽거나 올리지 못한 경우.
	 */
	static void putAttachment(MDTElementReference target, File file, @Nullable String path,
								@Nullable String mimeType) throws IOException {
		SubmodelElement sme = target.read();
		if ( !(sme instanceof org.eclipse.digitaltwin.aas4j.v3.model.File) ) {
			throw new IllegalArgumentException("Target element is not a File: " + target);
		}
		
		String filePath = Nullables.orGet(path, file::getName);
		String contentType = (mimeType != null) ? mimeType : new Tika().detect(file);
		SubmodelService svc = target.getSubmodelService();
		svc.putAttachmentByPath(target.getIdShortPathString(), new FileValue(filePath, contentType), file);
	}
}