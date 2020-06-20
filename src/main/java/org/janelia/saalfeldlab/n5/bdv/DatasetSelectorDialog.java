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

import ij.IJ;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessException;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessFactory;
import org.janelia.saalfeldlab.n5.bdv.dataaccess.DataAccessType;
import org.janelia.saalfeldlab.n5.bdv.metadata.N5Metadata;
import org.janelia.saalfeldlab.n5.bdv.metadata.N5MultiScaleMetadata;
import org.janelia.saalfeldlab.n5.bdv.metadata.N5ViewerMetadataParser;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DatasetSelectorDialog
{
    private static class SelectedListElement
    {
        private final String path;
        private final N5Metadata metadata;

        SelectedListElement(final String path, final N5Metadata metadata)
        {
            this.path = path;
            this.metadata = metadata;
        }

        @Override
        public String toString()
        {
            return path + (metadata instanceof N5MultiScaleMetadata ? " (multiscale)" : "");
        }
    }

    private final N5DatasetDiscoverer datasetDiscoverer = new N5DatasetDiscoverer(
            new N5ViewerMetadataParser());

    private Consumer<N5Viewer.DataSelection> okCallback;

    private JFrame dialog;
    private JTextField containerPathTxt;

    private JTree containerTree;
    private JList selectedList;

    private JButton addSourceBtn;
    private JButton removeSourceBtn;

    private JButton okBtn;

    private DefaultTreeModel treeModel;
    private DefaultListModel listModel;

    private String lastBrowsePath;
    private N5Reader n5;
    private N5TreeNode selectedNode;

    public void run(final Consumer<N5Viewer.DataSelection> okCallback)
    {
        this.okCallback = okCallback;

        dialog = new JFrame("N5 Viewer");
        dialog.setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.PAGE_AXIS));
        dialog.setMinimumSize(new Dimension(750, 500));
        dialog.setPreferredSize(dialog.getMinimumSize());

        final JPanel containerPanel = new JPanel();
        containerPanel.add(new JLabel("N5 container:"));

        containerPathTxt = new JTextField();
        containerPathTxt.setPreferredSize(new Dimension(400, containerPathTxt.getPreferredSize().height));
        containerPanel.add(containerPathTxt);

        final JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> openContainer(this::openBrowseDialog));
        containerPanel.add(browseBtn);

        final JButton linkBtn = new JButton("Open link...");
        linkBtn.addActionListener(e -> openContainer(this::openLinkDialog));
        containerPanel.add(linkBtn);

        dialog.getContentPane().add(containerPanel);

        final JPanel datasetsPanel = new JPanel();

        final JPanel containerTreePanel = new JPanel();
        containerTreePanel.setLayout(new BoxLayout(containerTreePanel, BoxLayout.Y_AXIS));
        final JLabel containerLabel = new JLabel("Available:");
        containerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        containerTreePanel.add(containerLabel);
        treeModel = new DefaultTreeModel(null, true);
        containerTree = new JTree(treeModel);
        containerTree.setEnabled(false);
        containerTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final JScrollPane containerTreeScroller = new JScrollPane(containerTree);
        containerTreeScroller.setPreferredSize(new Dimension(280, 350));
        containerTreeScroller.setMinimumSize(containerTreeScroller.getPreferredSize());
        containerTreeScroller.setMaximumSize(containerTreeScroller.getPreferredSize());
        containerTreePanel.add(containerTreeScroller);
        datasetsPanel.add(containerTreePanel);

        final JPanel sourceButtonsPanel = new JPanel();
        sourceButtonsPanel.setLayout( new BoxLayout( sourceButtonsPanel, BoxLayout.Y_AXIS ) );
        addSourceBtn = new JButton(">");
        addSourceBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        addSourceBtn.setEnabled(false);
        addSourceBtn.addActionListener(e -> addSource());
        sourceButtonsPanel.add(addSourceBtn);

        removeSourceBtn = new JButton("<");
        removeSourceBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        removeSourceBtn.setEnabled(false);
        removeSourceBtn.addActionListener(e -> removeSource());
        sourceButtonsPanel.add(removeSourceBtn);
        datasetsPanel.add(sourceButtonsPanel);

        final JPanel selectedListPanel = new JPanel();
        selectedListPanel.setLayout(new BoxLayout(selectedListPanel, BoxLayout.Y_AXIS));
        final JLabel selectedLabel = new JLabel("Selected:");
        selectedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        selectedListPanel.add(selectedLabel);
        listModel = new DefaultListModel();
        selectedList = new JList(listModel);
        selectedList.setEnabled(false);
        selectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final JScrollPane selectedListScroller = new JScrollPane(selectedList);
        selectedListScroller.setPreferredSize(new Dimension(280, 350));
        selectedListScroller.setMinimumSize(selectedListScroller.getPreferredSize());
        selectedListScroller.setMaximumSize(selectedListScroller.getPreferredSize());
        selectedListPanel.add(selectedListScroller);
        datasetsPanel.add(selectedListPanel);

        dialog.getContentPane().add(datasetsPanel);

        final JPanel okButtonPanel = new JPanel();
        okBtn = new JButton("OK");
        okBtn.setEnabled(false);
        okBtn.addActionListener(e -> ok());
        okButtonPanel.add(okBtn);

        dialog.getContentPane().add(okButtonPanel);

        dialog.pack();
        dialog.setVisible(true);

        containerTree.addTreeSelectionListener(e -> {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) containerTree.getLastSelectedPathComponent();
            selectedNode = (node == null ? null : (N5TreeNode) node.getUserObject());
            addSourceBtn.setEnabled(selectedNode != null && selectedNode.metadata != null);
        });
    }

    private String openBrowseDialog()
    {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (lastBrowsePath != null && !lastBrowsePath.isEmpty())
            fileChooser.setCurrentDirectory(new File(lastBrowsePath));
        int ret = fileChooser.showOpenDialog(dialog);
        if (ret != JFileChooser.APPROVE_OPTION)
            return null;
        return fileChooser.getSelectedFile().getAbsolutePath();
    }

    private String openLinkDialog()
    {
        return JOptionPane.showInputDialog(dialog, "Link: ", "N5 Viewer", JOptionPane.PLAIN_MESSAGE);
    }

    private void openContainer(final Supplier<String> opener)
    {
        final String n5Path = opener.get();
        if (n5Path == null || n5Path.isEmpty())
            return;

        lastBrowsePath = Paths.get(n5Path).getParent().toString();

        final DataAccessType type = DataAccessType.detectType(n5Path);
        if (type == null) {
            JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
            return;
        }

        n5 = null;
        try {
            n5 = new DataAccessFactory(type).createN5Reader(n5Path);

            if (!n5.exists("/") || n5.getVersion().equals(new N5Reader.Version(null))) {
                JOptionPane.showMessageDialog(dialog, "Not a valid path or link to an N5 container.", "N5 Viewer", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (final DataAccessException | IOException e) {
            IJ.handleException(e);
            return;
        }

        final N5TreeNode n5RootNode;
        try {
            n5RootNode = datasetDiscoverer.discover(n5);
        } catch (final IOException e) {
            IJ.handleException(e);
            return;
        }

        containerPathTxt.setText(n5Path);
        treeModel.setRoot(N5DatasetDiscoverer.toJTreeNode(n5RootNode));
        listModel.clear();

        containerTree.setEnabled(true);
        selectedList.setEnabled(true);
        removeSourceBtn.setEnabled(false);
        okBtn.setEnabled(false);

        containerTree.addTreeSelectionListener(e -> {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) containerTree.getLastSelectedPathComponent();
            selectedNode = (node == null ? null : (N5TreeNode) node.getUserObject());
        });
    }

    private void addSource()
    {
        if (selectedNode != null)
        {
            listModel.addElement(new SelectedListElement(selectedNode.path, selectedNode.metadata));
            selectedNode = null;
            containerTree.clearSelection();

            removeSourceBtn.setEnabled(true);
            okBtn.setEnabled(true);
        }
    }

    private void removeSource()
    {
        if (selectedList.isSelectionEmpty())
            return;

        listModel.removeElementAt(selectedList.getSelectedIndex());
        removeSourceBtn.setEnabled(!listModel.isEmpty());
        okBtn.setEnabled(!listModel.isEmpty());
    }

    private void ok()
    {
        final List<N5Metadata> selectedMetadata = new ArrayList<>();
        for (final Enumeration enumeration = listModel.elements(); enumeration.hasMoreElements();)
            selectedMetadata.add(((SelectedListElement) enumeration.nextElement()).metadata);
        okCallback.accept(new N5Viewer.DataSelection(n5, selectedMetadata));

        dialog.setVisible(false);
        dialog.dispose();
    }
}
