package com.secmulher.domain.dtos;

import java.util.List;

import com.secmulher.domain.dtos.ActivityDtos.ActivityResponse;
import com.secmulher.domain.dtos.FileDtos.FileResponse;

// Visão 360°: dados do registro + linha do tempo + documentos, em uma única resposta
public record RecordOverviewDto(
    String recordType,
    Object record,
    List<ActivityResponse> activities,
    List<FileResponse> files
) {}
