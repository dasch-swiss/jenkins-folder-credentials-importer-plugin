package swiss.dasch.plugins.foldercredentialsimporter;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;

public class ImportTarget implements Serializable {

	private static final long serialVersionUID = 374557176124519609L;

	public CredentialsScope scope;
	public String[] copiedDomains;
	public Domain defaultDomain;

	@DataBoundConstructor
	public ImportTarget() {
	}

	@DataBoundSetter
	public void setScope(CredentialsScope scope) {
		this.scope = scope;
	}

	@DataBoundSetter
	public void setCopiedDomains(String[] copiedDomains) {
		this.copiedDomains = copiedDomains;
	}

	@DataBoundSetter
	public void setDefaultDomain(Domain defaultDomain) {
		this.defaultDomain = defaultDomain;
	}

}
