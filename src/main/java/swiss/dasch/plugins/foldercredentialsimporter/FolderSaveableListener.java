package swiss.dasch.plugins.foldercredentialsimporter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

@Extension
public class FolderSaveableListener extends SaveableListener {

	@Override
	public void onChange(Saveable o, XmlFile file) {
		if (o instanceof AbstractFolder) {
			CredentialsImporterFolderProperty.importCredentials((AbstractFolder<?>) o);
		}
	}

}