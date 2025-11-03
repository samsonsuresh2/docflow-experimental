package com.docflow.web;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.service.DataInjectorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents/data-injector")
public class DataInjectorController {

    private final DataInjectorService dataInjectorService;
    private final RequestUserContext requestUserContext;

    public DataInjectorController(DataInjectorService dataInjectorService,
                                  RequestUserContext requestUserContext) {
        this.dataInjectorService = dataInjectorService;
        this.requestUserContext = requestUserContext;
    }

    @PostMapping(path = "/uploadexcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DataInjectorResponse> uploadExcel(@RequestPart("file") MultipartFile file) {
        RequestUser user = requestUserContext.requireUser();
        DataInjectorResponse response = dataInjectorService.uploadExcel(file, user);
        return ResponseEntity.ok(response);
    }
}
