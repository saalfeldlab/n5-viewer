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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5TreeNode;
import org.janelia.saalfeldlab.n5.universe.metadata.GenericMetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.MultiscaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup;
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataParser;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata;

public class N5ViewerMultichannelMetadata implements N5MetadataGroup<MultiscaleMetadata<?>> {

	public static final Predicate<String> channelPredicate = Pattern.compile("^c\\d+$").asPredicate();

	private final String basePath;

	private final MultiscaleMetadata<?>[] childMetadata;

	public N5ViewerMultichannelMetadata(final String basePath, final MultiscaleMetadata<?>[] childMetadata) {

		this.basePath = basePath;
		this.childMetadata = childMetadata;
	}

	@Override
	public String getPath() {

		return basePath;
	}

	@Override
	public String[] getPaths() {

		return Arrays.stream(childMetadata).map(m -> m.getPath()).toArray(String[]::new);
	}

	@Override
	public MultiscaleMetadata<?>[] getChildrenMetadata() {

		return childMetadata;
	}

	public static class N5ViewerMultichannelMetadataParser implements N5MetadataParser<N5ViewerMultichannelMetadata> {

		@Override
		public Optional<N5ViewerMultichannelMetadata> parseMetadata(final N5Reader n5, final N5TreeNode node) {

			final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
			for (final N5TreeNode childNode : node.childrenList()) {
				// note, the n5v spec is such that
				// channels are always parents of scales :
				// e.g. a path of c0/s0
				// this is why I check that there is a MultiscaleMetadata
				// instance
				if (channelPredicate.test(childNode.getNodeName())
						&& childNode.getMetadata() instanceof MultiscaleMetadata)
					scaleLevelNodes.put(childNode.getNodeName(), childNode);
			}

			if (scaleLevelNodes.isEmpty())
				return Optional.empty();

			final MultiscaleMetadata[] childMetadata = scaleLevelNodes
					.values()
					.stream()
					.map(N5TreeNode::getMetadata)
					.toArray(MultiscaleMetadata[]::new);
			return Optional.of(new N5ViewerMultichannelMetadata(node.getPath(), childMetadata));
		}
	}

	public static class GenericMultichannelMetadataParser implements N5MetadataParser<GenericMetadataGroup<N5SingleScaleMetadata>> {

		@Override
		public Optional<GenericMetadataGroup<N5SingleScaleMetadata>> parseMetadata(final N5Reader n5, final N5TreeNode node) {

			final Map<String, N5TreeNode> children = new HashMap<>();
			for (final N5TreeNode childNode : node.childrenList()) {
				if (channelPredicate.test(childNode.getNodeName()) && childNode.getMetadata() instanceof N5SingleScaleMetadata)
					children.put(childNode.getNodeName(), childNode);
			}

			final N5SingleScaleMetadata[] childMetadata = children
					.values()
					.stream()
					.map(N5TreeNode::getMetadata)
					.toArray(N5SingleScaleMetadata[]::new);

			if (childMetadata.length == 0)
				return Optional.empty();
			else
				return Optional.of(new GenericMetadataGroup<N5SingleScaleMetadata>(node.getPath(), childMetadata));
		}
	}

}
