/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
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
