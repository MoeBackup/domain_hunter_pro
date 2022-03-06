package domain.target;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.table.TableModel;

import burp.BurpExtender;
import domain.DomainPanel;
import domain.RootDomainMenu;

public class TargetTable extends JTable{

	private TargetTableModel targetModel = new TargetTableModel();
	private PrintWriter stderr;
	private PrintWriter stdout;


	public TargetTable() {

		try {
			stdout = new PrintWriter(BurpExtender.getCallbacks().getStdout(), true);
			stderr = new PrintWriter(BurpExtender.getCallbacks().getStderr(), true);
		} catch (Exception e) {
			stdout = new PrintWriter(System.out, true);
			stderr = new PrintWriter(System.out, true);
		}

		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		setBorder(new LineBorder(new Color(0, 0, 0)));

		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				SortOrder sortedMethod;
				try {
					getRowSorter().getSortKeys().get(0).getColumn();
					//System.out.println(sortedColumn);
					sortedMethod = getRowSorter().getSortKeys().get(0).getSortOrder();
					System.out.println(sortedMethod); //ASCENDING   DESCENDING
				} catch (Exception e1) {
					sortedMethod = null;
					e1.printStackTrace(stderr);
				}
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override//表格中的鼠标右键菜单
			public void mouseReleased(MouseEvent e) {//在windows中触发,因为isPopupTrigger在windows中是在鼠标释放是触发的，而在mac中，是鼠标点击时触发的。
				//https://stackoverflow.com/questions/5736872/java-popup-trigger-in-linux
				if (SwingUtilities.isRightMouseButton(e)) {
					if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
						//getSelectionModel().setSelectionInterval(rows[0], rows[1]);
						int[] rows = getSelectedRows();
						int col = ((JTable) e.getSource()).columnAtPoint(e.getPoint()); // 获得列位置
						if (rows.length > 0) {
							rows = SelectedRowsToModelRows(getSelectedRows());
							new RootDomainMenu(TargetTable.this, rows, col).show(e.getComponent(), e.getX(), e.getY());
						} else {//在table的空白处显示右键菜单
							//https://stackoverflow.com/questions/8903040/right-click-mouselistener-on-whole-jtable-component
							//new LineEntryMenu(_this).show(e.getComponent(), e.getX(), e.getY());
						}
					}
				}
			}

			@Override
			public void mousePressed(MouseEvent e) { //在mac中触发
				mouseReleased(e);
			}


		});

		setAutoCreateRowSorter(true);
		setColumnSelectionAllowed(true);
		setCellSelectionEnabled(true);
		setSurrendersFocusOnKeystroke(true);
		setFillsViewportHeight(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
	}

	public int[] SelectedRowsToModelRows(int[] SelectedRows) {
		int[] rows = SelectedRows;
		for (int i = 0; i < rows.length; i++) {
			rows[i] = convertRowIndexToModel(rows[i]);//转换为Model的索引，否则排序后索引不对应〿
		}
		Arrays.sort(rows);//升序
		return rows;
	}


	/**
	 * JTable本来就实现了这个函数，
	 * 之所以这样写，是为了避免自己去实现太多功能了。
	 */
	@Override
	public TableModel getModel() {
		return super.getModel();
	}

	/**
	 * JTable已经实现的方法，会被已有逻辑调用。不对其进行修改。
	 * 
	 */
	@Override
	public void setModel(TableModel model) {
		super.setModel(model);
	}

	/**
	 * 自己实现的targetModel的getter和setter，用于自己调用其中函数时，避免对象转换的问题。
	 * 通过getModel、setModel方法进行对象转换，会失败！
	 * @param targetModel
	 */
	public void setTargetModel(TargetTableModel targetModel) {
		this.targetModel = targetModel;
		setModel(targetModel);
	}

	public TargetTableModel getTargetModel() {
		return targetModel;
	}

	public void loadData(TargetTableModel targetTableModel){
		//兼容旧版本
		if (DomainPanel.getDomainResult().getRootDomainMap().size() >0 ) {
			TargetTableModel tmp = new TargetTableModel();
			for (Map.Entry<String, String> entry : DomainPanel.getDomainResult().getRootDomainMap().entrySet()) {
				tmp.addRow(entry.getKey(),new TargetEntry(entry.getKey()));
			}
			DomainPanel.getDomainResult().getRootDomainMap().clear();//值空，下次就使用新格式的数据了
			setTargetModel(tmp);
		}else {
			if (null != targetTableModel) {
				setTargetModel(targetTableModel);//这句很关键，否则无法显示整个表的头和内容
			}
		}
	}
}
