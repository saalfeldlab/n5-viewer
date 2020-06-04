package org.janelia.saalfeldlab.n5.bdv.dataaccess.googlecloud;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;
import ij.IJ;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;

import java.io.IOException;

public class GoogleCloudClientBuilderWithDefaultCredentials
{
    private static final String googleCloudSdkLink = "https://cloud.google.com/sdk/docs";

    private static final String googleCloudAuthCmd = "gcloud auth application-default login";

    public static Storage createStorage() throws DataAccessException
    {
        return createStorage( null );
    }

    public static Storage createStorage( final String projectId ) throws DataAccessException
    {
        try
        {
            if ( !verifyCredentials() )
                throw new Exception();

            return new GoogleCloudStorageClient( projectId ).create();
        }
        catch ( final Exception e )
        {
            showErrorPrompt();
            throw new DataAccessException();
        }
    }

    public static ResourceManager createResourceManager() throws DataAccessException
    {
        try
        {
            if ( !verifyCredentials() )
                throw new Exception();

            return new GoogleCloudResourceManagerClient().create();
        }
        catch ( final Exception e )
        {
            showErrorPrompt();
            throw new DataAccessException();
        }
    }

    private static boolean verifyCredentials() throws IOException
    {
        return GoogleCredentials.getApplicationDefault() != null;
    }

    private static void showErrorPrompt()
    {
        IJ.error(
                "N5 Viewer",
                "<html>Could not find Google Cloud credentials. Please install "
                        + "<a href=\"" + googleCloudSdkLink + "\">Google Cloud SDK</a><br/>"
                        + "and then run this command to initialize the credentials:<br/><br/>"
                        + "<pre>" + googleCloudAuthCmd + "</pre></html>"
        );
    }
}
