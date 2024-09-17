package swiss.dasch.plugins.foldercredentialsimporter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;

import hudson.security.ACL;
import hudson.security.AccessControlled;

public class Importer implements Serializable {

	private static final long serialVersionUID = 7753878291107491892L;

	// Used to force an update of the config/credentials with setUpdate(true)
	@SuppressWarnings("unused")
	private UUID uid = UUID.randomUUID();
	private Import[] imports;
	private boolean clear;

	private final boolean isSystemImporter;

	private transient ImportedCredentials credentials;

	public Importer(Import[] imports, boolean isSystemImporter) {
		this.imports = imports;
		this.isSystemImporter = isSystemImporter;
	}

	public synchronized void setClear(boolean clear) {
		this.clear = clear;
	}

	public synchronized void setUpdate(boolean update) {
		if (update) {
			this.uid = UUID.randomUUID();
		} else {
			this.uid = null;
		}
	}

	@SuppressWarnings("deprecation")
	public synchronized Importer fill(AccessControlled acl) {
		DomainCredentialsBuilder builder = new DomainCredentialsBuilder();

		for (Import i : imports) {
			if (this.isSystemImporter) {
				// Use SYSTEM for authorization
				builder.addCredentials(i.findCredentialsAuthorized(null, ACL.SYSTEM, this.clear).credentials);
			} else {
				// Otherwise use current authentication and run/executable for authorization
				builder.addCredentials(i.findCredentials(acl, this.clear).credentials);
			}
		}

		this.credentials = new ImportedCredentials(builder.build(), this.clear);

		return this;
	}

	public synchronized ImportedCredentials retrieve() {
		try {
			return this.credentials != null ? this.credentials : ImportedCredentials.empty();
		} finally {
			this.credentials = null;
		}
	}

	public synchronized void into(CredentialsStore store) {
		ImportedCredentials imported = this.retrieve();

		Map<Domain, Set<String>> addedCredentials = addStoreCredentials(store, imported.credentials);

		if (imported.clear) {
			clearStoreCredentials(store, addedCredentials);
		}
	}

	private static Map<Domain, Set<String>> addStoreCredentials(CredentialsStore store,
			List<DomainCredentials> credentials) {
		Map<Domain, Set<String>> addedCredentials = new HashMap<>();

		for (DomainCredentials domainCredentials : credentials) {
			Domain domain = domainCredentials.getDomain();

			if (store.isDomainsModifiable()) {
				try {
					Domain existingDomain = store.getDomainByName(domain.getName());
					if (existingDomain == null || !store.updateDomain(existingDomain, domain)) {
						store.addDomain(domain, Collections.emptyList());
					}
				} catch (IOException e) {
					throw new RuntimeException(Messages.FolderCredentialsImporter_DomainException(domain.getName()), e);
				}
			} else {
				domain = Domain.global();
			}

			for (Credentials credential : new ArrayList<>(domainCredentials.getCredentials())) {
				String id = credential instanceof IdCredentials ? ((IdCredentials) credential).getId() : null;

				try {
					if (!store.updateCredentials(domain, credential, credential)) {
						store.addCredentials(domain, credential);
					}

					if (id != null) {
						Set<String> addedIds = addedCredentials.get(domain);
						if (addedIds == null) {
							addedCredentials.put(domain, addedIds = new HashSet<>());
						}

						addedIds.add(id);
					}
				} catch (IOException e) {
					throw new RuntimeException(
							Messages.FolderCredentialsImporter_CredentialException(id != null ? id : "<unknown>"), e);
				}
			}
		}

		return addedCredentials;
	}

	private static void clearStoreCredentials(CredentialsStore store, Map<Domain, Set<String>> excludedCredentials) {
		for (Domain domain : new ArrayList<>(store.getDomains())) {
			Set<String> excludedIds = excludedCredentials.get(domain);

			if (excludedIds != null) {
				for (Credentials credential : new ArrayList<>(store.getCredentials(domain))) {
					String id = credential instanceof IdCredentials ? ((IdCredentials) credential).getId() : null;

					if (!excludedIds.contains(id)) {
						try {
							store.removeCredentials(domain, credential);
						} catch (IOException e) {
							throw new RuntimeException(Messages
									.FolderCredentialsImporter_CredentialException(id != null ? id : "<unknown>"), e);
						}
					}
				}

				if (!domain.isGlobal() && store.isDomainsModifiable() && store.getCredentials(domain).isEmpty()) {
					try {
						store.removeDomain(domain);
					} catch (IOException e) {
						throw new RuntimeException(Messages.FolderCredentialsImporter_DomainException(domain.getName()),
								e);
					}
				}
			} else if (!domain.isGlobal() && store.isDomainsModifiable()) {
				try {
					store.removeDomain(domain);
				} catch (IOException e) {
					throw new RuntimeException(Messages.FolderCredentialsImporter_DomainException(domain.getName()), e);
				}
			} else {
				for (Credentials credential : new ArrayList<>(store.getCredentials(domain))) {
					String id = credential instanceof IdCredentials ? ((IdCredentials) credential).getId() : null;

					try {
						store.removeCredentials(domain, credential);
					} catch (IOException e) {
						throw new RuntimeException(
								Messages.FolderCredentialsImporter_CredentialException(id != null ? id : "<unknown>"),
								e);
					}
				}
			}
		}
	}

}
