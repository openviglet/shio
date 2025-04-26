/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.spreedsheet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShSpreadsheet {
	static final Logger logger = LogManager.getLogger(ShSpreadsheet.class.getName());
	static final int MIN_COL_WIDTH = 20 << 8;
	static final int MAX_COL_WIDTH = 100 << 8;

	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostUtils shPostUtils;

	public void generate(ShFolder shFolder, HttpServletResponse response) {

		try (Workbook workbook = new XSSFWorkbook()) {
			Map<String, List<ShPost>> shPostsByTypeMap = shPostMap(shFolder);
			for (Entry<String, List<ShPost>> entryPost : shPostsByTypeMap.entrySet()) {
				List<ShPost> shPostsEntry = entryPost.getValue();

				if (shPostsEntry != null && !shPostsEntry.isEmpty()) {
					XSSFSheet sheet = this.tableDefinition(workbook, entryPost.getKey(), shPostsEntry);
					this.tableRows(workbook, sheet, shPostsEntry);
					if (sheet != null)
						this.autoSize(sheet, shPostsEntry.get(0).getShPostAttrs().size());

				}
			}

			download(workbook, shFolder.getFurl(), response);
		} catch (IOException e) {
			logger.error("Spreadsheet error: ", e);
		}

	}

	private Map<String, List<ShPost>> shPostMap(ShFolder shFolder) {
		List<ShPost> shPosts = shPostRepository.findByShFolderOrderByShPostType(shFolder);

		Map<String, List<ShPost>> shPostsByTypeMap = new HashMap<>();

		String postTypeName = null;
		List<ShPost> shPostsByType = null;
		for (ShPost shPost : shPosts) {
			shPostUtils.syncWithPostType(shPost);
			if (postTypeName == null) {
				postTypeName = shPost.getShPostType().getTitle();
				shPostsByType = new ArrayList<>();
			} else if (!postTypeName.equals(shPost.getShPostType().getTitle())) {
				shPostsByTypeMap.put(postTypeName, new ArrayList<>(shPostsByType));
				shPostsByType = new ArrayList<>();
				postTypeName = shPost.getShPostType().getTitle();
			}
			shPostsByType.add(shPost);

		}
		shPostsByTypeMap.put(postTypeName, new ArrayList<>(shPostsByType));
		return shPostsByTypeMap;
	}

	private void download(Workbook workbook, String name, HttpServletResponse response) {

		String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
		String fileName = name + "_" + strDate + ".xlsx";

		response.addHeader("Content-disposition", "attachment;filename=" + fileName);
		response.setContentType("application/octet-stream");
		response.setStatus(HttpServletResponse.SC_OK);
		ServletOutputStream out = null;
		try {
			out = response.getOutputStream();
			workbook.write(out);
		} catch (IOException e1) {
			logger.error("Spreadsheet download error: ", e1);
		} finally {
			try {
				if (out != null)
					out.close();
				if (workbook != null)
					workbook.close();
			} catch (IOException e) {
				logger.error("Spreadsheet download error: ", e);
			}
		}
	}

	private void tableRows(Workbook workbook, XSSFSheet sheet, List<ShPost> shPostsEntry) {
		CreationHelper createHelper = workbook.getCreationHelper();
		int rowCount = 0;	
		
		shPostsEntry.forEach(shPost -> createRow(workbook, sheet, createHelper, rowCount, shPost));
	

	} 

	private void createRow(Workbook workbook, XSSFSheet sheet, CreationHelper createHelper, int rowCount,
			ShPost shPost) {
		int columnCount;
		CellStyle cellTextStyle = workbook.createCellStyle();
		cellTextStyle.setVerticalAlignment(VerticalAlignment.TOP);

		CellStyle cellTextAreaStyle = workbook.createCellStyle();
		cellTextAreaStyle.setWrapText(true);
		cellTextAreaStyle.setVerticalAlignment(VerticalAlignment.TOP);

		CellStyle cellDateStyle = workbook.createCellStyle();
		cellDateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy"));
		cellDateStyle.setVerticalAlignment(VerticalAlignment.TOP);
		rowCount++;
		columnCount = 0;

		XSSFRow row = sheet.createRow(rowCount);
		this.createColumns(shPost, columnCount, cellTextStyle, cellTextAreaStyle, cellDateStyle, row);
	}

	@SuppressWarnings("unchecked")
	private void createColumns(ShPost shPost, int columnCount, CellStyle cellTextStyle, CellStyle cellTextAreaStyle,
			CellStyle cellDateStyle, XSSFRow row) {
		this.createdDateCell(shPost, cellDateStyle, row);

		for (ShPostAttrImpl shPostAttr : shPostUtils.postAttrsSort((Set<ShPostAttrImpl>) shPost.getShPostAttrs())) {
			columnCount++;
			String widgetName = shPostAttr.getShPostTypeAttr().getShWidget().getName();
			if (!widgetName.contains(ShSystemWidget.TAB)) {
				XSSFCell cell = row.createCell(columnCount);
				if (widgetName.equals(ShSystemWidget.DATE)) {
					this.dateCell(cellDateStyle, shPostAttr, cell);
				} else if (widgetName.equals(ShSystemWidget.CONTENT_SELECT)
						|| widgetName.equals(ShSystemWidget.FILE)) {
					this.relatorCell(cellTextStyle, shPost, shPostAttr, cell);
				} else if (widgetName.equals(ShSystemWidget.TEXT_AREA)
						|| widgetName.equals(ShSystemWidget.HTML_EDITOR)) {
					this.textAreaCell(cellTextAreaStyle, shPostAttr, cell);
				} else if (widgetName.equals(ShSystemWidget.MULTI_SELECT)) {
					multiSelectCell(cellTextAreaStyle, shPostAttr, cell);
				} else {
					this.textAreaCell(cellTextStyle, shPostAttr, cell);
				}
			}

		}
	}

	private void createdDateCell(ShPost shPost, CellStyle cellDateStyle, XSSFRow row) {
		Cell cellDate = row.createCell(0);
		cellDate.setCellValue(shPost.getDate());
		cellDate.setCellStyle(cellDateStyle);
	}

	private void dateCell(CellStyle cellDateStyle, ShPostAttrImpl shPostAttr, XSSFCell cell) {
		cell.setCellValue(shPostAttr.getDateValue());
		cell.setCellStyle(cellDateStyle);
	}

	private void multiSelectCell(CellStyle cellTextAreaStyle, ShPostAttrImpl shPostAttr, XSSFCell cell) {
		List<String> msItems = new ArrayList<>();
		for (String id : shPostAttr.getArrayValue()) {
			Optional<ShPost> shPostMultSelect = shPostRepository.findById(id);
			if (shPostMultSelect.isPresent()) {
				msItems.add(shPostMultSelect.get().getTitle());
			}
		}
		cell.setCellValue(String.join(", ", msItems));
		cell.setCellStyle(cellTextAreaStyle);
	}

	private void textAreaCell(CellStyle cellTextAreaStyle, ShPostAttrImpl shPostAttr, XSSFCell cell) {
		cell.setCellValue(shPostAttr.getStrValue());
		cell.setCellStyle(cellTextAreaStyle);
	}

	private void relatorCell(CellStyle cellTextStyle, ShPostImpl shPost, ShPostAttrImpl shPostAttr, XSSFCell cell) {
		String value = null;
		if (shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			value = shPostAttr.getStrValue();
		} else {
			ShObjectImpl shObject = shPostAttr.getReferenceObject();

			if (shObject instanceof ShFolder shFolder) {
				value = shFolder.getName();
			} else if (shObject instanceof ShPostImpl shPostImpl) {
				value = shPostImpl.getTitle();
			} else if (shObject instanceof ShSite shSite) {
				value = shSite.getName();
			}
		}
		cell.setCellValue(value);
		cell.setCellStyle(cellTextStyle);
	}

	@SuppressWarnings("unchecked")
	private XSSFSheet tableDefinition(Workbook workbook, String sheetName, List<ShPost> shPostsEntry) {
		if (shPostsEntry != null && !shPostsEntry.isEmpty()) {
			XSSFSheet sheet = (XSSFSheet) workbook.createSheet(sheetName);

			int totalCols = shPostsEntry.get(0).getShPostAttrs().size();

			int columnCount = 0;

			XSSFRow row = sheet.createRow(0);

			Cell cellHeaderDate = row.createCell(columnCount);
			cellHeaderDate.setCellValue("Date");

			for (ShPostAttrImpl shPostAttr : shPostUtils.postAttrsSort((Set<ShPostAttrImpl>) shPostsEntry.get(0).getShPostAttrs())) {
				columnCount++;
				XSSFCell cell = row.createCell(columnCount);
				cell.setCellValue(shPostAttr.getShPostTypeAttr().getLabel());

			}
			sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, totalCols));

			return sheet;
		} else {
			return null;
		}
	}

	private int autoSize(XSSFSheet sheet, int maxCol) {
		for (int i = 0; i <= maxCol; i++) {
			sheet.autoSizeColumn(i);

			int cw = (int) (sheet.getColumnWidth(i) * 0.8);
			sheet.setColumnWidth(i, Math.max(Math.min(cw, MAX_COL_WIDTH), MIN_COL_WIDTH));
		}
		maxCol = 0;
		return maxCol;
	}

}
