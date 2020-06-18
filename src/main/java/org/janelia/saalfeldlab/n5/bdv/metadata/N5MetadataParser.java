package org.janelia.saalfeldlab.n5.bdv.metadata;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5TreeNode;

import java.io.IOException;

@FunctionalInterface
public interface N5MetadataParser {

    N5Metadata parseMetadata(N5Reader n5, N5TreeNode node) throws IOException;
}
