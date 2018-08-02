package org.janelia.saalfeldlab.n5.bdv.dataaccess.googlecloud;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudClientSecretsPrompt;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

import fiji.util.gui.GenericDialogPlus;

public class GoogleCloudClientSecretsDialogPrompt extends GoogleCloudClientSecretsPrompt
{
	@Override
	public GoogleClientSecrets prompt( final GoogleCloudClientSecretsPromptReason reason ) throws GoogleCloudSecretsPromptCanceledException
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );
		gd.addMessage( "Please provide client ID & secret generated in the Google Cloud console:" );
		gd.addStringField( "Client ID", "", 75 );
		gd.addStringField( "Client secret", "", 75 );
		gd.showDialog();

		if ( gd.wasCanceled() )
			throw new GoogleCloudSecretsPromptCanceledException();

		final String clientId = gd.getNextString().trim();
		final String clientSecret = gd.getNextString().trim();
		return create( clientId, clientSecret );
	}
}
