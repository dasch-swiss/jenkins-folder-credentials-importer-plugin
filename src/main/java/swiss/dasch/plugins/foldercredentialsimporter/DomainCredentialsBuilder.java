package swiss.dasch.plugins.foldercredentialsimporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;

public class DomainCredentialsBuilder {

	private final Map<Domain, List<Credentials>> credentials = new LinkedHashMap<>();

	public void addCredentials(Domain domain, List<Credentials> credentials) {
		this.credentials.computeIfAbsent(domain, d -> new ArrayList<>()).addAll(credentials);
	}

	public void addCredentials(Domain domain, Credentials credentials) {
		this.addCredentials(domain, Collections.singletonList(credentials));
	}

	public void addCredentials(DomainCredentials credentials) {
		this.addCredentials(credentials.getDomain(), credentials.getCredentials());
	}

	public void addCredentials(List<DomainCredentials> credentials) {
		for (DomainCredentials dc : credentials) {
			this.addCredentials(dc);
		}
	}

	public List<DomainCredentials> build() {
		List<DomainCredentials> credentials = new ArrayList<>();

		for (Entry<Domain, List<Credentials>> entry : this.credentials.entrySet()) {
			credentials.add(new DomainCredentials(entry.getKey(), entry.getValue()));
		}

		return credentials;
	}

}
