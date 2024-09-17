package swiss.dasch.plugins.foldercredentialsimporter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Predicates;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Failure;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.Queue.Executable;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jline.internal.Preconditions;

@SuppressWarnings("deprecation")
public class Import extends AbstractDescribableImpl<Import> implements ExtensionPoint, Serializable {

	private static final long serialVersionUID = -1617027891386576945L;

	private static transient Field baseCredentialsScopeField;

	private static transient final CredentialsSource[] DEFAULT_CREDENTIALS_SOURCES = new CredentialsSource[] {
			CredentialsSource.SYSTEM, CredentialsSource.JOB };

	private static transient final CredentialsScope[] DEFAULT_CREDENTIALS_SCOPES = new CredentialsScope[] {
			CredentialsScope.GLOBAL, CredentialsScope.USER };

	private ImportSource source;
	private ImportTarget target;

	@DataBoundConstructor
	public Import(ImportSource source) {
		this.source = source;
	}

	@DataBoundSetter
	public void setTo(ImportTarget to) {
		this.target = to;
	}

	public ImportedCredentials findCredentials(AccessControlled acl, boolean clear) {
		Preconditions.checkNotNull(acl);

		Executor executor = Executor.currentExecutor();
		Executable executable = executor != null ? executor.getCurrentExecutable() : null;

		// Only do stuff when called from a job(-dsl) run
		if (executable == null || executable instanceof Run == false) {
			return ImportedCredentials.empty();
		}

		return this.findCredentials(acl, (Run<?, ?>) executable, clear);
	}

	public ImportedCredentials findCredentials(AccessControlled acl, Run<?, ?> run, boolean clear) {
		Preconditions.checkNotNull(acl);
		Preconditions.checkNotNull(run);

		if (this.source == null || this.source.ids == null || this.source.ids.length == 0) {
			return ImportedCredentials.empty();
		}

		Authentication authentication = Jenkins.getAuthentication();

		// Job is required to run under a specific user for safety
		if (authentication == ACL.SYSTEM) {
			throw new AccessDeniedException(Messages.FolderCredentialsImporter_NotAuthenticated());
		}

		// Must be able to modify credentials at the place to which the
		// credentials will be copied
		acl.checkPermission(CredentialsProvider.CREATE);
		acl.checkPermission(CredentialsProvider.UPDATE);
		if (clear) {
			acl.checkPermission(CredentialsProvider.DELETE);
		}
		if (clear
				|| (this.target != null && (this.target.copiedDomains != null || this.target.defaultDomain != null))) {
			acl.checkPermission(CredentialsProvider.MANAGE_DOMAINS);
		}

		return this.findCredentialsAuthorized(run, authentication, clear);
	}

	public ImportedCredentials findCredentialsAuthorized(@Nullable Run<?, ?> run,
			@Nullable Authentication authentication, boolean clear) {
		if (this.source == null || this.source.ids == null || this.source.ids.length == 0) {
			return ImportedCredentials.empty();
		}

		// Job's parent
		ItemGroup<?> parent = run != null ? run.getParent().getParent() : null;

		List<DomainCredentials> credentials;

		credentials = lookupCredentials(parent, run, authentication, this.source.sources, this.source.scopes,
				this.source.uris, this.source.domains);

		credentials = filterCredentialsByIds(credentials, this.source.ids, this.source.required);

		credentials = setDomainsAndScope(credentials, this.target != null ? this.target.copiedDomains : null,
				this.target != null ? this.target.defaultDomain : null, this.target != null ? this.target.scope : null);

		return new ImportedCredentials(credentials, clear);
	}

	private static List<DomainCredentials> setDomainsAndScope(List<DomainCredentials> credentials,
			@Nullable String[] copiedDomains, @Nullable Domain defaultDomain, @Nullable CredentialsScope scope) {

		Predicate<Domain> shouldCopyDomain;

		if (copiedDomains != null && copiedDomains.length > 0) {
			GlobMatcher[] matchers = new GlobMatcher[copiedDomains.length];

			for (int i = 0; i < copiedDomains.length; ++i) {
				matchers[i] = new GlobMatcher(copiedDomains[i]);
			}

			shouldCopyDomain = (domain) -> {
				for (GlobMatcher matcher : matchers) {
					if (matcher.matches(domain.getName())) {
						return true;
					}
				}
				return false;
			};
		} else {
			shouldCopyDomain = Predicates.alwaysFalse();
		}

		List<DomainCredentials> newDomainCredentials = new ArrayList<>();

		for (DomainCredentials domainCredentials : credentials) {
			Domain domain = domainCredentials.getDomain();

			Domain newDomain;

			if (shouldCopyDomain.test(domain)) {
				newDomain = domain;
			} else if (defaultDomain != null) {
				newDomain = defaultDomain;
			} else {
				newDomain = Domain.global();
			}

			List<Credentials> newCredentials = new ArrayList<>();

			for (Credentials credential : domainCredentials.getCredentials()) {
				Credentials newCredential = null;

				if (scope != null) {
					newCredential = createCredentialWithScope(credential, scope);
				}

				newCredentials.add(newCredential != null ? newCredential : credential);
			}

			newDomainCredentials.add(new DomainCredentials(newDomain, newCredentials));
		}

		return newDomainCredentials;
	}

