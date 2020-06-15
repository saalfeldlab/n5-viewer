package org.janelia.saalfeldlab.n5.bdv;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import org.janelia.saalfeldlab.n5.N5Reader;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class N5ViewerDatasetSelector implements N5ViewerDataSelection.DatasetSelector {

    private static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";
    private static final String PIXEL_RESOLUTION_KEY = "pixelResolution";

    public N5ViewerDataSelection.SelectedDataset selectDataset(final N5Reader n5, final N5TreeNode node) throws N5ViewerDataSelection.DatasetParsingException, IOException
    {
        if (node.isDataset)
            return new N5ViewerDataSelection.SingleScaleDataset(node.path, getTransform(n5, node.path));

        final Set<String> childrenSet = new HashSet<>();
        for (final N5TreeNode childNode : node.children)
        {
            if (!childNode.isDataset)
                return null;
            childrenSet.add(childNode.getNodeName());
        }
        for (int i = 0; i < childrenSet.size(); ++i)
            if (!childrenSet.contains("s" + i))
                return null;

        final List<String> scaleLevelPaths = new ArrayList<>();
        for (int i = 0; i < childrenSet.size(); ++i)
            scaleLevelPaths.add(Paths.get(node.path, "s" + i).toString());

        final AffineTransform3D[] transforms = new AffineTransform3D[scaleLevelPaths.size()];
        for (int i = 0; i < scaleLevelPaths.size(); ++i)
            transforms[i] = getTransform(n5, scaleLevelPaths.get(i));

        return new N5ViewerDataSelection.MultiScaleDataset(scaleLevelPaths.toArray(new String[0]), transforms);
    }

    private AffineTransform3D getTransform(final N5Reader n5, final String datasetPath) throws N5ViewerDataSelection.DatasetParsingException, IOException {
        double[] downsamplingFactors = n5.getAttribute(datasetPath, DOWNSAMPLING_FACTORS_KEY, double[].class);
        if (downsamplingFactors == null)
            downsamplingFactors = new double[] {1, 1, 1};

        double[] pixelResolution = n5.getAttribute(datasetPath, PIXEL_RESOLUTION_KEY, double[].class);
        if (pixelResolution == null)
            pixelResolution = new double[] {1, 1, 1};

        final AffineTransform3D transform = new AffineTransform3D();
        transform.preConcatenate(new Scale3D(downsamplingFactors)).preConcatenate(new Scale3D(pixelResolution));

        return transform;
    }
}
