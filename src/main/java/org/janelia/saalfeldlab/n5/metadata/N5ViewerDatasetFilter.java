package org.janelia.saalfeldlab.n5.metadata;

import java.util.function.Predicate;

import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.N5DatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5Metadata;

public class N5ViewerDatasetFilter implements Predicate<N5TreeNode> {

	@Override
	public boolean test(final N5TreeNode t) {

		if (t.isDataset()) {
			final N5Metadata meta = t.getMetadata();
			if (meta instanceof N5DatasetMetadata) {
				final int nd = ((N5DatasetMetadata)meta).getAttributes().getNumDimensions();
				if (nd > 3) {
					return false;
				}
			}
		}

		return true;
	}
}
