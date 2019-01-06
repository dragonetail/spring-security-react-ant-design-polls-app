package com.example.polls.payload;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VoteRequest {
    @NotNull
    private Long choiceId;
}
