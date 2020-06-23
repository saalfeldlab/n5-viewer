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
package org.janelia.saalfeldlab.n5.bdv.metadata;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5TreeNode;

import java.io.IOException;

@FunctionalInterface
public interface N5MetadataParser {

    /**
     * Called by the {@link org.janelia.saalfeldlab.n5.bdv.N5DatasetDiscoverer}
     * while discovering the N5 tree and filling the metadata for datasets or groups.
     *
     * The metadata parsing is done in the bottom-up fashion, so the children of the given {@code node}
     * have already been processed and should already contain valid metadata (if any).
     *
     * @param n5
     * @param node
     * @return
     * @throws IOException
     */
    N5Metadata parseMetadata(N5Reader n5, N5TreeNode node) throws IOException;
}
