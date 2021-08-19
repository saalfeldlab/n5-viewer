package org.janelia.saalfeldlab.n5.metadata;

import java.util.function.Predicate;

import org.janelia.saalfeldlab.n5.N5TreeNode;

public class N5ViewerDatasetFilter implements Predicate< N5TreeNode >
{
	@Override
	public boolean test(final N5TreeNode t) {
		if (t.isDataset()) {
			N5Metadata meta = t.getMetadata();
			if (meta instanceof N5DatasetMetadata) {
				int nd = ((N5DatasetMetadata) meta).getAttributes().getNumDimensions();
				if (nd > 3) {
					return false;
				}
			}
		}

		return true;
	}

}
