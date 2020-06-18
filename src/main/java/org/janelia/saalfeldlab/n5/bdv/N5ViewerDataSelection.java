package org.janelia.saalfeldlab.n5.bdv;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.metadata.N5Metadata;

import java.util.*;

public class N5ViewerDataSelection
{
    public final N5Reader n5;
    public final List<N5Metadata> metadata;

    public N5ViewerDataSelection(final N5Reader n5, final List<N5Metadata> metadata)
    {
        this.n5 = n5;
        this.metadata = Collections.unmodifiableList(metadata);
    }
}
