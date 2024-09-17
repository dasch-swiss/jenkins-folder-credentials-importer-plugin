package swiss.dasch.plugins.foldercredentialsimporter;

import java.util.Optional;
import java.util.stream.StreamSupport;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;

import hudson.Extension;
import hudson.model.Executor;
import hudson.model.Queue.Executable;
import hudson.security.ACL;
import jenkins.model.Jenkins;

public class CredentialsImporterFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

	private final Importer importer;

	private boolean initialized;

	@DataBoundConstructor
	public CredentialsImporterFolderProperty(Import[] imports) {
		Executor executor = Executor.currentExecutor();
		Executable executable = executor != null ? executor.getCurrentExecutable() : null;

		// If there's no executable/job and current authentication is already SYSTEM
		// then we import the credentials as SYSTEM (e.g. when property is created by
		// JCasC). Otherwise when this is created by a job(-dsl) run then we require an
		// authentication that is used for importing the credentials other than SYSTEM.
		@SuppressWarnings("deprecation")
		boolean isSystemImporter = executable == null && Jenkins.getAuthentication() == ACL.SYSTEM;

		this.importer = new Importer(imports, isSystemImporter);
	}

	@DataBoundSetter
	public void setClear(boolean clear) {
		this.importer.setClear(clear);
	}

	@DataBoundSetter
	public void setUpdate(boolean update) {
		this.importer.setUpdate(update);
	}

	@Override
	protected synchronized void setOwner(AbstractFolder<?> owner) {
		super.setOwner(owner);

		// Only extract credentials once when property is first added/updated
		// and not when the property is (re-)loaded from disk
		if (this.initialized) {
			return;
		}

		this.initialized = true;

		this.importer.fill(owner);
	}

	public static void importCredentials(AbstractFolder<?> folder) {
		CredentialsImporterFolderProperty property = folder.getProperties()
				.get(CredentialsImporterFolderProperty.class);

		if (property != null) {
			Optional<CredentialsStore> storeLookup = StreamSupport
					.stream(CredentialsProvider.lookupStores(folder).spliterator(), false)
					.filter(s -> s.getContext() == folder).findFirst();

			storeLookup.ifPresent(property.importer::into);
		}
	}

	@Extension
	@Symbol("importCredentials")
	public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
		@DataBoundConstructor
		public DescriptorImpl() {
		}

		@Override
		public String getDisplayName() {
			return Messages.FolderCredentialsImporter_DisplayName();
		}
	}

}
