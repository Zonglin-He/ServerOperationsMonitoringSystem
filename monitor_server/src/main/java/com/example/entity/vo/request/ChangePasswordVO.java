package com.example.entity.vo.request;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class ChangePasswordVO {
    @Length(min = 6, max = 64)
    String password;
    @Length(min = 6, max = 64)
    String new_password;
}
