package swiss.dasch.plugins.foldercredentialsimporter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

@Extension
public class FolderItemListener extends ItemListener {

	@Override
	public void onCreated(Item item) {
		if (item instanceof AbstractFolder) {
			CredentialsImporterFolderProperty.importCredentials((AbstractFolder<?>) item);
		}
	}

	@Override
	public void onUpdated(Item item) {
		if (item instanceof AbstractFolder) {
			CredentialsImporterFolderProperty.importCredentials((AbstractFolder<?>) item);
		}
	}

}