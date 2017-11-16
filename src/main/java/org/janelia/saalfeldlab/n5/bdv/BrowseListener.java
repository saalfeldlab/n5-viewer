package org.janelia.saalfeldlab.n5.bdv;

import java.awt.Button;
import java.awt.Choice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

class BrowseListener implements ActionListener
{
	private int lastItemIndex = -1;

	private Button okButton;
	private Choice choice;

	private BrowseHandler browseHandler;
	private boolean removeFirstItem;

	public void update( final BrowseHandler browseHandler, final boolean removeFirstItem )
	{
		this.browseHandler = browseHandler;
		this.removeFirstItem = removeFirstItem;
		updateOkButtonState();
	}

	public void setOkButton( final Button okButton )
	{
		this.okButton = okButton;
		updateOkButtonState();
	}

	private void updateOkButtonState()
	{
		if ( okButton != null )
			okButton.setEnabled( !removeFirstItem );
	}

	public void setChoice( final Choice choice )
	{
		this.choice = choice;
	}

	@Override
	public void actionPerformed( final ActionEvent e )
	{
		final String newSelected = browseHandler.select();
		if ( newSelected != null )
		{
			final List< String > choiceItems = new ArrayList<>();
			for ( int i = 0; i < choice.getItemCount(); ++i )
				choiceItems.add( choice.getItem( i ) );

			final String oldSelected = choiceItems.get( 0 );

			// put old selected item back on its position, or just remove it if was not present in the history
			if ( removeFirstItem )
			{
				choiceItems.remove( 0 );
				if ( lastItemIndex != -1 )
					choiceItems.add( lastItemIndex, oldSelected );
			}

			// move new selected item to the top, or just add it if was not present if the history
			final int newSelectedIndex = choiceItems.indexOf( newSelected );
			if ( newSelectedIndex != -1 )
				choiceItems.remove( newSelectedIndex );
			choiceItems.add( 0, newSelected );

			removeFirstItem = true;
			lastItemIndex = newSelectedIndex;
			if ( okButton != null )
				okButton.setEnabled( true );

			choice.removeAll();
			for ( final String item : choiceItems )
				choice.add( item );
			choice.select( 0 );
		}
	}
}