	@Nullable
	private static Credentials createCredentialWithScope(Credentials credentials, CredentialsScope scope) {
		if (credentials instanceof BaseCredentials == false) {
			return null;
		}

		Credentials clone = null;

		try {
			byte[] bytes;

			try (ByteArrayOutputStream bytesStream = new ByteArrayOutputStream()) {
				try (ObjectOutputStream objectStream = new ObjectOutputStream(bytesStream)) {
					objectStream.writeObject(credentials);
				}
				bytesStream.flush();
				bytes = bytesStream.toByteArray();
			}

			// Using the "uber" classloader which can also load classes from other plugins,
			// which is necessary in case the credentials class is from a plugin
			final ClassLoader classLoader = Jenkins.get().getPluginManager().uberClassLoader;

			try (ByteArrayInputStream bytesStream = new ByteArrayInputStream(bytes);
					ObjectInputStream objectStream = new ClassLoaderObjectInputStream(classLoader, bytesStream)) {
				clone = (IdCredentials) objectStream.readObject();
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed serializing or deserializing credential", e);
		}

		if (clone instanceof BaseCredentials) {
			setCredentialsScopeField((BaseCredentials) clone, scope);

			if (clone.getScope() == scope) {
				return clone;
			}
		}

		return null;
	}

	private static void setCredentialsScopeField(BaseCredentials credentials, CredentialsScope scope) {
		try {
			synchronized (Import.class) {
				if (baseCredentialsScopeField == null) {
					baseCredentialsScopeField = BaseCredentials.class.getDeclaredField("scope");
					baseCredentialsScopeField.setAccessible(true);
				}
			}
			baseCredentialsScopeField.set(credentials, scope);
		} catch (Exception e) {
			throw new RuntimeException("Failed setting BaseCredentials#scope field", e);
		}
	}

	private static List<DomainCredentials> filterCredentialsByIds(List<DomainCredentials> credentials, String[] ids,
			boolean failIfNotFound) {
		List<DomainCredentials> allFilteredDomainCredentials = new ArrayList<>();

		IdGlobMatcher[] idFilters = Arrays.asList(ids).stream().map(id -> new IdGlobMatcher(id))
				.toArray(i -> new IdGlobMatcher[i]);

		CredentialsMatcher idFilter = CredentialsMatchers.anyOf(idFilters);

		for (DomainCredentials domainCredentials : credentials) {
			DomainCredentials filteredDomainCredentials = new DomainCredentials(domainCredentials.getDomain(),
					CredentialsMatchers.filter(domainCredentials.getCredentials(), idFilter));

			if (!filteredDomainCredentials.getCredentials().isEmpty()) {
				allFilteredDomainCredentials.add(filteredDomainCredentials);
			}
		}

		if (failIfNotFound) {
			for (IdGlobMatcher filter : idFilters) {
				if (!filter.found()) {
					throw new Failure(Messages.FolderCredentialsImporter_CredentialNotFound(filter.pattern()));
				}
			}
		}

		return allFilteredDomainCredentials;
	}

	private static List<DomainCredentials> lookupCredentials(@Nullable ItemGroup<?> itemGroup,
			@Nullable AccessControlled acl, @Nullable Authentication authentication,
			@Nullable CredentialsSource[] sources, @Nullable CredentialsScope[] scopes, @Nullable String[] uris,
			@Nullable String[] domains) {

		if (sources == null || sources.length == 0) {
			sources = DEFAULT_CREDENTIALS_SOURCES;
		}

		if (scopes == null || scopes.length == 0) {
			scopes = DEFAULT_CREDENTIALS_SCOPES;
		}

		List<List<DomainRequirement>> allDomainRequirements = new ArrayList<>();
		if (uris != null && uris.length > 0) {
			for (String uri : uris) {
				allDomainRequirements.add(URIRequirementBuilder.fromUri(uri).build());
			}
		} else {
			allDomainRequirements.add(null);
		}

		if (authentication == null) {
			authentication = Jenkins.getAuthentication();
		}

		DomainCredentialsBuilder builder = new DomainCredentialsBuilder();

		for (CredentialsSource source : sources) {
			ItemGroup<?> credentialsItemGroup = null;
			AccessControlled credentialsAcl = null;

			// Require ADMINISTER for SYSTEM scope because typically jobs
			// don't have access to SYSTEM scoped credentials
			Permission systemScopeRequiredPermission = Jenkins.ADMINISTER;
			Permission globalScopeRequiredPermission = null;
			Permission userScopeRequiredPermission = null;

			switch (source) {
			case SYSTEM:
				globalScopeRequiredPermission = null; // Always available by default
				userScopeRequiredPermission = CredentialsProvider.USE_OWN;
				credentialsItemGroup = Jenkins.get();
				credentialsAcl = Jenkins.get();
				break;
			case JOB:
				globalScopeRequiredPermission = CredentialsProvider.USE_ITEM;
				userScopeRequiredPermission = CredentialsProvider.USE_OWN;
				credentialsItemGroup = itemGroup;
				credentialsAcl = acl != null ? acl
						: itemGroup instanceof AccessControlled ? (AccessControlled) itemGroup : Jenkins.get();
				break;
			}

			if (credentialsItemGroup != null) {
				DomainCredentialsFilter baseDomainsFilter = createDomainFilter(credentialsItemGroup, null, domains);

				for (CredentialsScope scope : scopes) {
					CredentialsMatcher scopeFilter = CredentialsMatchers.withScope(scope);

					Authentication credentialsAuthentication = null;

					switch (scope) {
					case SYSTEM:
						if (systemScopeRequiredPermission == null
								|| credentialsAcl.hasPermission(authentication, systemScopeRequiredPermission)) {
							credentialsAuthentication = ACL.SYSTEM;
						}
						break;
					case GLOBAL:
						if (globalScopeRequiredPermission == null
								|| credentialsAcl.hasPermission(authentication, globalScopeRequiredPermission)) {
							credentialsAuthentication = ACL.SYSTEM;
						}
						break;
					case USER:
						if (userScopeRequiredPermission == null
								|| credentialsAcl.hasPermission(authentication, userScopeRequiredPermission)) {
							credentialsAuthentication = authentication;
						}
						break;
					}

					if (credentialsAuthentication != null) {
						DomainCredentialsFilter userDomainsFilter = createDomainFilter(null, credentialsAuthentication,
								domains);

						CredentialsMatcher filter = CredentialsMatchers.allOf(scopeFilter,
								CredentialsMatchers.anyOf(baseDomainsFilter, userDomainsFilter));

						for (List<DomainRequirement> domainRequirements : allDomainRequirements) {
							List<IdCredentials> foundCredentials = CredentialsMatchers.filter(
									CredentialsProvider.lookupCredentials(IdCredentials.class, credentialsItemGroup,
											credentialsAuthentication, domainRequirements),
									filter);

							for (IdCredentials credential : foundCredentials) {
								Domain domain = DomainCredentialsFilter.getDomain(credential.getId(), baseDomainsFilter,
										userDomainsFilter);
								if (domain == null) {
									domain = Domain.global();
								}

								builder.addCredentials(domain, credential);
							}
						}
					}
				}
			}
		}

		return builder.build();
	}

	private static DomainCredentialsFilter createDomainFilter(@Nullable ModelObject object,
			@Nullable Authentication authentication, @Nullable String[] domains) {
		List<Iterable<CredentialsStore>> allStores = new ArrayList<>();
		if (object != null) {
			allStores.add(CredentialsProvider.lookupStores(object));
		}

		if (authentication != ACL.SYSTEM) {
			User user = User.get(authentication);
			if (user != null) {
				allStores.add(CredentialsProvider.lookupStores(user));
			}
		}

		if (!allStores.isEmpty()) {
			Set<String> filterSet = new HashSet<>();

			Map<String, Domain> domainMap = new HashMap<>();

			for (Iterable<CredentialsStore> stores : allStores) {
				for (CredentialsStore store : stores) {
					for (Domain storeDomain : store.getDomains()) {
						for (Credentials credential : store.getCredentials(storeDomain)) {
							if (credential instanceof IdCredentials) {
								String id = ((IdCredentials) credential).getId();

								domainMap.put(id, storeDomain);

								if (domains != null) {
									for (String domain : domains) {
										GlobMatcher matcher = new GlobMatcher(domain);

										if (matcher.matches(storeDomain.getName())) {
											filterSet.add(id);
										}
									}
								}
							}
						}
					}
				}
			}

			if (domains == null || domains.length == 0) {
				return DomainCredentialsFilter.any(domainMap);
			} else {
				return DomainCredentialsFilter.some(filterSet, domainMap);
			}
		}

		return DomainCredentialsFilter.none();
	}

	@Extension
	@Symbol("from")
	public static class DescriptorImpl extends Descriptor<Import> {
	}

}
