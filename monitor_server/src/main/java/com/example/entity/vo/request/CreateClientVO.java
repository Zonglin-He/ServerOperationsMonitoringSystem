package com.example.entity.vo.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class CreateClientVO {
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,10}$")
    String name;
    @Length(min = 1, max = 10)
    String node;
    @Pattern(regexp = "(cn|hk|jp|us|sg|kr|de)")
    String location;
}
