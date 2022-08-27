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
