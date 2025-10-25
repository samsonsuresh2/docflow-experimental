package com.docflow.api.dto;

import jakarta.validation.constraints.Size;

public class DocumentActionRequest {

    @Size(max = 500)
    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
