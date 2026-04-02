package com.example.entity.vo.response;

import lombok.Data;

@Data
public class RegisterClientVO {
    int id;
    String name;
    String node;
    String location;
    String token;
}
