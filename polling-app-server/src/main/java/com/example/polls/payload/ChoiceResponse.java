package com.example.polls.payload;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChoiceResponse {
    private long id;
    private String text;
    private long voteCount;
}
