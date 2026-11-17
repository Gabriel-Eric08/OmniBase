package com.secmulher.domain.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.secmulher.domain.dtos.FileDtos.FileDownload;
import com.secmulher.domain.dtos.FileDtos.FileResponse;
import com.secmulher.domain.dtos.FileDtos.FileUpdateRequest;
import com.secmulher.domain.enums.RecordType;
import com.secmulher.domain.exceptions.BusinessException;
import com.secmulher.domain.exceptions.NotFoundException;
import com.secmulher.infra.entities.FileRecord;
import com.secmulher.infra.entities.enums.ActivityType;
import com.secmulher.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.infra.repositories.FileRecordRepository;
import com.secmulher.infra.security.CurrentUserProvider;
import com.secmulher.infra.storage.FileStorage;

@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    final FileRecordRepository fileRecordRepository;
    final FileStorage fileStorage;
    final RecordService recordService;
    final ActivityService activityService;
    final CurrentUserProvider currentUserProvider;

    public FileService(FileRecordRepository fileRecordRepository, FileStorage fileStorage,
            RecordService recordService, ActivityService activityService, CurrentUserProvider currentUserProvider) {
        this.fileRecordRepository = fileRecordRepository;
        this.fileStorage = fileStorage;
        this.recordService = recordService;
        this.activityService = activityService;
        this.currentUserProvider = currentUserProvider;
    }

    private FileRecord getFileById(UUID id) {
        return fileRecordRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Arquivo não encontrado: " + id));
    }

    private static RecordType recordTypeOf(FileRecord file) {
        return file.getEntityType().equals(RecordType.CLIENT.getTableName())
            ? RecordType.CLIENT
            : RecordType.DYNAMIC_ENTITY;
    }

    @Transactional(readOnly = true)
    public List<FileResponse> findAll(RecordType type, UUID recordId) {
        recordService.ensureExists(type, recordId);
        return fileRecordRepository.findByEntityTypeAndEntityIdOrderByUploadedAtDesc(type.getTableName(), recordId)
            .stream().map(FileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FileResponse findById(UUID id) {
        return FileResponse.from(getFileById(id));
    }

    @Transactional
    public FileResponse upload(RecordType type, UUID recordId, MultipartFile upload, ConfidentialityLevel level) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException("Nenhum arquivo enviado");
        }
        recordService.ensureExists(type, recordId);

        // O nome físico é um UUID; o nome original só é guardado como metadado
        String storagePath = type.getTableName() + "/" + recordId + "/" + UUID.randomUUID();
        String originalName = sanitizeFileName(upload.getOriginalFilename());

        try (InputStream content = upload.getInputStream()) {
            fileStorage.store(content, storagePath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", ex);
        }
        runAfterRollback(() -> fileStorage.delete(storagePath));

        FileRecord file = fileRecordRepository.saveAndFlush(FileRecord.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .uploadedBy(currentUserProvider.getCurrentUser().orElse(null))
            .originalName(originalName)
            .storagePath(storagePath)
            .fileSizeBytes(upload.getSize())
            .contentType(upload.getContentType())
            .confidentialityLevel(level)
            .build());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("arquivo", originalName);
        details.put("sigilo", level.name());
        activityService.record(type, recordId, ActivityType.FILE_UPLOADED, "Documento anexado: " + originalName, details);

        return FileResponse.from(file);
    }

    // Downloads de documentos restritos/sigilosos ficam registrados na linha do tempo (LGPD)
    @Transactional
    public FileDownload download(UUID id) {
        FileRecord file = getFileById(id);

        if (file.getConfidentialityLevel() != ConfidentialityLevel.INTERNAL) {
            activityService.record(recordTypeOf(file), file.getEntityId(), ActivityType.FILE_DOWNLOADED,
                "Documento acessado: " + file.getOriginalName(), null);
        }

        return new FileDownload(file.getOriginalName(), file.getContentType(), fileStorage.load(file.getStoragePath()));
    }

    @Transactional
    public FileResponse updateConfidentiality(UUID id, FileUpdateRequest request) {
        FileRecord file = getFileById(id);

        Map<String, Object> changes = new LinkedHashMap<>();
        ActivityService.trackChange(changes, "sigilo", file.getConfidentialityLevel(), request.confidentialityLevel());
        if (!changes.isEmpty()) {
            file.setConfidentialityLevel(request.confidentialityLevel());
            activityService.record(recordTypeOf(file), file.getEntityId(), ActivityType.FILE_UPDATED,
                "Nível de sigilo alterado: " + file.getOriginalName(), changes);
        }

        return FileResponse.from(fileRecordRepository.saveAndFlush(file));
    }

    @Transactional
    public void delete(UUID id) {
        FileRecord file = getFileById(id);

        activityService.record(recordTypeOf(file), file.getEntityId(), ActivityType.FILE_REMOVED,
            "Documento removido: " + file.getOriginalName(), null);
        fileRecordRepository.delete(file);
        runAfterCommit(() -> fileStorage.delete(file.getStoragePath()));
    }

    // Usado quando o registro dono dos arquivos é excluído
    @Transactional
    public void deleteAllFor(RecordType type, UUID recordId) {
        List<FileRecord> files = fileRecordRepository
            .findByEntityTypeAndEntityIdOrderByUploadedAtDesc(type.getTableName(), recordId);
        fileRecordRepository.deleteAll(files);
        runAfterCommit(() -> files.forEach(file -> fileStorage.delete(file.getStoragePath())));
    }

    private static String sanitizeFileName(String originalFilename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
            originalFilename == null ? "" : originalFilename.replace('\\', '/')));
        if (name == null || name.isBlank()) {
            return "arquivo";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    // O disco não participa da transação: só apaga o arquivo físico depois que o banco confirmou
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("Falha ao remover arquivo físico após commit", ex);
                }
            }
        });
    }

    // Se o banco falhar depois do upload, remove o arquivo físico que ficaria órfão
    private void runAfterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        action.run();
                    } catch (RuntimeException ex) {
                        log.warn("Falha ao remover arquivo físico após rollback", ex);
                    }
                }
            }
        });
    }
}
