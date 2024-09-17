package swiss.dasch.plugins.foldercredentialsimporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;

public class DomainCredentialsFilter implements CredentialsMatcher {

	private static final long serialVersionUID = 4427891374130338662L;

	private final Set<String> filterSet = new HashSet<>();
	private final Map<String, Domain> domainMap = new HashMap<>();
	private final boolean matchesAny;

	private DomainCredentialsFilter(Set<String> filterSet, Map<String, Domain> domainMap) {
		this.filterSet.addAll(filterSet);
		this.domainMap.putAll(domainMap);
		this.matchesAny = false;
	}

	private DomainCredentialsFilter(boolean matchesAny, Map<String, Domain> domainMap) {
		this.matchesAny = matchesAny;
		this.domainMap.putAll(domainMap);
	}

	public static DomainCredentialsFilter some(Set<String> filterSet) {
		return new DomainCredentialsFilter(filterSet, Collections.emptyMap());
	}

	public static DomainCredentialsFilter some(Set<String> filterSet, Map<String, Domain> domainMap) {
		return new DomainCredentialsFilter(filterSet, domainMap);
	}

	public static DomainCredentialsFilter none() {
		return new DomainCredentialsFilter(false, Collections.emptyMap());
	}

	public static DomainCredentialsFilter none(Map<String, Domain> domainMap) {
		return new DomainCredentialsFilter(false, domainMap);
	}

	public static DomainCredentialsFilter any() {
		return new DomainCredentialsFilter(true, Collections.emptyMap());
	}

	public static DomainCredentialsFilter any(Map<String, Domain> domainMap) {
		return new DomainCredentialsFilter(true, domainMap);
	}

	@Nullable
	public Domain getDomain(String credentialsId) {
		return this.domainMap.get(credentialsId);
	}

	@Nullable
	public static Domain getDomain(String credentialsIds, DomainCredentialsFilter... filters) {
		for (DomainCredentialsFilter filter : filters) {
			Domain domain = filter.getDomain(credentialsIds);
			if (domain != null) {
				return domain;
			}
		}
		return null;
	}

	@Override
	public boolean matches(Credentials item) {
		return this.matchesAny
				|| (item instanceof IdCredentials && this.filterSet.contains(((IdCredentials) item).getId()));
	}

}
