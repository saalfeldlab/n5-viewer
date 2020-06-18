package org.janelia.saalfeldlab.n5.bdv.metadata;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5TreeNode;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerDataSelection;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
            return new N5SingleScaleMetadata(transform);
        }

        // Could be a multiscale group, need to check if it contains datasets named s0..sN
        final Map<String, N5TreeNode> scaleLevelNodes = new HashMap<>();
        for (final N5TreeNode childNode : node.children)
            if (scaleLevelPredicate.test(childNode.getNodeName()))
                scaleLevelNodes.put(childNode.getNodeName(), childNode);

        for (final N5TreeNode scaleLevelNode : scaleLevelNodes.values())
            if (!scaleLevelNode.isDataset)
                return null;

        boolean checkDeprecatedFormat = false;

        for (int i = 0; i < scaleLevelNodes.size(); ++i) {
            final String scaleLevelKey = "s" + i;
            if (!scaleLevelNodes.containsKey(scaleLevelKey))
                return null;

            final N5TreeNode scaleLevelNode = scaleLevelNodes.get(scaleLevelKey);
            if (i != 0 && scaleLevelNode.metadata == null)
                checkDeprecatedFormat = true;
        }

        final List<AffineTransform3D> scaleLevelTransforms = new ArrayList<>();

        if (checkDeprecatedFormat) {
            // it may be a multiscale group, need to check for the group attribute 'scales'
            throw new NotImplementedException();
        } else {
            // this is a multiscale group, where scale level transforms are available through dataset metadata
            for (int i = 0; i < scaleLevelNodes.size(); ++i) {
                final String scaleLevelKey = "s" + i;
                final N5Metadata scaleLevelMetadata = scaleLevelNodes.get(scaleLevelKey).metadata;
                if (!(scaleLevelMetadata instanceof N5SingleScaleMetadata))
                    return null;
                scaleLevelTransforms.add(((N5SingleScaleMetadata) scaleLevelMetadata).transform)
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

//    public N5ViewerDataSelection.SelectedDataset selectDataset(final N5Reader n5, final N5TreeNode node) throws N5ViewerDataSelection.DatasetParsingException, IOException
//    {
//        if (node.isDataset)
//            return new N5ViewerDataSelection.SingleScaleDataset(node.path, getTransform(n5, node.path));
//
//        final Set<String> childrenSet = new HashSet<>();
//        for (final N5TreeNode childNode : node.children)
//        {
//            if (!childNode.isDataset)
//                return null;
//            childrenSet.add(childNode.getNodeName());
//        }
//        for (int i = 0; i < childrenSet.size(); ++i)
//            if (!childrenSet.contains("s" + i))
//                return null;
//
//        final List<String> scaleLevelPaths = new ArrayList<>();
//        for (int i = 0; i < childrenSet.size(); ++i)
//            scaleLevelPaths.add(Paths.get(node.path, "s" + i).toString());
//
//        final AffineTransform3D[] scaleLevelTransforms = getScaleLevelTransforms(n5, node, scaleLevelPaths);
//
//        return new N5ViewerDataSelection.MultiScaleDataset(scaleLevelPaths.toArray(new String[0]), scaleLevelTransforms);
//    }

//    private AffineTransform3D[] getScaleLevelTransforms(final N5Reader n5, final N5TreeNode node, final List<String> scaleLevelPaths) throws N5ViewerDataSelection.DatasetParsingException, IOException
//    {
//        final double[][] scales = n5.getAttribute(node.path, SCALES_KEY, double[][].class);
//        if (scales != null)
//        {
//            final double[] pixelResolution = n5.getAttribute(node.path, PIXEL_RESOLUTION_KEY, double[].class);
//            final AffineTransform3D[] transforms = new AffineTransform3D[scaleLevelPaths.size()];
//            for (int i = 0; i < scaleLevelPaths.size(); ++i)
//            {
//                final AffineTransform3D mipmapTransform = new AffineTransform3D();
//                mipmapTransform.set(
//                        scales[i][ 0 ], 0, 0, 0.5 * ( scales[i][ 0 ] - 1 ),
//                        0, scales[i][ 1 ], 0, 0.5 * ( scales[i][ 1 ] - 1 ),
//                        0, 0, scales[i][ 2 ], 0.5 * ( scales[i][ 2 ] - 1 ) );
//
//                final AffineTransform3D transform = new AffineTransform3D();
//                transform.preConcatenate(mipmapTransform).preConcatenate(new Scale3D(pixelResolution));
//                transforms[i] = transform;
//            }
//            return transforms;
//        }
//
//        final AffineTransform3D[] transforms = new AffineTransform3D[scaleLevelPaths.size()];
//        for (int i = 0; i < scaleLevelPaths.size(); ++i)
//            transforms[i] = getTransform(n5, scaleLevelPaths.get(i));
//        return transforms;
//    }

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
