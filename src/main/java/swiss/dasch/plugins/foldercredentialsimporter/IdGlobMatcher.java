package swiss.dasch.plugins.foldercredentialsimporter;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.IdCredentials;

public class IdGlobMatcher implements CredentialsMatcher {

	private static final long serialVersionUID = -8345461594243392471L;

	private final GlobMatcher matcher;

	private boolean found;

	public IdGlobMatcher(String pattern) {
		this.matcher = new GlobMatcher(pattern);
	}

	public String pattern() {
		return this.matcher.pattern();
	}

	public boolean found() {
		return this.found;
	}

	public void reset() {
		this.found = false;
	}

	@Override
	public boolean matches(Credentials item) {
		if (item instanceof IdCredentials && this.matcher.matches(((IdCredentials) item).getId())) {
			this.found = true;
			return true;
		}
		return false;
	}

}
