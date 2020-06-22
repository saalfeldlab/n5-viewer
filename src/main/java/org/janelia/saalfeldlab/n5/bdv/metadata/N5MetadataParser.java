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
