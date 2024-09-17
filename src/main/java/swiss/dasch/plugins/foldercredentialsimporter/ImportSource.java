package swiss.dasch.plugins.foldercredentialsimporter;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsScope;

public class ImportSource implements Serializable {

	private static final long serialVersionUID = 2338224388367433381L;

	public CredentialsSource[] sources;
	public CredentialsScope[] scopes;
	public String[] uris;
	public String[] domains;
	public String[] ids;
	public boolean required;

	@DataBoundConstructor
	public ImportSource(String... ids) {
		this.ids = ids != null ? ids.clone() : null;
	}

	@DataBoundSetter
	public void setSources(CredentialsSource... sources) {
		this.sources = sources != null ? sources.clone() : null;
	}

	@DataBoundSetter
	public void setScopes(CredentialsScope... scopes) {
		this.scopes = scopes != null ? scopes.clone() : null;
	}

	@DataBoundSetter
	public void setUris(String... uris) {
		this.uris = uris != null ? uris.clone() : null;
	}

	@DataBoundSetter
	public void setDomains(String... domains) {
		this.domains = domains != null ? domains.clone() : null;
	}

	@DataBoundSetter
	public void setRequired(boolean required) {
		this.required = required;
	}

}
