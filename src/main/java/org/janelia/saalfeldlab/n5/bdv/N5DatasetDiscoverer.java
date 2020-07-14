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
package org.janelia.saalfeldlab.n5.bdv;

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5TreeNode;
import org.janelia.saalfeldlab.n5.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.metadata.N5SingleScaleMetadata;
import se.sawano.java.text.AlphanumericComparator;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.Collator;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;

public class N5DatasetDiscoverer {

    private final N5MetadataParser[] metadataParsers;

    private final Comparator<? super String> comparator;

    /**
     * Creates an N5 discoverer with alphanumeric sorting order of groups/datasets (such as, s9 goes before s10).
     *
     * @param metadataParsers
     */
    public N5DatasetDiscoverer(final N5MetadataParser... metadataParsers) {

        this(
                Optional.of(new AlphanumericComparator(Collator.getInstance())),
                metadataParsers);
    }

    /**
     * Creates an N5 discoverer.
     *
     * If the optional parameter {@code comparator} is specified, the groups and datasets
     * will be listed in the order determined by this comparator.
     *
     * @param comparator
     * @param metadataParsers
     */
    public N5DatasetDiscoverer(
            final Optional<Comparator<? super String>> comparator,
            final N5MetadataParser... metadataParsers) {

        this.comparator = comparator.orElseGet(null);
        this.metadataParsers = metadataParsers;
    }

    public N5TreeNode discover(final N5Reader n5) throws IOException {

        final N5TreeNode root = new N5TreeNode("/");
        discover(n5, root);
        parseMetadata(n5, root, metadataParsers);
        trim(root);
        if (comparator != null)
            sort(root, comparator);
        return root;
    }

    public static DefaultMutableTreeNode toJTreeNode(final N5TreeNode n5Node)
    {
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(n5Node);
        for (final N5TreeNode n5ChildNode : n5Node.children)
            node.add(toJTreeNode(n5ChildNode));
        return node;
    }

    private static void discover(final N5Reader n5, final N5TreeNode node) throws IOException {

        if (n5.datasetExists(node.path)) {
            node.isDataset = true;
        } else {
            for (final String childGroup : n5.list(node.path)) {
                final String childPath = Paths.get(node.path, childGroup).toString();
                final N5TreeNode childNode = new N5TreeNode(childPath);
                node.children.add(childNode);
                discover(n5, childNode);
            }
        }
    }

    private static void parseMetadata(final N5Reader n5, final N5TreeNode node, final N5MetadataParser[] metadataParsers) throws IOException {

        // Recursively parse metadata for children nodes
        for (final N5TreeNode childNode : node.children)
            parseMetadata(n5, childNode, metadataParsers);

		// Go through all parsers to populate metadata
		for ( final N5MetadataParser parser : metadataParsers )
		{
			try
			{
				node.metadata = parser.parseMetadata( n5, node );
				if ( node.metadata != null )
					break;
			}
			catch ( Exception e )
			{
				// System.err.println( "Error parsing metadata: " + node );
			}
		}

        // If there is no matching metadata but it is a dataset, we should still be able to open it.
        // Create a single-scale metadata entry with an identity transform.
        if (node.metadata == null && node.isDataset)
        {
        	System.out.println("Using default metadata for: " + node.path );
            node.metadata = new N5SingleScaleMetadata(node.path, new AffineTransform3D());
        }
    }

    /**
     * Removes branches of the N5 container tree that do not contain any nodes that can be opened
     * (nodes with metadata).
     *
     * @param node
     * @return
     *      {@code true} if the branch contains a node that can be opened, {@code false} otherwise
     */
    private static boolean trim(final N5TreeNode node)
    {
        if (node.children.isEmpty())
            return node.metadata != null;

        boolean ret = false;
        for (final Iterator<N5TreeNode> it = node.children.iterator(); it.hasNext();)
        {
            final N5TreeNode childNode = it.next();
            if (!trim(childNode))
                it.remove();
            else
                ret = true;
        }
        return ret || node.metadata != null;
    }

    private static void sort(final N5TreeNode node, final Comparator<? super String> comparator)
    {
        node.children.sort(Comparator.comparing(N5TreeNode::toString, comparator));
        for (final N5TreeNode childNode : node.children)
            sort(childNode, comparator);
    }
}
