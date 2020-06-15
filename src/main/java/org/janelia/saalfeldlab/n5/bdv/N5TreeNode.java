package org.janelia.saalfeldlab.n5.bdv;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class N5TreeNode {

    public final String path;
    public final List<N5TreeNode> children = new ArrayList<>();
    public boolean isDataset;

    public N5TreeNode(final String path) {

        this.path = path;
    }

    public String getNodeName() {

        return Paths.get(removeLeadingSlash(path)).getFileName().toString();
    }

    @Override
    public String toString() {

        final String nodeName = getNodeName();
        return !nodeName.isEmpty() ? nodeName : "/";
    }

    /**
     * Removes the leading slash from a given path and returns the corrected path.
     * It ensures correctness on both Unix and Windows, otherwise {@code pathName} is treated
     * as UNC path on Windows, and {@code Paths.get(pathName, ...)} fails with {@code InvalidPathException}.
     *
     * @param pathName
     * @return
     */
    protected static String removeLeadingSlash(final String pathName) {

        return pathName.startsWith("/") || pathName.startsWith("\\") ? pathName.substring(1) : pathName;
    }
}