package com.igot.cb.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FileProcessServiceTest {

    @InjectMocks
    private FileProcessService fileProcessService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProcessExcelFile_Success() throws IOException {
        MultipartFile file = createTestExcelFile();
        List<Map<String, String>> result = fileProcessService.processExcelFile(file);
        assertNotNull("Result should not be null", result);
        assertEquals("Should have 2 data rows", 2, result.size());
        assertEquals("First row, first column should match", "Value1", result.get(0).get("Header1"));
        assertEquals("First row, second column should match", "Value2", result.get(0).get("Header2"));
        assertEquals("Second row, first column should match", "Value3", result.get(1).get("Header1"));
        assertEquals("Second row, second column should match", "Value4", result.get(1).get("Header2"));
    }

    @Test
    public void testProcessCsvFile_Success() throws IOException {
        MultipartFile file = createTestCsvFile();
        List<Map<String, String>> result = fileProcessService.processExcelFile(file);
        assertNotNull("Result should not be null", result);
        assertEquals("Should have 2 data rows", 2, result.size());
        assertEquals("First row, first column should match", "Value1", result.get(0).get("Header1"));
        assertEquals("First row, second column should match", "Value2", result.get(0).get("Header2"));
        assertEquals("Second row, first column should match", "Value3", result.get(1).get("Header1"));
        assertEquals("Second row, second column should match", "Value4", result.get(1).get("Header2"));
    }

    @Test(expected = RuntimeException.class)
    public void testProcessExcelFile_NullFileName() {
        MockMultipartFile file = new MockMultipartFile("file", null, "application/vnd.ms-excel", new byte[0]);
        fileProcessService.processExcelFile(file);
    }

    @Test(expected = RuntimeException.class)
    public void testProcessExcelFile_UnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        fileProcessService.processExcelFile(file);
    }

    @Test
    public void testProcessExcelFile_EmptyRows() throws IOException {
        MultipartFile file = createExcelFileWithEmptyRows();
        List<Map<String, String>> result = fileProcessService.processExcelFile(file);
        assertNotNull("Result should not be null", result);
        assertEquals("Should have 1 data row (ignoring empty rows)", 1, result.size());
        assertEquals("First row, first column should match", "Value1", result.get(0).get("Header1"));
    }

    @Test
    public void testProcessExcelFile_WithDates() throws IOException {
        MultipartFile file = createExcelFileWithDates();
        List<Map<String, String>> result = fileProcessService.processExcelFile(file);
        assertNotNull("Result should not be null", result);
        assertEquals("Should have 1 data row", 1, result.size());
        assertTrue("Date should be formatted correctly", 
                result.get(0).get("Date").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
    }

    private MultipartFile createTestExcelFile() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Test Sheet");
        Row headerRow = sheet.createRow(0);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("Header1");
        Cell headerCell2 = headerRow.createCell(1);
        headerCell2.setCellValue("Header2");
        Row dataRow1 = sheet.createRow(1);
        dataRow1.createCell(0).setCellValue("Value1");
        dataRow1.createCell(1).setCellValue("Value2");
        Row dataRow2 = sheet.createRow(2);
        dataRow2.createCell(0).setCellValue("Value3");
        dataRow2.createCell(1).setCellValue("Value4");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        workbook.write(byteArrayOutputStream);
        workbook.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                byteArrayOutputStream.toByteArray());
    }
    
    private MultipartFile createTestCsvFile() {
        String csvContent = "Header1,Header2\nValue1,Value2\nValue3,Value4";
        return new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
    }
    
    private MultipartFile createExcelFileWithEmptyRows() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Test Sheet");
        Row headerRow = sheet.createRow(0);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("Header1");
        Row dataRow1 = sheet.createRow(1);
        dataRow1.createCell(0).setCellValue("Value1");
        sheet.createRow(2);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        workbook.write(byteArrayOutputStream);
        workbook.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                byteArrayOutputStream.toByteArray());
    }
    
    private MultipartFile createExcelFileWithDates() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Test Sheet");
        Row headerRow = sheet.createRow(0);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Date");
        Row dataRow = sheet.createRow(1);
        Cell dateCell = dataRow.createCell(0);
        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        dateCell.setCellStyle(cellStyle);
        dateCell.setCellValue(new java.util.Date());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        workbook.write(byteArrayOutputStream);
        workbook.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                byteArrayOutputStream.toByteArray());
    }
}