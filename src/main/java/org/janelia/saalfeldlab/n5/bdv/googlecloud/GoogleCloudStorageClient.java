package org.janelia.saalfeldlab.n5.bdv.googlecloud;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public abstract class GoogleCloudStorageClient
{
	/** Directory to store user credentials. */
	private static final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".store/n5-viewer-google-cloud-oauth2");

	/** OAuth 2.0 scopes. */
	private static final List<String> SCOPES = Arrays.asList(
			"https://www.googleapis.com/auth/cloudplatformprojects.readonly",
			"https://www.googleapis.com/auth/devstorage.read_write");

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
	 * globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	public static Storage create() throws IOException
	{
		try
		{
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		}
		catch ( final GeneralSecurityException e )
		{
			throw new RuntimeException( e );
		}

		dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

		final Credential credential = authorize();
		final AccessToken accessToken = new AccessToken(credential.getAccessToken(), null);

		// create custom client
		final Storage storage = StorageOptions
				.newBuilder()
				.setCredentials(OAuth2Credentials.create(accessToken))
				.build()
				.getService();

		return storage;
	}

	/** Authorizes the installed application to access user's protected data. */
	private static Credential authorize() throws IOException {

		// load client secrets
		final GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(GoogleCloudStorageClient.class.getResourceAsStream("/googlecloud_client_secrets.json")));

		// set up authorization code flow
		final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets, SCOPES).setDataStoreFactory(
						dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}
}
