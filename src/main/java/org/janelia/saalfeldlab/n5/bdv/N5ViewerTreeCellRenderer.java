/*-
 * #%L
 * N5 Viewer
 * %%
 * Copyright (C) 2017 - 2022 Igor Pisarev, Stephan Saalfeld
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.janelia.saalfeldlab.n5.bdv;

import java.awt.Component;

import javax.swing.JTree;

import org.janelia.saalfeldlab.n5.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.metadata.N5ViewerMultichannelMetadata;
import org.janelia.saalfeldlab.n5.metadata.canonical.CanonicalMultichannelMetadata;
import org.janelia.saalfeldlab.n5.ui.N5DatasetTreeCellRenderer;
import org.janelia.saalfeldlab.n5.ui.N5SwingTreeNode;

public class N5ViewerTreeCellRenderer extends N5DatasetTreeCellRenderer
{
	private static final long serialVersionUID = -4245251506197982653L;

	public N5ViewerTreeCellRenderer( final boolean showConversionWarning ) {
		super( showConversionWarning );
	}

	@Override
	public Component getTreeCellRendererComponent( final JTree tree, final Object value,
			final boolean sel, final boolean exp, final boolean leaf, final int row, final boolean hasFocus )
	{
		super.getTreeCellRendererComponent( tree, value, sel, exp, leaf, row, hasFocus );

		N5SwingTreeNode node;
		if ( value instanceof N5SwingTreeNode )
		{
			node = ( ( N5SwingTreeNode ) value );
			N5Metadata meta = node.getMetadata();
			if ( meta != null )
			{
			    final String memStr = memString( node );
			    final String memSizeString = memStr.isEmpty() ? "" : " (" + memStr + ")";
			    final String name = node.getParent() == null ? rootName : node.getNodeName();

				final String multiscaleString;
				if( meta instanceof MultiscaleMetadata )
					multiscaleString = "multiscale";
				else
					multiscaleString = "";

				final String multiChannelString;
				if( meta instanceof N5ViewerMultichannelMetadata || meta instanceof CanonicalMultichannelMetadata )
					multiChannelString = "multichannel";
				else
					multiChannelString = "";

				setText( String.join( "", new String[]{
						"<html>",
						String.format( nameFormat, name),
						" (",
						getParameterString( node ),
						multiChannelString,
						multiscaleString,
						")",
						memSizeString,
						"</html>"
				}));

			}
			else
			{
				setText(node.getParent() == null ? rootName : node.getNodeName());
			}
		}
		return this;
    }

}
