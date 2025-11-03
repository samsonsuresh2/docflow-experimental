package com.docflow.service;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.service.config.DataInjectorProperties;
import com.docflow.service.config.ExcelHeaderMappingResolver;
import com.docflow.service.config.ExcelHeaderMappingResolver.ColumnBinding;
import com.docflow.service.config.ExcelHeaderMappingResolver.HeaderDescriptor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class DefaultDataInjectorService implements DataInjectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataInjectorService.class);

    private final DocumentRepository documentRepository;
    private final MetadataService metadataService;
    private final AuditService auditService;
    private final DataInjectorProperties properties;
    private final ExcelHeaderMappingResolver headerMappingResolver;

    public DefaultDataInjectorService(DocumentRepository documentRepository,
                                      MetadataService metadataService,
                                      AuditService auditService,
                                      DataInjectorProperties properties,
                                      ExcelHeaderMappingResolver headerMappingResolver) {
        this.documentRepository = documentRepository;
        this.metadataService = metadataService;
        this.auditService = auditService;
        this.properties = properties;
        this.headerMappingResolver = headerMappingResolver;
    }

    @Override
    public DataInjectorResponse uploadExcel(MultipartFile file, RequestUser user) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must contain data");
        }

        LOGGER.info("Starting data injector upload for file: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Uploaded Excel file does not contain any sheets");
            }

            String primaryKey = properties.getPrimaryKey();
            if (primaryKey == null || primaryKey.isBlank()) {
                throw new IllegalStateException("excel.primaryKey must be configured");
            }

            HeaderDescriptor headerDescriptor = headerMappingResolver.resolve(sheet.getRow(0));
            if (!headerDescriptor.hasColumn(primaryKey)) {
                throw new IllegalArgumentException("Header must include a column mapped to primary key: " + primaryKey);
            }

            DataInjectorResponse response = new DataInjectorResponse();
            response.setIgnoredColumns(new ArrayList<>(headerDescriptor.ignoredHeaders()));

            int totalRows = 0;
            int inserted = 0;
            int updated = 0;
            int skipped = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                RowPayload payload = readRowPayload(row, headerDescriptor.mappedColumns());
                if (!payload.hasValues()) {
                    LOGGER.trace("Row {} is empty; skipping", rowIndex + 1);
                    continue;
                }

                totalRows++;

                if (payload.documentNumber() == null || payload.documentNumber().isBlank()) {
                    LOGGER.trace("Row {} missing document number; marking as skipped", rowIndex + 1);
                    skipped++;
                    continue;
                }

                Optional<DocumentParent> existing = documentRepository.findByDocumentNumber(payload.documentNumber().trim());
                if (existing.isEmpty()) {
                    insertDocument(payload, user);
                    inserted++;
                } else {
                    updateDocument(existing.get(), payload, user);
                    updated++;
                }
            }

            response.setTotalRows(totalRows);
            response.setInserted(inserted);
            response.setUpdated(updated);
            response.setSkipped(skipped);

            LOGGER.info("Data injector upload complete. Total: {}, Inserted: {}, Updated: {}, Skipped: {}",
                totalRows, inserted, updated, skipped);

            return response;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded Excel file", ex);
        } catch (RuntimeException ex) {
            LOGGER.trace("Data injector upload failed", ex);
            throw ex;
        }
    }

    private void insertDocument(RowPayload payload, RequestUser user) {
        LOGGER.trace("Inserting document {}", payload.documentNumber());
        OffsetDateTime now = OffsetDateTime.now();
        DocumentParent document = new DocumentParent();
        document.setDocumentNumber(payload.documentNumber().trim());
        document.setTitle(resolveTitle(payload));
        DocumentStatus status = parseStatus(payload.status());
        document.setStatus(status != null ? status : DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(now);
        DocumentParent saved = documentRepository.save(document);

        auditService.logFieldUpdate(saved, "title", null, saved.getTitle(), "DATA_INJECTOR_UPLOAD", user, now);
        auditService.logStatusChange(saved, null, saved.getStatus(), "DATA_INJECTOR_UPLOAD", null, user, now);

        if (!payload.metadata().isEmpty()) {
            metadataService.persistMetadata(saved, payload.metadata(), user);
        }
    }

    private void updateDocument(DocumentParent document, RowPayload payload, RequestUser user) {
        LOGGER.trace("Updating document {}", payload.documentNumber());
        OffsetDateTime now = OffsetDateTime.now();

        String title = resolveTitle(payload);
        if (title != null && !title.isBlank() && !Objects.equals(document.getTitle(), title)) {
            auditService.logFieldUpdate(document, "title", document.getTitle(), title, "DATA_INJECTOR_UPLOAD", user, now);
            document.setTitle(title);
        }

        DocumentStatus desiredStatus = parseStatus(payload.status());
        if (desiredStatus != null && !Objects.equals(document.getStatus(), desiredStatus)) {
            auditService.logStatusChange(document, document.getStatus(), desiredStatus, "DATA_INJECTOR_UPLOAD", null, user, now);
            document.setStatus(desiredStatus);
        }

        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        if (!payload.metadata().isEmpty()) {
            metadataService.persistMetadata(document, payload.metadata(), user);
        }
    }

    private String resolveTitle(RowPayload payload) {
        if (payload.title() != null && !payload.title().isBlank()) {
            return payload.title().trim();
        }
        return payload.documentNumber() != null ? payload.documentNumber().trim() : null;
    }

    private DocumentStatus parseStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(statusValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown document status: " + statusValue, ex);
        }
    }

    private RowPayload readRowPayload(Row row, Map<Integer, ColumnBinding> headers) {
        String documentNumber = null;
        String title = null;
        String status = null;
        Map<String, Object> metadata = new LinkedHashMap<>();
        boolean hasValues = false;

        for (Map.Entry<Integer, ColumnBinding> entry : headers.entrySet()) {
            int columnIndex = entry.getKey();
            ColumnBinding binding = entry.getValue();
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Object value = cell != null ? readCellValue(cell) : null;
            if (value == null) {
                continue;
            }

            hasValues = true;

            Object resolvedValue = applyExtractor(binding, value);

            String target = binding.targetColumn();
            if (target == null) {
                continue;
            }

            if (matches(target, properties.getPrimaryKey())) {
                documentNumber = Objects.toString(resolvedValue, null);
            } else if (matches(target, "title")) {
                title = Objects.toString(resolvedValue, null);
            } else if (matches(target, "status")) {
                status = Objects.toString(resolvedValue, null);
            } else {
                metadata.put(target, resolvedValue);
            }
        }

        return new RowPayload(documentNumber, title, status, metadata, hasValues);
    }

    private Object applyExtractor(ColumnBinding binding, Object value) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof String stringValue)) {
            return value;
        }

        if (binding.extractorPattern() == null) {
            return stringValue;
        }

        var matcher = binding.extractorPattern().matcher(stringValue);
        if (matcher.find()) {
            String extracted = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            return extracted != null ? extracted.trim() : null;
        }

        LOGGER.warn("Extractor pattern for column '{}' did not match value '{}'", binding.targetColumn(), stringValue);
        return stringValue;
    }

    private boolean matches(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private Object readCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> {
                String text = cell.getStringCellValue();
                yield text != null ? text.trim() : null;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.rint(numericValue)) {
                    yield (long) numericValue;
                }
                yield numericValue;
            }
            case FORMULA -> readFormulaCell(cell);
            case BLANK, _NONE, ERROR -> null;
        };
    }

    private Object readFormulaCell(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> {
                String text = cell.getRichStringCellValue().getString();
                yield text != null ? text.trim() : null;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.rint(numericValue)) {
                    yield (long) numericValue;
                }
                yield numericValue;
            }
            case BLANK, ERROR, FORMULA, _NONE -> null;
        };
    }

    private record RowPayload(String documentNumber, String title, String status,
                              Map<String, Object> metadata, boolean hasValues) {

        RowPayload {
            metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        }
    }
}
