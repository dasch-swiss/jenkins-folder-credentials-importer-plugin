package swiss.dasch.plugins.foldercredentialsimporter;

import java.util.Collections;
import java.util.List;

import com.cloudbees.plugins.credentials.domains.DomainCredentials;

public class ImportedCredentials {

	private static final ImportedCredentials EMPTY = new ImportedCredentials();

	public final transient List<DomainCredentials> credentials;
	public final boolean clear;

	public ImportedCredentials(List<DomainCredentials> credentials, boolean clear) {
		this.credentials = Collections.unmodifiableList(credentials);
		this.clear = clear;
	}

	private ImportedCredentials() {
		this.credentials = Collections.emptyList();
		this.clear = false;
	}

	public static ImportedCredentials empty() {
		return EMPTY;
	}

}
