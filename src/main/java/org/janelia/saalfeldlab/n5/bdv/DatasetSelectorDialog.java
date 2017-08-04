package org.janelia.saalfeldlab.n5.bdv;

import java.awt.Button;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;

import fiji.util.gui.GenericDialogPlus;
import ij.Prefs;

public class DatasetSelectorDialog
{
	private static final String EMPTY_ITEM = "                                                                                                ";

	public String run()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "N5 Viewer" );

		final SelectionHistory selectionHistory = new SelectionHistory();
		final List< String > choiceItems = new ArrayList<>( selectionHistory.getHistory() );
		final boolean fakeFirstItem = choiceItems.isEmpty();
		if ( fakeFirstItem )
			choiceItems.add( EMPTY_ITEM );

		gd.addChoice( "N5_dataset_path: ", choiceItems.toArray( new String[ 0 ] ), null );
		final ChoiceDirectoryListener choiceDirectoryListener = addChoiceDirectorySelector( gd, ( Choice ) gd.getChoices().get( 0 ), fakeFirstItem );

		gd.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowOpened( final WindowEvent e )
					{
						final Button okButton = gd.getButtons()[ 0 ];
						okButton.setEnabled( !fakeFirstItem );
						choiceDirectoryListener.setOKButton( okButton );
					}
				}
			);

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final String selection = gd.getNextChoice();
		selectionHistory.addToHistory( selection );

		return selection;
	}

	private ChoiceDirectoryListener addChoiceDirectorySelector( final GenericDialogPlus gd, final Choice choice, final boolean fakeFirstItem )
	{
		final Button button = new Button( "Browse..." );
		final ChoiceDirectoryListener listener = new ChoiceDirectoryListener( choice, fakeFirstItem );
		button.addActionListener( listener );
		button.addKeyListener( gd );

		gd.add( new Panel() );

		final Panel panel = new Panel();
		panel.setLayout( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( button );

		gd.add( panel );
		return listener;
	}

	private static class ChoiceDirectoryListener implements ActionListener
	{
		private final Choice choice;
		private final int fileSelectionMode;

		private boolean removeFirstItem;
		private int lastItemIndex;

		private Button okButton;

		public ChoiceDirectoryListener( final Choice choice, final boolean removeFirstItem )
		{
			this( choice, JFileChooser.DIRECTORIES_ONLY, removeFirstItem );
		}

		public ChoiceDirectoryListener( final Choice choice, final int fileSelectionMode, final boolean removeFirstItem )
		{
			this.choice = choice;
			this.fileSelectionMode = fileSelectionMode;
			this.removeFirstItem = removeFirstItem;
			this.lastItemIndex = -1;
		}

		public void setOKButton( final Button okButton )
		{
			this.okButton = okButton;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			File directory = new File( choice.getSelectedItem() );
			while ( directory != null && !directory.exists() )
				directory = directory.getParentFile();

			final JFileChooser fc = new JFileChooser( directory );
			fc.setFileSelectionMode( fileSelectionMode );

			fc.showOpenDialog( null );
			final File selFile = fc.getSelectedFile();
			if ( selFile != null )
			{
				final List< String > choiceItems = new ArrayList<>();
				for ( int i = 0; i < choice.getItemCount(); ++i )
					choiceItems.add( choice.getItem( i ) );

				final String oldSelected = choiceItems.get( 0 );
				final String newSelected = selFile.getAbsolutePath();

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

	private static class SelectionHistory
	{
		private static final String PREF_KEY = "n5-viewer.history";
		private static final String DELIMETER = "|";
		private static final int MAX_ENTRIES = 10;

		private final List< String > history;

		public SelectionHistory()
		{
			final String pref = Prefs.get( PREF_KEY, "" );
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

			Prefs.set( PREF_KEY, String.join( DELIMETER, history ) );
		}
	}
}
