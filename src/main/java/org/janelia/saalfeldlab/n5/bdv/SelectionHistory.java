package org.janelia.saalfeldlab.n5.bdv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import ij.Prefs;

class SelectionHistory
{
	private static final String DELIMETER = "|";
	private static final int MAX_ENTRIES = 10;

	private final String prefKey;
	private final List< String > history;

	public SelectionHistory( final String prefKey )
	{
		this.prefKey = prefKey;
		final String pref = Prefs.get( prefKey, "" );
		history = new ArrayList<>();
		if ( !pref.isEmpty() )
			history.addAll( Arrays.asList( pref.split( Pattern.quote( DELIMETER ) ) ) );
	}

	public List< String > getHistory()
	{
		return history;
	}

	public void addToHistory( final String item )
	{
		final int index = history.indexOf( item );
		if ( index == -1 )
		{
			history.add( 0, item );
			if ( history.size() > MAX_ENTRIES )
				history.remove( history.size() - 1 );
		}
		else
		{
			history.remove( index );
			history.add( 0, item );
		}

		Prefs.set( prefKey, String.join( DELIMETER, history ) );
	}
}
