/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.n5.bdv;

import org.janelia.saalfeldlab.n5.bdv.metadata.N5Metadata;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class N5TreeNode {

    public final String path;
    public final List<N5TreeNode> children = new ArrayList<>();

    public boolean isDataset;
    public N5Metadata metadata;

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