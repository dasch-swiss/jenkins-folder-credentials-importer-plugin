package swiss.dasch.plugins.foldercredentialsimporter;

import java.io.Serializable;
import java.util.Arrays;

import com.cloudbees.plugins.credentials.domains.PathRequirement;
import com.cloudbees.plugins.credentials.domains.PathSpecification;

public class GlobMatcher implements Serializable {

	private static final long serialVersionUID = -2718289044026580295L;

	private final String pattern;
	private final PathSpecification pathSpecification;

	public GlobMatcher(String pattern) {
		this.pattern = pattern;
		String pathPattern = "/" + String.join(",/",
				Arrays.asList(pattern.split(",")).stream().map(String::trim).toArray(i -> new String[i]));
		this.pathSpecification = new PathSpecification(pathPattern, null, true);
	}

	public String pattern() {
		return this.pattern;
	}

	public boolean matches(String str) {
		return this.pathSpecification.test(new PathRequirement(str)).isMatch();
	}

}
