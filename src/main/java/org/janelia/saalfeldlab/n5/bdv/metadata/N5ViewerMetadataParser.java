package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5TreeNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class N5ViewerMetadataParser implements N5MetadataParser {

    private static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";
    private static final String PIXEL_RESOLUTION_KEY = "pixelResolution";
    private static final String SCALES_KEY = "scales";

    private static final Predicate<String> scaleLevelPredicate = Pattern.compile("^s\\d+$").asPredicate();

    @Override
    public N5Metadata parseMetadata(final N5Reader n5, final N5TreeNode node) throws IOException {

        if (node.isDataset) {
            final long[] downsamplingFactors = n5.getAttribute(node.path, DOWNSAMPLING_FACTORS_KEY, long[].class);
            final double[] pixelResolution = n5.getAttribute(node.path, PIXEL_RESOLUTION_KEY, double[].class);
            final AffineTransform3D transform = buildTransform(downsamplingFactors, pixelResolution);
            return new N5SingleScaleMetadata(node.path, transform);
        }

        // Could be a multiscale group, need to check if it contains datasets named s0..sN
        final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
        for (final N5TreeNode childNode : node.children)
            if (scaleLevelPredicate.test(childNode.getNodeName()))
                scaleLevelNodes.put(childNode.getNodeName(), childNode);

        if (scaleLevelNodes.isEmpty())
            return null;

        for (final N5TreeNode scaleLevelNode : scaleLevelNodes.values())
            if (!scaleLevelNode.isDataset)
                return null;

        for (int i = 0; i < scaleLevelNodes.size(); ++i) {
            final String scaleLevelKey = "s" + i;
            if (!scaleLevelNodes.containsKey(scaleLevelKey))
                return null;
        }

        final List<AffineTransform3D> scaleLevelTransforms = new ArrayList<>();

        final double[][] deprecatedScales = n5.getAttribute(node.path, SCALES_KEY, double[][].class);
        if (deprecatedScales != null) {
            // this is a multiscale group in deprecated format
            final double[] pixelResolution = n5.getAttribute(node.path, PIXEL_RESOLUTION_KEY, double[].class);
            for (int i = 0; i < Math.min(deprecatedScales.length, scaleLevelNodes.size()); ++i) {
                final long[] downsamplingFactors = new long[deprecatedScales[i].length];
                for (int d = 0; d < downsamplingFactors.length; ++d)
                    downsamplingFactors[d] = Math.round(deprecatedScales[i][d]);
                scaleLevelTransforms.add(buildTransform(downsamplingFactors, pixelResolution));
            }
        } else {
            // this is a multiscale group, where scale level transforms are available through dataset metadata
            for (int i = 0; i < scaleLevelNodes.size(); ++i) {
                final String scaleLevelKey = "s" + i;
                final N5Metadata scaleLevelMetadata = scaleLevelNodes.get(scaleLevelKey).metadata;
                if (!(scaleLevelMetadata instanceof N5SingleScaleMetadata))
                    return null;
                scaleLevelTransforms.add(((N5SingleScaleMetadata) scaleLevelMetadata).transform);
            }
        }

        final List<String> scaleLevelPaths = new ArrayList<>();
        for (int i = 0; i < scaleLevelNodes.size(); ++i) {
            final String scaleLevelKey = "s" + i;
            scaleLevelPaths.add(scaleLevelNodes.get(scaleLevelKey).path);
        }

        return new N5MultiScaleMetadata(
                scaleLevelPaths.toArray(new String[scaleLevelPaths.size()]),
                scaleLevelTransforms.toArray(new AffineTransform3D[scaleLevelTransforms.size()]));
    }

    private static AffineTransform3D buildTransform(
            long[] downsamplingFactors,
            double[] pixelResolution)
    {
        if (downsamplingFactors == null)
            downsamplingFactors = new long[] {1, 1, 1};

        if (pixelResolution == null)
            pixelResolution = new double[] {1, 1, 1};

        final AffineTransform3D mipmapTransform = new AffineTransform3D();
        mipmapTransform.set(
                downsamplingFactors[ 0 ], 0, 0, 0.5 * ( downsamplingFactors[ 0 ] - 1 ),
                0, downsamplingFactors[ 1 ], 0, 0.5 * ( downsamplingFactors[ 1 ] - 1 ),
                0, 0, downsamplingFactors[ 2 ], 0.5 * ( downsamplingFactors[ 2 ] - 1 ) );

        final AffineTransform3D transform = new AffineTransform3D();
        transform.preConcatenate(mipmapTransform).preConcatenate(new Scale3D(pixelResolution));
        return transform;
    }
}
