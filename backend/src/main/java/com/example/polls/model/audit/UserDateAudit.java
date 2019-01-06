package com.example.polls.model.audit;

import javax.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@JsonIgnoreProperties(value = { "createdBy", "updatedBy" }, allowGetters = true)
@Getter
@Setter
public abstract class UserDateAudit extends DateAudit {
    private static final long serialVersionUID = -5668994881302355970L;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;
}
