package com.docflow.service;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Transactional
public interface DataInjectorService {

    DataInjectorResponse uploadExcel(MultipartFile file, RequestUser user);
}
