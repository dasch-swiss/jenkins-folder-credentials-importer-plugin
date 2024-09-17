package swiss.dasch.plugins.foldercredentialsimporter;

public enum CredentialsSource {
	/**
	 * Credentials from
	 * {@link com.cloudbees.plugins.credentials.SystemCredentialsProvider}
	 */
	SYSTEM,

	/**
	 * Credentials available to job (includes {@link CredentialsSource#SYSTEM}
	 * credentials)
	 */
	JOB
}