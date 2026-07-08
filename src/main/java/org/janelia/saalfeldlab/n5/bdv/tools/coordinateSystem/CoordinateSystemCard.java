package org.janelia.saalfeldlab.n5.bdv.tools.coordinateSystem;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;

/**
 * A BigDataViewer side-panel card that lists the coordinate-system names
 * detected in an {@link CoordinateSystemContext}. 
 * <p>
 * The table is single-selection and pre-selects the context's current
 * coordinate system. When the selection changes to a committed value, the
 * context's {@link CoordinateSystemSourceTransformer} re-aligns every source into the
 * selected coordinate system, and the given {@code repaint} handle is run to
 * refresh the viewer (see {@link #getTable()}). If the context has no aligner
 * (e.g. its transform graph could not be resolved), the card is effectively
 * read-only.
 */
public class CoordinateSystemCard extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Unique key identifying this card in the {@link bdv.ui.CardPanel}. */
	public static final String CARD_KEY = "n5viewer.coordinateSystems.card";

	/** Title shown in the card header. */
	public static final String CARD_TITLE = "Coordinate Systems";

	private final CoordinateSystemContext context;

	private final JTable table;

	private final CoordinateSystemTableModel model;

	/**
	 * @param context
	 *            the context whose coordinate-system names are listed and whose
	 *            {@link CoordinateSystemSourceTransformer} is driven by selection
	 * @param repaint
	 *            run after a re-alignment to refresh the viewer (e.g.
	 *            {@code viewerPanel::requestRepaint}); may be {@code null}
	 */
	public CoordinateSystemCard(final CoordinateSystemContext context, final Runnable repaint) {

		super(new BorderLayout());
		this.context = context;

		model = new CoordinateSystemTableModel(context.getCoordinateSystemNames());
		table = new JTable(model);
		table.setShowGrid(false);
		table.setRowHeight((int)Math.round(UIManager.getDefaults().getFont("Table.font").getSize() * 1.5));
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.setTableHeader(null);
		table.setPreferredScrollableViewportSize(new Dimension(300, 200));

		// pre-select the current coordinate system *before* attaching the
		// listener, so this initial selection does not trigger a (redundant)
		// re-alignment - the sources were already aligned to it at build time
		final int selectedRow = model.indexOf(context.getSelectedCoordinateSystemName());
		if (selectedRow >= 0)
			table.setRowSelectionInterval(selectedRow, selectedRow);

		table.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting())
				return;

			final int row = table.getSelectedRow();
			if (row < 0)
				return;

			final String coordinateSystemName = model.getName(row);
			context.setSelectedCoordinateSystemName(coordinateSystemName);

			final CoordinateSystemSourceTransformer aligner = context.getAligner();
			if (aligner != null) {
				aligner.transformTo(coordinateSystemName);
				if (repaint != null)
					repaint.run();
			}
		});

		final JScrollPane scrollPane = new GutterScrollPane(table, "Table.background");
		add(scrollPane, BorderLayout.CENTER);
		setPreferredSize(new Dimension(300, 245));
	}

	public JTable getTable() {

		return table;
	}

	public CoordinateSystemContext getContext() {

		return context;
	}

	/**
	 * A {@link JScrollPane} with a 4px left {@link MatteBorder} matching the
	 * built-in Sources card's scroll-pane gutter, kept in sync across
	 * look-and-feel changes.
	 */
	private static class GutterScrollPane extends JScrollPane {

		private static final long serialVersionUID = 1L;

		private final String bgColorName;

		GutterScrollPane(final Component view, final String bgColorName) {

			super(view);
			this.bgColorName = bgColorName;
			updateBorder();
		}

		@Override
		public void updateUI() {

			super.updateUI();
			if (bgColorName != null)
				updateBorder();
		}

		private void updateBorder() {

			setBorder(new MatteBorder(0, 4, 0, 0, UIManager.getColor(bgColorName)));
		}
	}

	/**
	 * Single-column, read-only table model backing the coordinate-systems list.
	 */
	private static class CoordinateSystemTableModel extends AbstractTableModel {

		private static final long serialVersionUID = 1L;

		private final List<String> names;

		CoordinateSystemTableModel(final List<String> names) {

			this.names = names;
		}

		String getName(final int row) {

			return names.get(row);
		}

		int indexOf(final String name) {

			return name == null ? -1 : names.indexOf(name);
		}

		@Override
		public int getRowCount() {

			return names.size();
		}

		@Override
		public int getColumnCount() {

			return 1;
		}

		@Override
		public String getColumnName(final int column) {

			return "name";
		}

		@Override
		public Class<?> getColumnClass(final int columnIndex) {

			return String.class;
		}

		@Override
		public boolean isCellEditable(final int rowIndex, final int columnIndex) {

			return false;
		}

		@Override
		public Object getValueAt(final int rowIndex, final int columnIndex) {

			return names.get(rowIndex);
		}
	}
}
