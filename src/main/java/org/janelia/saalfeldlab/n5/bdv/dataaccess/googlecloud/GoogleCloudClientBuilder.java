package org.janelia.saalfeldlab.n5.bdv.dataaccess.googlecloud;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth.Scope;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;

import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;

public class GoogleCloudClientBuilder
{
	public static Storage createStorage() throws IOException
	{
		return createStorage( createOAuth(), null );
	}
	public static Storage createStorage( final String projectId ) throws IOException
	{
		return createStorage( createOAuth(), projectId );
	}
	public static Storage createStorage( final GoogleCloudOAuth oauth ) throws IOException
	{
		return createStorage( oauth, null );
	}
	public static Storage createStorage( final GoogleCloudOAuth oauth, final String projectId )
	{
		final GoogleCloudStorageClient storageClient = new GoogleCloudStorageClient(
				oauth.getAccessToken(),
				oauth.getClientSecrets(),
				oauth.getRefreshToken()
			);
		return projectId == null ? storageClient.create() : storageClient.create( projectId );
	}

	public static ResourceManager createResourceManager() throws IOException
	{
		return createResourceManager( createOAuth() );
	}
	public static ResourceManager createResourceManager( final GoogleCloudOAuth oauth )
	{
		return new GoogleCloudResourceManagerClient(
				oauth.getAccessToken(),
				oauth.getClientSecrets(),
				oauth.getRefreshToken()
			).create();
	}

	public static GoogleCloudOAuth createOAuth() throws IOException
	{
		return createOAuth( createScopes() );
	}
	public static GoogleCloudOAuth createOAuth( final Collection< Scope > scopes ) throws IOException
	{
		return new GoogleCloudOAuth(
				scopes,
				"n5-viewer-google-cloud-oauth2",
				GoogleCloudClientBuilder.class.getResourceAsStream( "/googlecloud_client_secrets.json" )
			);
	}

	private static Collection< Scope > createScopes()
	{
		return Arrays.asList(
				GoogleCloudResourceManagerClient.ProjectsScope.READ_ONLY,
				GoogleCloudStorageClient.StorageScope.READ_WRITE
			);
	}
}
